package com.covaimetertaxi.driver.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Data class representing driver details loaded from Firebase Backend.
 */
data class FirebaseDriverData(
    val id: String,
    val pin: String,
    val driverName: String,
    val mobileNumber: String,
    val vehicleNumber: String,
    val vehicleCategory: String,
    val vehicleModel: String = "",
    val driverImageUrl: String,
    val localPhotoPath: String? = null,
    val deviceId: String = "",
    val backendType: String = "FIRESTORE",
    val backendDocPath: String = ""
)

/**
 * FirebaseManager handles driver authentication and profile synchronization
 * directly with Cloud Firestore and Firebase Realtime Database via REST endpoints.
 *
 * It does not require google-services.json to compile or operate, ensuring full
 * portability while maintaining 100% compatibility with standard Firebase instances.
 */
object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private const val PREFS_NAME = "CovaiMeterTaxiPrefs"
    private const val PREFS_AUTH_CACHE = "CovaiDriverAuthCache"
    private const val KEY_FIREBASE_PROJECT_ID = "pref_firebase_project_id"
    val DEFAULT_PROJECT_ID: String
        get() = com.covaimetertaxi.driver.util.SecurityObfuscator.getSecureProjectId()

    const val KEY_DEVICE_ID = "simple_device_id"
    const val KEY_LOGGED_DRIVER_ID = "logged_driver_id"
    const val KEY_LOGGED_BACKEND_TYPE = "logged_backend_type"
    const val KEY_LOGGED_BACKEND_DOC_PATH = "logged_backend_doc_path"
    const val KEY_LOGGED_DEVICE_ID = "logged_device_id"

    private const val KEY_CACHED_DRIVER_ID = "cached_driver_id"
    private const val KEY_CACHED_PIN = "cached_driver_pin"
    private const val KEY_CACHED_NAME = "cached_driver_name"
    private const val KEY_CACHED_MOBILE = "cached_mobile_number"
    private const val KEY_CACHED_VEHICLE = "cached_vehicle_number"
    private const val KEY_CACHED_CATEGORY = "cached_vehicle_category"
    private const val KEY_CACHED_PHOTO = "cached_local_photo_path"
    private const val KEY_CACHED_LAST_SYNC = "cached_last_sync_time"
    private const val CACHE_VALIDITY_MS = 24 * 60 * 60 * 1000L // 24 hours: daily at most

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getFirebaseProjectId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_FIREBASE_PROJECT_ID, "")?.trim() ?: ""
        return if (saved.isNotEmpty()) saved else DEFAULT_PROJECT_ID
    }

    fun setFirebaseProjectId(context: Context, projectId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FIREBASE_PROJECT_ID, projectId.trim()).apply()
    }

    /**
     * Retrieves or generates a persistent simple Device ID for this device.
     * Uses Android ID (hex string) where available, falling back to a persistent random 16-char ID.
     */
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, "")?.trim() ?: ""
        if (deviceId.isEmpty()) {
            val androidId = try {
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )?.trim() ?: ""
            } catch (e: Exception) {
                ""
            }
            deviceId = if (androidId.isNotEmpty() && androidId != "9774d56d682e549c") {
                androidId.lowercase(java.util.Locale.ROOT)
            } else {
                java.util.UUID.randomUUID().toString().replace("-", "").take(16).lowercase(java.util.Locale.ROOT)
            }
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    /**
     * Extracts the last 4 numeric digits from a vehicle registration number.
     * Examples:
     * - "TN 38 AB 1234" -> "1234"
     * - "TN38AB1234" -> "1234"
     * - "TN-38-C-9876" -> "9876"
     */
    fun extractVehiclePin(vehicleNumber: String): String {
        val digits = vehicleNumber.filter { it.isDigit() }
        return if (digits.length >= 4) {
            digits.takeLast(4)
        } else {
            digits
        }
    }

    /**
     * Resolves direct image links from various formats including Google Drive file sharing links.
     * Google Drive share links format:
     * - https://drive.google.com/file/d/FILE_ID/view?usp=sharing
     * - https://drive.google.com/open?id=FILE_ID
     * - https://drive.google.com/uc?id=FILE_ID
     */
    fun resolveDriveOrImageUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""

        val driveRegex = Regex("""drive\.google\.com/(?:file/d/|open\?id=|uc\?(?:export=download&)?id=)([a-zA-Z0-9_-]+)""")
        val match = driveRegex.find(trimmed)
        if (match != null) {
            val fileId = match.groupValues[1]
            return "https://lh3.googleusercontent.com/d/$fileId"
        }

        return trimmed
    }

    /**
     * Downloads an avatar image to local internal storage.
     * Supports both direct URLs and Google Drive links.
     */
    suspend fun downloadDriverImage(context: Context, rawUrl: String, driverId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val resolvedUrl = resolveDriveOrImageUrl(rawUrl)
                if (resolvedUrl.isBlank()) return@withContext null

                val request = Request.Builder()
                    .url(resolvedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
                    .build()

                var response = httpClient.newCall(request).execute()
                var bytes = response.body?.bytes()

                // Fallback attempt for Google Drive if CDN didn't return image bytes
                if ((bytes == null || bytes.isEmpty() || !response.isSuccessful) && rawUrl.contains("drive.google.com")) {
                    val driveRegex = Regex("""([a-zA-Z0-9_-]{25,})""")
                    val fileId = driveRegex.find(rawUrl)?.groupValues?.get(1)
                    if (fileId != null) {
                        val fallbackUrl = "https://drive.google.com/uc?export=download&id=$fileId"
                        val fallbackReq = Request.Builder()
                            .url(fallbackUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile)")
                            .build()
                        val fallbackResp = httpClient.newCall(fallbackReq).execute()
                        if (fallbackResp.isSuccessful) {
                            bytes = fallbackResp.body?.bytes()
                        }
                    }
                }

                if (bytes != null && bytes.isNotEmpty()) {
                    val safeId = driverId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val file = File(context.filesDir, "driver_photo_${safeId}.jpg")
                    file.writeBytes(bytes)
                    Log.d(TAG, "Successfully downloaded driver avatar to: ${file.absolutePath}")
                    return@withContext file.absolutePath
                }
                null
            } catch (e: Exception) {
                Log.w(TAG, "Driver image download failed from $rawUrl: ${e.message}")
                null
            }
        }
    }

    /**
     * Generates a circular avatar image with driver initials when no image URL is provided.
     */
    fun generateInitialsAvatar(context: Context, driverName: String, driverId: String): String {
        return try {
            val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { isAntiAlias = true }

            // Background circle with Taxi Yellow color
            paint.color = android.graphics.Color.parseColor("#FACC15")
            canvas.drawCircle(128f, 128f, 128f, paint)

            // Inner subtle border
            paint.color = android.graphics.Color.parseColor("#CA8A04")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            canvas.drawCircle(128f, 128f, 124f, paint)

            // Initials text
            paint.style = Paint.Style.FILL
            paint.color = android.graphics.Color.parseColor("#0F172A") // Slate 900
            paint.textSize = 96f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER

            val words = driverName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val initials = if (words.size >= 2) {
                "${words[0].first()}${words[1].first()}".uppercase()
            } else if (words.isNotEmpty()) {
                words[0].take(2).uppercase()
            } else {
                driverId.take(2).uppercase().ifEmpty { "TX" }
            }

            val textY = 128f - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(initials, 128f, textY, paint)

            val safeId = driverId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(context.filesDir, "driver_avatar_${safeId}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating initials avatar: ${e.message}", e)
            ""
        }
    }

    /**
     * Helper to extract a string value from a Cloud Firestore document's "fields" map.
     */
    private fun extractFirestoreField(fields: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val fieldObj = fields.optJSONObject(key) ?: continue
            if (fieldObj.has("stringValue")) {
                val str = fieldObj.optString("stringValue", "").trim()
                if (str.isNotEmpty()) return str
            }
            if (fieldObj.has("integerValue")) {
                val intVal = fieldObj.optString("integerValue", "").trim()
                if (intVal.isNotEmpty()) return intVal
            }
            if (fieldObj.has("doubleValue")) {
                val dblVal = fieldObj.optString("doubleValue", "").trim()
                if (dblVal.isNotEmpty()) return dblVal
            }
            if (fieldObj.has("booleanValue")) {
                return fieldObj.optBoolean("booleanValue").toString()
            }
        }
        return ""
    }

    /**
     * Helper to extract a string value from a standard JSON map (Realtime Database format).
     */
    private fun extractJsonField(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val str = obj.optString(key, "").trim()
                if (str.isNotEmpty()) return str
            }
        }
        return ""
    }

    /**
     * Authenticates a driver against Firebase Firestore or Realtime Database.
     *
     * Expected Firebase Document Fields:
     * - id / driver_id: (String or Document ID) e.g. "101" or "DRV101"
     * - pin: (String or Integer) e.g. "1234"
     * - driver_name: (String) e.g. "Ramesh Kumar"
     * - mobile_number: (String) e.g. "9876543210"
     * - vehicle_number: (String) e.g. "TN 38 AB 1234"
     * - vehicle_category: (String) e.g. "Sedan" or "Mini" or "Auto"
     * - vehicle_model: (String, optional) e.g. "Etios"
     * - driver_image_url / drive_url: (String, optional) e.g. Google Drive link or Image URL
     */
    suspend fun authenticateDriver(
        context: Context,
        driverIdInput: String,
        pinInput: String
    ): Result<FirebaseDriverData> {
        return withContext(Dispatchers.IO) {
            val cleanId = driverIdInput.trim()
            val cleanPin = pinInput.trim()

            if (cleanId.isEmpty() || cleanPin.isEmpty()) {
                return@withContext Result.failure(Exception("Please enter both Driver ID and PIN"))
            }

            val cachePrefs = context.getSharedPreferences(PREFS_AUTH_CACHE, Context.MODE_PRIVATE)
            val cachedId = cachePrefs.getString(KEY_CACHED_DRIVER_ID, "") ?: ""
            val cachedPin = cachePrefs.getString(KEY_CACHED_PIN, "") ?: ""
            val lastSyncTime = cachePrefs.getLong(KEY_CACHED_LAST_SYNC, 0L)
            val now = System.currentTimeMillis()
            val isCacheFresh = (now - lastSyncTime) in 0L..CACHE_VALIDITY_MS

            val matchesCachedCredentials = cachedId.equals(cleanId, ignoreCase = true) && cachedPin == cleanPin

            val currentDeviceId = getDeviceId(context)
            val projectId = getFirebaseProjectId(context)

            // Step 1: Query Cloud Firestore by Document ID
            var authResult = queryFirestoreByDocId(projectId, cleanId, cleanPin)

            // Step 2: If not found, try Firestore query by field (in case document ID is auto-generated)
            if (authResult.isFailure && authResult.exceptionOrNull()?.message?.contains("not found", ignoreCase = true) == true) {
                val structuredResult = queryFirestoreByField(projectId, cleanId, cleanPin)
                if (structuredResult.isSuccess) {
                    authResult = structuredResult
                }
            }

            // Step 3: If still not found or error, try Firebase Realtime Database
            if (authResult.isFailure && authResult.exceptionOrNull()?.message?.contains("not found", ignoreCase = true) == true) {
                val rtdbResult = queryRealtimeDatabase(projectId, cleanId, cleanPin)
                if (rtdbResult.isSuccess) {
                    authResult = rtdbResult
                }
            }

            // If authentication was successful, enforce single-device login & download photo
            if (authResult.isSuccess) {
                val data = authResult.getOrThrow()
                val existingDeviceId = data.deviceId.trim()

                // Enforce single-device rule: reject if logged in on a different device
                if (existingDeviceId.isNotEmpty() && !existingDeviceId.equals(currentDeviceId, ignoreCase = true)) {
                    Log.w(TAG, "Single device login blocked: ID '$cleanId' is active on device '$existingDeviceId', this device is '$currentDeviceId'")
                    return@withContext Result.failure(
                        Exception("This Driver ID is already logged in on another device. Only one device is allowed to login at a time. Please logout from the previous device first.")
                    )
                }

                // Update device_id in Firebase backend to current device ID
                updateBackendDeviceId(
                    projectId = projectId,
                    backendType = data.backendType,
                    backendDocPath = data.backendDocPath,
                    cleanId = data.id,
                    deviceId = currentDeviceId
                )

                // Save session info in SharedPreferences for seamless logout
                val appPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                appPrefs.edit().apply {
                    putString(KEY_LOGGED_DRIVER_ID, cleanId)
                    putString(KEY_LOGGED_BACKEND_TYPE, data.backendType)
                    putString(KEY_LOGGED_BACKEND_DOC_PATH, data.backendDocPath)
                    putString(KEY_LOGGED_DEVICE_ID, currentDeviceId)
                    apply()
                }

                var localPhoto: String? = null
                if (data.driverImageUrl.isNotBlank()) {
                    localPhoto = downloadDriverImage(context, data.driverImageUrl, data.id)
                }

                if (localPhoto == null || localPhoto.isBlank()) {
                    localPhoto = generateInitialsAvatar(context, data.driverName, data.id)
                }

                // Cache successful login data locally
                cachePrefs.edit().apply {
                    putString(KEY_CACHED_DRIVER_ID, cleanId)
                    putString(KEY_CACHED_PIN, cleanPin)
                    putString(KEY_CACHED_NAME, data.driverName)
                    putString(KEY_CACHED_MOBILE, data.mobileNumber)
                    putString(KEY_CACHED_VEHICLE, data.vehicleNumber)
                    putString(KEY_CACHED_CATEGORY, data.vehicleCategory)
                    putString(KEY_CACHED_PHOTO, localPhoto ?: "")
                    putLong(KEY_CACHED_LAST_SYNC, now)
                    apply()
                }

                return@withContext Result.success(data.copy(deviceId = currentDeviceId, localPhotoPath = localPhoto))
            }

            // If backend read failed due to network / timeout, but user credentials match local cache, allow offline login
            if (matchesCachedCredentials) {
                val cachedName = cachePrefs.getString(KEY_CACHED_NAME, "") ?: ""
                val cachedVehicle = cachePrefs.getString(KEY_CACHED_VEHICLE, "") ?: ""
                if (cachedName.isNotBlank() && cachedVehicle.isNotBlank()) {
                    Log.d(TAG, "Backend unreachable, verified driver using cached local profile.")
                    val cachedData = FirebaseDriverData(
                        id = cleanId,
                        pin = cleanPin,
                        driverName = cachedName,
                        mobileNumber = cachePrefs.getString(KEY_CACHED_MOBILE, "") ?: "",
                        vehicleNumber = cachedVehicle,
                        vehicleCategory = cachePrefs.getString(KEY_CACHED_CATEGORY, "Mini") ?: "Mini",
                        vehicleModel = "",
                        driverImageUrl = "",
                        localPhotoPath = cachePrefs.getString(KEY_CACHED_PHOTO, null),
                        deviceId = currentDeviceId
                    )
                    return@withContext Result.success(cachedData)
                }
            }

            authResult
        }
    }

    /**
     * Updates the device_id field in Firebase backend (Firestore or Realtime Database).
     */
    suspend fun updateBackendDeviceId(
        projectId: String,
        backendType: String,
        backendDocPath: String,
        cleanId: String,
        deviceId: String
    ): Boolean = withContext(Dispatchers.IO) {
        var success = false
        if (backendType == "RTDB") {
            try {
                val url = "https://$projectId-default-rtdb.firebaseio.com/drivers/$cleanId.json"
                val jsonPayload = JSONObject().apply {
                    put("device_id", deviceId)
                }.toString()
                val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).patch(requestBody).build()
                httpClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "RTDB update device_id ($deviceId) for $cleanId: ${response.code}")
                    if (response.isSuccessful) success = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating device_id in RTDB: ${e.message}", e)
            }
        } else {
            try {
                val url = if (backendDocPath.startsWith("https://")) {
                    "$backendDocPath?updateMask.fieldPaths=device_id"
                } else if (backendDocPath.startsWith("projects/")) {
                    "https://firestore.googleapis.com/v1/$backendDocPath?updateMask.fieldPaths=device_id"
                } else if (backendDocPath.isNotEmpty()) {
                    "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$backendDocPath?updateMask.fieldPaths=device_id"
                } else {
                    "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/drivers/$cleanId?updateMask.fieldPaths=device_id"
                }

                val jsonPayload = JSONObject().apply {
                    val fieldsObj = JSONObject().apply {
                        put("device_id", JSONObject().apply {
                            put("stringValue", deviceId)
                        })
                    }
                    put("fields", fieldsObj)
                }.toString()

                val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(url).patch(requestBody).build()
                httpClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "Firestore update device_id ($deviceId) for $cleanId: ${response.code}")
                    if (response.isSuccessful) success = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating device_id in Firestore: ${e.message}", e)
            }
        }
        success
    }

    /**
     * Clears the device_id in Firebase backend to blank ("") upon driver logout,
     * freeing up the account so it can be logged in again.
     */
    suspend fun clearDriverDeviceId(
        context: Context,
        fallbackDriverId: String = ""
    ) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val projectId = getFirebaseProjectId(context)
        val savedDriverId = prefs.getString(KEY_LOGGED_DRIVER_ID, "") ?: ""
        val uniqueDriverId = prefs.getString("unique_driver_id", "") ?: ""
        val targetDriverId = savedDriverId.ifEmpty { fallbackDriverId }.ifEmpty { uniqueDriverId }.trim()
        val backendDocPath = prefs.getString(KEY_LOGGED_BACKEND_DOC_PATH, "") ?: ""

        Log.d(TAG, "Clearing device_id in Firebase backend for driver '$targetDriverId' (docPath: '$backendDocPath')...")

        // Clear local tracking preferences
        prefs.edit().apply {
            remove(KEY_LOGGED_DRIVER_ID)
            remove(KEY_LOGGED_BACKEND_TYPE)
            remove(KEY_LOGGED_BACKEND_DOC_PATH)
            remove(KEY_LOGGED_DEVICE_ID)
            apply()
        }

        // Clear cached auth credentials
        context.getSharedPreferences(PREFS_AUTH_CACHE, Context.MODE_PRIVATE).edit().clear().apply()

        // 1. Blank out device_id in Firestore
        try {
            val firestoreUrl = if (backendDocPath.startsWith("https://")) {
                "$backendDocPath?updateMask.fieldPaths=device_id"
            } else if (backendDocPath.startsWith("projects/")) {
                "https://firestore.googleapis.com/v1/$backendDocPath?updateMask.fieldPaths=device_id"
            } else if (backendDocPath.isNotEmpty()) {
                "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$backendDocPath?updateMask.fieldPaths=device_id"
            } else if (targetDriverId.isNotEmpty()) {
                "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/drivers/$targetDriverId?updateMask.fieldPaths=device_id"
            } else null

            if (firestoreUrl != null) {
                val jsonPayload = JSONObject().apply {
                    val fieldsObj = JSONObject().apply {
                        put("device_id", JSONObject().apply {
                            put("stringValue", "")
                        })
                    }
                    put("fields", fieldsObj)
                }.toString()

                val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(firestoreUrl).patch(requestBody).build()
                httpClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "Cleared device_id in Firestore for driver '$targetDriverId', response: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing Firestore device_id: ${e.message}", e)
        }

        // 2. Blank out device_id in Realtime Database as well if targetDriverId is known
        if (targetDriverId.isNotEmpty()) {
            try {
                val rtdbUrl = "https://$projectId-default-rtdb.firebaseio.com/drivers/$targetDriverId.json"
                val jsonPayload = JSONObject().apply {
                    put("device_id", "")
                }.toString()
                val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())
                val request = Request.Builder().url(rtdbUrl).patch(requestBody).build()
                httpClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "Cleared device_id in RTDB for driver '$targetDriverId', response: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing RTDB device_id: ${e.message}", e)
            }
        }
    }

    /**
     * Non-suspending wrapper called by ViewModel during driver logout.
     */
    fun syncDriverLogout(context: Context, driverId: String = "") {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                clearDriverDeviceId(context, driverId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync driver logout to Firebase: ${e.message}", e)
            }
        }
    }

    /**
     * Queries Cloud Firestore directly for a document with ID = driverId in 'drivers' collection.
     */
    private fun queryFirestoreByDocId(
        projectId: String,
        cleanId: String,
        cleanPin: String
    ): Result<FirebaseDriverData> {
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/drivers/$cleanId"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                Log.d(TAG, "Firestore doc lookup HTTP ${response.code} for $cleanId")

                if (response.code == 404) {
                    return Result.failure(Exception("Driver ID '$cleanId' not found"))
                }

                if (response.code == 403 || response.code == 401) {
                    return Result.failure(Exception("Access denied (HTTP ${response.code}). Please contact administrator."))
                }

                if (!response.isSuccessful) {
                    return Result.failure(Exception("Server connection error (HTTP ${response.code})"))
                }

                val docJson = JSONObject(bodyStr)
                val fields = docJson.optJSONObject("fields")
                    ?: return Result.failure(Exception("Malformed driver document"))

                val driverName = extractFirestoreField(fields, "driver_name", "name", "driverName", "full_name").ifEmpty { "Driver $cleanId" }
                val mobileNumber = extractFirestoreField(fields, "mobile_number", "driver_mobile", "mobile", "phone")
                val vehicleNumber = extractFirestoreField(fields, "vehicle_number", "vehicle_no", "vehicleNumber", "car_number")
                val vehicleCategory = extractFirestoreField(fields, "vehicle_category", "category", "vehicle_type", "type").replace("(?i)\\s*taxi\\b".toRegex(), "").trim().ifEmpty { "Mini" }
                val vehicleModel = ""
                val imageUrl = extractFirestoreField(fields, "driver_image_url", "drive_url", "image_url", "photo_url", "driver_image", "avatar_url")
                val deviceIdInDb = extractFirestoreField(fields, "device_id", "deviceId", "simple_device_id", "active_device_id")

                val pinInDb = extractFirestoreField(fields, "pin", "driver_pin", "passcode", "password").trim()

                if (pinInDb.isEmpty()) {
                    return Result.failure(Exception("Incorrect PIN"))
                }

                if (cleanPin != pinInDb) {
                    return Result.failure(Exception("Incorrect PIN"))
                }

                return Result.success(
                    FirebaseDriverData(
                        id = cleanId,
                        pin = cleanPin,
                        driverName = driverName,
                        mobileNumber = mobileNumber,
                        vehicleNumber = vehicleNumber,
                        vehicleCategory = vehicleCategory,
                        vehicleModel = vehicleModel,
                        driverImageUrl = imageUrl,
                        deviceId = deviceIdInDb,
                        backendType = "FIRESTORE",
                        backendDocPath = "drivers/$cleanId"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firestore document query error: ${e.message}", e)
            return Result.failure(Exception("Network error connecting to server: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Queries Cloud Firestore using runQuery for documents where driver_id or id == cleanId.
     */
    private fun queryFirestoreByField(
        projectId: String,
        cleanId: String,
        cleanPin: String
    ): Result<FirebaseDriverData> {
        val url = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents:runQuery"

        val queryJson = """
        {
          "structuredQuery": {
            "from": [{ "collectionId": "drivers" }],
            "where": {
              "fieldFilter": {
                "field": { "fieldPath": "driver_id" },
                "op": "EQUAL",
                "value": { "stringValue": "$cleanId" }
              }
            },
            "limit": 1
          }
        }
        """.trimIndent()

        val requestBody = queryJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return Result.failure(Exception("Firestore query failed with HTTP ${response.code}"))
                }

                val array = JSONArray(bodyStr)
                if (array.length() == 0) {
                    return Result.failure(Exception("Driver ID '$cleanId' not found"))
                }

                val firstItem = array.optJSONObject(0)
                val document = firstItem?.optJSONObject("document")
                    ?: return Result.failure(Exception("Driver ID '$cleanId' not found"))

                val docName = document.optString("name", "")
                val fields = document.optJSONObject("fields")
                    ?: return Result.failure(Exception("Driver document has no fields"))

                val driverName = extractFirestoreField(fields, "driver_name", "name", "driverName").ifEmpty { "Driver $cleanId" }
                val mobileNumber = extractFirestoreField(fields, "mobile_number", "driver_mobile", "mobile")
                val vehicleNumber = extractFirestoreField(fields, "vehicle_number", "vehicle_no", "vehicleNumber")
                val vehicleCategory = extractFirestoreField(fields, "vehicle_category", "category").replace("(?i)\\s*taxi\\b".toRegex(), "").trim().ifEmpty { "Mini" }
                val vehicleModel = ""
                val imageUrl = extractFirestoreField(fields, "driver_image_url", "drive_url", "image_url")
                val deviceIdInDb = extractFirestoreField(fields, "device_id", "deviceId", "simple_device_id", "active_device_id")

                val pinInDb = extractFirestoreField(fields, "pin", "driver_pin", "passcode", "password").trim()

                if (pinInDb.isEmpty()) {
                    return Result.failure(Exception("Incorrect PIN"))
                }

                if (cleanPin != pinInDb) {
                    return Result.failure(Exception("Incorrect PIN"))
                }

                return Result.success(
                    FirebaseDriverData(
                        id = cleanId,
                        pin = cleanPin,
                        driverName = driverName,
                        mobileNumber = mobileNumber,
                        vehicleNumber = vehicleNumber,
                        vehicleCategory = vehicleCategory,
                        vehicleModel = vehicleModel,
                        driverImageUrl = imageUrl,
                        deviceId = deviceIdInDb,
                        backendType = "FIRESTORE",
                        backendDocPath = docName.ifEmpty { "drivers/$cleanId" }
                    )
                )
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Queries Firebase Realtime Database for drivers/{cleanId}.json.
     */
    private fun queryRealtimeDatabase(
        projectId: String,
        cleanId: String,
        cleanPin: String
    ): Result<FirebaseDriverData> {
        val url = "https://$projectId-default-rtdb.firebaseio.com/drivers/$cleanId.json"
        val request = Request.Builder().url(url).get().build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful || bodyStr.trim() == "null" || bodyStr.isBlank()) {
                    return Result.failure(Exception("Driver ID '$cleanId' not found"))
                }

                val obj = JSONObject(bodyStr)
                val driverName = extractJsonField(obj, "driver_name", "name", "driverName").ifEmpty { "Driver $cleanId" }
                val mobileNumber = extractJsonField(obj, "mobile_number", "driver_mobile", "mobile")
                val vehicleNumber = extractJsonField(obj, "vehicle_number", "vehicle_no", "vehicleNumber")
                val vehicleCategory = extractJsonField(obj, "vehicle_category", "category").replace("(?i)\\s*taxi\\b".toRegex(), "").trim().ifEmpty { "Mini" }
                val vehicleModel = ""
                val imageUrl = extractJsonField(obj, "driver_image_url", "drive_url", "image_url")
                val deviceIdInDb = extractJsonField(obj, "device_id", "deviceId", "simple_device_id", "active_device_id")

                val pinInDb = extractJsonField(obj, "pin", "driver_pin", "passcode", "password").trim()

                if (pinInDb.isEmpty()) {
                    return Result.failure(Exception("Incorrect PIN"))
                }

                if (cleanPin != pinInDb) {
                    return Result.failure(Exception("Incorrect PIN"))
                }

                return Result.success(
                    FirebaseDriverData(
                        id = cleanId,
                        pin = cleanPin,
                        driverName = driverName,
                        mobileNumber = mobileNumber,
                        vehicleNumber = vehicleNumber,
                        vehicleCategory = vehicleCategory,
                        vehicleModel = vehicleModel,
                        driverImageUrl = imageUrl,
                        deviceId = deviceIdInDb,
                        backendType = "RTDB",
                        backendDocPath = "drivers/$cleanId"
                    )
                )
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    enum class DriverDocStatus {
        ACTIVE,
        DELETED,
        NETWORK_UNAVAILABLE
    }

    /**
     * Checks if the driver's Document ID exists in Cloud Firestore (or Google Sheets fallback).
     * Strictly designed for a daily-only check to avoid unnecessary reads/writes.
     *
     * Returns:
     * - ACTIVE: Document exists and is active.
     * - DELETED: Document ID is confirmed deleted (HTTP 404) or status marked DELETED/INACTIVE.
     * - NETWORK_UNAVAILABLE: Connection issue / offline. (Never logs out driver on network errors).
     */
    suspend fun checkDriverDocumentStatus(context: Context, driverId: String): DriverDocStatus {
        return withContext(Dispatchers.IO) {
            val cleanId = driverId.trim()
            if (cleanId.isBlank()) return@withContext DriverDocStatus.DELETED

            val appPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedDocPath = appPrefs.getString(KEY_LOGGED_BACKEND_DOC_PATH, "drivers/$cleanId") ?: "drivers/$cleanId"
            val projectId = getFirebaseProjectId(context)

            // Step 1: Query Firestore REST endpoint for this Document ID
            val docUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$savedDocPath"
            val request = Request.Builder()
                .url(docUrl)
                .get()
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    Log.d(TAG, "Daily doc lookup HTTP ${response.code} for $savedDocPath")
                    if (response.code == 404) {
                        // If document is not found at savedDocPath, double check standard "drivers/$cleanId" if different
                        if (savedDocPath != "drivers/$cleanId") {
                            val fallbackUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/drivers/$cleanId"
                            val fallbackReq = Request.Builder().url(fallbackUrl).get().build()
                            httpClient.newCall(fallbackReq).execute().use { fbResp ->
                                if (fbResp.code == 404) {
                                    Log.w(TAG, "Driver Document ID '$cleanId' deleted from Firestore (HTTP 404)")
                                    return@withContext DriverDocStatus.DELETED
                                } else if (fbResp.isSuccessful) {
                                    return@withContext DriverDocStatus.ACTIVE
                                }
                            }
                        }
                        Log.w(TAG, "Driver Document ID '$cleanId' is DELETED in Firestore (HTTP 404)")
                        return@withContext DriverDocStatus.DELETED
                    }

                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val docJson = JSONObject(bodyStr)
                        val fields = docJson.optJSONObject("fields")
                        if (fields != null) {
                            val status = extractFirestoreField(fields, "status", "driver_status", "account_status").trim()
                            if (status.equals("DELETED", ignoreCase = true) ||
                                status.equals("INACTIVE", ignoreCase = true) ||
                                status.equals("DISABLED", ignoreCase = true) ||
                                status.equals("SUSPENDED", ignoreCase = true)) {
                                Log.w(TAG, "Driver Document ID '$cleanId' exists but status is '$status'")
                                return@withContext DriverDocStatus.DELETED
                            }
                        }
                        return@withContext DriverDocStatus.ACTIVE
                    }

                    // Any server error or temporary error code - don't logout
                    return@withContext DriverDocStatus.NETWORK_UNAVAILABLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Daily driver doc check network error: ${e.message}")
                // If Google Sheets is configured, check Google Sheets as fallback
                val sheetsCheck = GoogleSheetsSyncManager.checkDriverExistsInSheets(context, cleanId)
                if (sheetsCheck != DriverDocStatus.NETWORK_UNAVAILABLE) {
                    return@withContext sheetsCheck
                }
                return@withContext DriverDocStatus.NETWORK_UNAVAILABLE
            }
        }
    }
}
