package com.covaimetertaxi.driver.network

import android.content.Context
import android.util.Log
import com.covaimetertaxi.driver.data.Trip
import com.covaimetertaxi.driver.data.TripRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GoogleSheetsTripRequest(
    val type: String = "trip", // "login", "verify_login", "logout", "trip", "get_profile"
    val timestamp: Long = 0L,
    val tripIdCode: String = "",
    val driverId: String = "",
    val pin: String = "",
    val driverName: String = "",
    val vehicleNumber: String = "",
    val vehicleCategory: String = "",
    val vehicleModel: String = "",
    val driverMobile: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val distance: Double = 0.0,
    val durationSeconds: Long = 0L,
    val totalFare: Double = 0.0,
    val baseFare: Double = 0.0,
    val perKmFare: Double = 0.0,
    val waitingChargePerMin: Double = 0.0,
    val minimumFare: Double = 0.0,
    val nightChargePercent: Double = 0.0,
    val dateStr: String = "",
    val startLocation: String = "",
    val endLocation: String = "",
    val ccCommission: Double = 0.0,
    val customerMobile: String = "",
    val isPackageMeter: Boolean = false,
    val packageName: String = "",
    val packageBaseFare: Double = 0.0,
    val includedKm: Double = 0.0,
    val includedMinutes: Int = 0,
    val extraKmRate: Double = 0.0,
    val extraTimeRate: Double = 0.0,
    val packageWaitingChargePerMin: Double = 0.0,
    val waitingSeconds: Long = 0L,
    val deviceId: String = ""
)

@JsonClass(generateAdapter = true)
data class GoogleSheetsResponse(
    val status: String,
    val message: String? = null,
    val code: String? = null,
    val userCount: Int? = null,
    val driverId: String? = null,
    val driverName: String? = null,
    val vehicleNumber: String? = null,
    val vehicleCategory: String? = null,
    val vehicleModel: String? = null,
    val driverMobile: String? = null,
    val driverImageUrl: String? = null
)

interface GoogleSheetsSyncApi {
    @POST
    suspend fun syncTripToSheets(
        @Url url: String,
        @Body body: GoogleSheetsTripRequest
    ): retrofit2.Response<ResponseBody>
}

object GoogleSheetsSyncManager {
    private const val TAG = "GoogleSheetsSync"
    private const val PREFS_NAME = "CovaiMeterTaxiPrefs"
    private const val KEY_SHEETS_URL = "google_sheets_webapp_url"

    // Obfuscated Google Sheets URL to prevent extraction via APK decompilation
    val HARDCODED_SHEETS_URL: String
        get() = com.covaimetertaxi.driver.util.SecurityObfuscator.getSecureSheetsUrl()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Add logging and custom timeout to follow redirects properly and handle high load
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://script.google.com/") // Place-holder base URL, will be overridden by full dynamic @Url
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
        .build()

    private val api = retrofit.create(GoogleSheetsSyncApi::class.java)

    /**
     * Safely parse the raw string returned by Google Sheets Web App.
     * Prevents JsonEncodingException when Google Apps Script returns HTML error pages or redirects.
     */
    private fun parseGoogleSheetsResponse(rawString: String?): GoogleSheetsResponse? {
        if (rawString.isNullOrBlank()) return null
        val trimmed = rawString.trim()

        // Handle HTML error pages returned by Google Drive / Google Apps Script (e.g. <!DOCTYPE html>)
        if (trimmed.startsWith("<")) {
            Log.w(TAG, "Google Sheets Web App returned HTML page (Web App URL may be disabled, deleted, or unshared): ${trimmed.take(150)}")
            return GoogleSheetsResponse(
                status = "error",
                message = "Google Sheets returned HTML instead of JSON. Check Web App URL deployment."
            )
        }

        return try {
            val adapter = moshi.adapter(GoogleSheetsResponse::class.java).lenient()
            adapter.fromJson(trimmed)
        } catch (e: Exception) {
            Log.w(TAG, "Moshi parse failed, attempting JSONObject fallback: ${e.message}")
            try {
                val json = JSONObject(trimmed)
                GoogleSheetsResponse(
                    status = json.optString("status", "error"),
                    message = if (json.has("message")) json.optString("message") else null,
                    code = if (json.has("code")) json.optString("code") else null,
                    userCount = if (json.has("userCount")) json.optInt("userCount") else null,
                    driverId = if (json.has("driverId")) json.optString("driverId") else null,
                    driverName = if (json.has("driverName")) json.optString("driverName") else null,
                    vehicleNumber = if (json.has("vehicleNumber")) json.optString("vehicleNumber") else null,
                    vehicleCategory = if (json.has("vehicleCategory")) json.optString("vehicleCategory") else null,
                    vehicleModel = if (json.has("vehicleModel")) json.optString("vehicleModel") else null,
                    driverMobile = if (json.has("driverMobile")) json.optString("driverMobile") else null,
                    driverImageUrl = if (json.has("driverImageUrl")) json.optString("driverImageUrl") else null
                )
            } catch (je: Exception) {
                GoogleSheetsResponse(
                    status = if (trimmed.contains("success", ignoreCase = true) || trimmed.contains("ok", ignoreCase = true)) "success" else "error",
                    message = trimmed.take(200)
                )
            }
        }
    }

    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", "") ?: ""
        if (deviceId.isEmpty()) {
            deviceId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
            Log.d(TAG, "Generated new unique device ID: $deviceId")
        }
        return deviceId
    }

    fun getGoogleSheetsUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_SHEETS_URL, "")?.trim() ?: ""
        if (savedUrl.isNotEmpty()) {
            return savedUrl
        }
        if (HARDCODED_SHEETS_URL.isNotEmpty() && HARDCODED_SHEETS_URL != "YOUR_GOOGLE_SHEETS_WEB_APP_URL_HERE") {
            return HARDCODED_SHEETS_URL.trim()
        }
        return ""
    }

    fun saveGoogleSheetsUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SHEETS_URL, url.trim()).apply()
    }

    /**
     * Sends a single trip row to Google Sheets Web App.
     */
    suspend fun syncTrip(context: Context, trip: Trip, repository: TripRepository): Boolean {
        val customUrl = getGoogleSheetsUrl(context)
        if (customUrl.isEmpty()) {
            Log.w(TAG, "Sync failed: Google Sheets Web App URL is not configured.")
            return false
        }

        val timeFormatter = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("dd MMM yyyy, hh:mm:ss a")
        val formattedStartTime = if (trip.startTime > 0) timeFormatter.format(java.util.Date(trip.startTime)) else "N/A"
        val formattedEndTime = if (trip.endTime > 0) timeFormatter.format(java.util.Date(trip.endTime)) else "N/A"

        val request = GoogleSheetsTripRequest(
            type = "trip",
            tripIdCode = trip.tripIdCode,
            driverName = trip.driverName,
            vehicleNumber = trip.vehicleNumber,
            vehicleCategory = trip.vehicleCategory,
            vehicleModel = trip.vehicleModel,
            driverMobile = trip.driverMobile,
            startTime = formattedStartTime,
            endTime = formattedEndTime,
            distance = trip.distance,
            durationSeconds = trip.durationSeconds,
            totalFare = trip.totalFare,
            baseFare = trip.baseFare,
            perKmFare = trip.perKmFare,
            waitingChargePerMin = trip.waitingChargePerMin,
            minimumFare = trip.minimumFare,
            nightChargePercent = trip.nightChargePercent,
            dateStr = trip.dateStr,
            startLocation = trip.startLocation,
            endLocation = trip.endLocation,
            ccCommission = trip.ccCommission,
            customerMobile = trip.customerMobile,
            isPackageMeter = trip.isPackageMeter,
            packageName = trip.packageName,
            packageBaseFare = trip.packageBaseFare,
            includedKm = trip.includedKm,
            includedMinutes = trip.includedMinutes,
            extraKmRate = trip.extraKmRate,
            extraTimeRate = trip.extraTimeRate,
            packageWaitingChargePerMin = trip.packageWaitingChargePerMin,
            waitingSeconds = trip.waitingSeconds,
            deviceId = getOrCreateDeviceId(context)
        )

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Syncing trip ${trip.tripIdCode} to Sheets...")
                val response = api.syncTripToSheets(customUrl, request)
                if (response.isSuccessful) {
                    val rawBody = response.body()?.string()
                    val body = parseGoogleSheetsResponse(rawBody)
                    if (body != null && (body.status.equals("success", ignoreCase = true) || body.status.equals("OK", ignoreCase = true))) {
                        Log.i(TAG, "Successfully synced trip ${trip.tripIdCode} to Google Sheets!")
                        // Update Room entity flag
                        val updatedTrip = trip.copy(isSyncedToSheets = true)
                        repository.updateTrip(updatedTrip)
                        true
                    } else {
                        Log.w(TAG, "Sheets app returned error or non-JSON: ${body?.message ?: "Unknown error"}")
                        false
                    }
                } else {
                    Log.w(TAG, "Network response code: ${response.code()}")
                    false
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Google Sheets sync timed out after 60s for trip ${trip.tripIdCode}")
                false
            } catch (e: Exception) {
                Log.w(TAG, "Error during network sync to Google Sheets: ${e.message}")
                false
            }
        }
    }

    /**
     * Verifies driver credentials and enforces single-device login against Google Sheets Web App.
     * Returns failure if credentials fail OR if already logged in on another device.
     */
    suspend fun verifyDriverLoginWithSheets(
        context: Context,
        driverId: String,
        pin: String,
        deviceId: String
    ): Result<FirebaseDriverData> {
        val customUrl = getGoogleSheetsUrl(context)
        if (customUrl.isEmpty()) {
            return Result.failure(Exception("Google Sheets Web App URL is not configured."))
        }

        return withContext(Dispatchers.IO) {
            try {
                val formatter = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("dd MMM yyyy, hh:mm:ss a")
                val currentDateStr = formatter.format(java.util.Date(com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()))

                val request = GoogleSheetsTripRequest(
                    type = "verify_login",
                    driverId = driverId.trim(),
                    pin = pin.trim(),
                    deviceId = deviceId,
                    dateStr = currentDateStr,
                    timestamp = com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()
                )

                Log.d(TAG, "Verifying driver login with Sheets for ID $driverId, deviceId $deviceId...")
                val response = api.syncTripToSheets(customUrl, request)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Sheets server HTTP ${response.code()} error."))
                }

                val rawBody = response.body()?.string()
                val body = parseGoogleSheetsResponse(rawBody) ?: return@withContext Result.failure(Exception("Empty response from Sheets server."))

                if (body.status.equals("error", ignoreCase = true)) {
                    val msg = body.message ?: "Authentication failed."
                    Log.w(TAG, "Sheets login rejected: $msg (code: ${body.code})")
                    return@withContext Result.failure(Exception(msg))
                }

                val resolvedDriverName = (body.driverName ?: "").trim()
                val resolvedVehicle = (body.vehicleNumber ?: "").trim()
                if (resolvedDriverName.isNotBlank() || resolvedVehicle.isNotBlank()) {
                    Log.i(TAG, "Sheets login verified successfully for $resolvedDriverName ($resolvedVehicle)")
                    return@withContext Result.success(
                        FirebaseDriverData(
                            id = body.driverId?.ifBlank { driverId.trim() } ?: driverId.trim(),
                            pin = pin.trim(),
                            driverName = resolvedDriverName.ifBlank { "Driver $driverId" },
                            mobileNumber = body.driverMobile ?: "",
                            vehicleNumber = resolvedVehicle,
                            vehicleCategory = body.vehicleCategory?.ifBlank { "Mini" } ?: "Mini",
                            vehicleModel = body.vehicleModel ?: "",
                            driverImageUrl = body.driverImageUrl ?: ""
                        )
                    )
                }

                Result.failure(Exception(body.message ?: "Driver ID not found in Google Sheets."))
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Network timeout verifying driver with Sheets: ${e.message}")
                Result.failure(Exception("Connection to Sheets timed out. Please check network."))
            } catch (e: Exception) {
                Log.w(TAG, "Network exception verifying driver with Sheets: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Checks if driver exists in Google Sheets Web App.
     */
    suspend fun checkDriverExistsInSheets(
        context: Context,
        driverId: String
    ): FirebaseManager.DriverDocStatus {
        val customUrl = getGoogleSheetsUrl(context)
        if (customUrl.isEmpty()) {
            return FirebaseManager.DriverDocStatus.NETWORK_UNAVAILABLE
        }
        return withContext(Dispatchers.IO) {
            try {
                val request = GoogleSheetsTripRequest(
                    type = "verify_login",
                    driverId = driverId.trim(),
                    pin = "",
                    deviceId = FirebaseManager.getDeviceId(context),
                    timestamp = com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()
                )
                val response = api.syncTripToSheets(customUrl, request)
                if (!response.isSuccessful) {
                    return@withContext FirebaseManager.DriverDocStatus.NETWORK_UNAVAILABLE
                }
                val rawBody = response.body()?.string()
                val body = parseGoogleSheetsResponse(rawBody) ?: return@withContext FirebaseManager.DriverDocStatus.NETWORK_UNAVAILABLE
                if (body.status.equals("error", ignoreCase = true)) {
                    val msg = body.message ?: ""
                    if (msg.contains("not found", ignoreCase = true) ||
                        msg.contains("does not exist", ignoreCase = true) ||
                        msg.contains("deleted", ignoreCase = true)) {
                        return@withContext FirebaseManager.DriverDocStatus.DELETED
                    }
                }
                val resolvedDriverName = (body.driverName ?: "").trim()
                val resolvedVehicle = (body.vehicleNumber ?: "").trim()
                if (resolvedDriverName.isNotBlank() || resolvedVehicle.isNotBlank()) {
                    return@withContext FirebaseManager.DriverDocStatus.ACTIVE
                }
                FirebaseManager.DriverDocStatus.NETWORK_UNAVAILABLE
            } catch (e: Exception) {
                FirebaseManager.DriverDocStatus.NETWORK_UNAVAILABLE
            }
        }
    }

    /**
     * Sends a driver login/configuration row to Google Sheets Web App.
     */
    fun syncDriverLogin(
        context: Context,
        driverName: String,
        vehicleNumber: String,
        vehicleCategory: String,
        vehicleModel: String,
        driverMobile: String,
        driverId: String = ""
    ) {
        val customUrl = getGoogleSheetsUrl(context)
        if (customUrl.isEmpty()) {
            Log.w(TAG, "Driver login sync skipped: Google Sheets Web App URL is not configured.")
            return
        }

        val formatter = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("dd MMM yyyy, hh:mm:ss a")
        val currentDateStr = formatter.format(java.util.Date(com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()))

        val request = GoogleSheetsTripRequest(
            type = "login",
            driverId = driverId,
            timestamp = com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis(),
            dateStr = currentDateStr,
            driverName = driverName,
            vehicleNumber = vehicleNumber,
            vehicleCategory = vehicleCategory,
            vehicleModel = vehicleModel,
            driverMobile = driverMobile,
            deviceId = getOrCreateDeviceId(context)
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Syncing driver login ($driverName) to Sheets...")
                val response = api.syncTripToSheets(customUrl, request)
                if (response.isSuccessful) {
                    val rawBody = response.body()?.string()
                    val body = parseGoogleSheetsResponse(rawBody)
                    if (body != null && (body.status.equals("success", ignoreCase = true) || body.status.equals("OK", ignoreCase = true))) {
                        Log.i(TAG, "Successfully synced driver login ($driverName) to Google Sheets!")
                    } else {
                        Log.w(TAG, "Sheets app returned login error or non-JSON: ${body?.message ?: "Unknown error"}")
                    }
                } else {
                    Log.w(TAG, "Network response code for login sync: ${response.code()}")
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Driver login network sync to Google Sheets timed out (server busy or offline).")
            } catch (e: Exception) {
                Log.w(TAG, "Error during driver login network sync to Google Sheets: ${e.message}")
            }
        }
    }

    /**
     * Sends a driver logout/status row update to Google Sheets Web App.
     * Clears the active device ID so another device can log in!
     */
    fun syncDriverLogout(
        context: Context,
        driverName: String,
        vehicleNumber: String,
        vehicleCategory: String,
        vehicleModel: String,
        driverMobile: String,
        driverId: String = ""
    ) {
        val customUrl = getGoogleSheetsUrl(context)
        if (customUrl.isEmpty()) {
            Log.w(TAG, "Driver logout sync skipped: Google Sheets Web App URL is not configured.")
            return
        }

        val formatter = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("dd MMM yyyy, hh:mm:ss a")
        val currentDateStr = formatter.format(java.util.Date(com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()))

        val request = GoogleSheetsTripRequest(
            type = "logout",
            driverId = driverId,
            timestamp = com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis(),
            dateStr = currentDateStr,
            driverName = driverName,
            vehicleNumber = vehicleNumber,
            vehicleCategory = vehicleCategory,
            vehicleModel = vehicleModel,
            driverMobile = driverMobile,
            deviceId = getOrCreateDeviceId(context)
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Syncing driver logout ($driverName, ID: $driverId) to Sheets...")
                val response = api.syncTripToSheets(customUrl, request)
                if (response.isSuccessful) {
                    val rawBody = response.body()?.string()
                    val body = parseGoogleSheetsResponse(rawBody)
                    if (body != null && (body.status.equals("success", ignoreCase = true) || body.status.equals("OK", ignoreCase = true))) {
                        Log.i(TAG, "Successfully synced driver logout to Google Sheets!")
                    } else {
                        Log.w(TAG, "Sheets app returned logout error: ${body?.message ?: "Unknown error"}")
                    }
                } else {
                    Log.w(TAG, "Network response code for logout sync: ${response.code()}")
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Driver logout network sync to Google Sheets timed out.")
            } catch (e: Exception) {
                Log.w(TAG, "Error during driver logout network sync to Google Sheets: ${e.message}")
            }
        }
    }

    /**
     * Fetches user count and profile info from Google Sheets Web App.
     */
    fun fetchUserCountAndProfile(
        context: Context,
        driverName: String = "",
        vehicleNumber: String = "",
        vehicleCategory: String = "",
        vehicleModel: String = "",
        driverMobile: String = "",
        onResult: (userCount: Int, profile: GoogleSheetsResponse?) -> Unit
    ) {
        val customUrl = getGoogleSheetsUrl(context)
        if (customUrl.isEmpty()) {
            onResult(0, null)
            return
        }

        val formatter = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("dd MMM yyyy, hh:mm:ss a")
        val currentDateStr = formatter.format(java.util.Date(com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()))

        val request = GoogleSheetsTripRequest(
            type = "get_profile",
            deviceId = getOrCreateDeviceId(context),
            timestamp = com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis(),
            dateStr = currentDateStr,
            driverName = driverName,
            vehicleNumber = vehicleNumber,
            vehicleCategory = vehicleCategory,
            vehicleModel = vehicleModel,
            driverMobile = driverMobile
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Fetching user count and profile from Sheets...")
                val response = api.syncTripToSheets(customUrl, request)
                if (response.isSuccessful) {
                    val rawBody = response.body()?.string()
                    val body = parseGoogleSheetsResponse(rawBody)
                    if (body != null) {
                        val count = body.userCount ?: 0
                        withContext(Dispatchers.Main) {
                            onResult(count, body)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(0, null)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(0, null)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching user count: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(0, null)
                }
            }
        }
    }

    /**
     * Grabs all unsynced trips from database and uploads them in the background.
     */
    fun syncAllUnsyncedTrips(context: Context, repository: TripRepository, onComplete: (() -> Unit)? = null) {
        val customUrl = getGoogleSheetsUrl(context)
        if (customUrl.isEmpty()) return

        CoroutineScope(Dispatchers.Main).launch {
            val unsynced = withContext(Dispatchers.IO) {
                repository.getUnsyncedTrips()
            }
            if (unsynced.isEmpty()) {
                onComplete?.invoke()
                return@launch
            }

            Log.i(TAG, "Found ${unsynced.size} unsynced trips. Initiating batch upload...")
            var successCount = 0
            withContext(Dispatchers.IO) {
                for (trip in unsynced) {
                    val success = syncTrip(context, trip, repository)
                    if (success) {
                        successCount++
                    }
                }
            }
            onComplete?.invoke()
        }
    }
}
