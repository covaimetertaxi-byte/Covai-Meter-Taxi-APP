package com.covaimetertaxi.driver.network

import android.content.Context
import android.util.Log
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class SupabaseDriver(
    val id: String,
    val driver_name: String,
    val mobile_number: String,
    val vehicle_number: String,
    val vehicle_type: String,
    val online_status: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val last_seen: String? = null,
    val created_at: String? = null,
    val status: String? = null,
    val is_blocked: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseTrip(
    val id: String? = null,
    val trip_id: String? = null,
    val customer_name: String? = null,
    val customer_mobile: String? = null,
    val pickup_location: String? = null,
    val drop_location: String? = null,
    val vehicle_type: String? = null,
    val trip_type: String? = null,
    val estimated_fare: Double? = null,
    val notes: String? = null,
    val status: String? = null, // OPEN, ACCEPTED, COMPLETED
    val driver_id: String? = null,
    val accepted_at: String? = null,
    val completed_at: String? = null,
    val created_at: String? = null,
    val base_fare: Double? = null,
    val kms_fare: Double? = null,
    val driver_name: String? = null,
    val driver_vehicle_number: String? = null,
    val driver_mobile: String? = null,
    val dispatch_type: String? = null,
    val pickup_latitude: Double? = null,
    val pickup_longitude: Double? = null,
    val radius_kms: Double? = null
)

@JsonClass(generateAdapter = true)
data class RealtimePayload(
    val event: String,
    val topic: String,
    val payload: RealtimeChangeData
)

@JsonClass(generateAdapter = true)
data class RealtimeChangeData(
    val type: String? = null, // INSERT, UPDATE
    val record: Map<String, Any?>? = null,
    val old_record: Map<String, Any?>? = null
)

object SupabaseManager {
    private const val TAG = "SupabaseManager"
    private const val PREFS_NAME = "CovaiSupabasePrefs"
    private const val KEY_URL = "supabase_url"
    private const val KEY_KEY = "supabase_key"

    // Default Sandbox Supabase Credentials (the user can customize these dynamically via settings)
    const val DEFAULT_SUPABASE_URL = "https://zkzersjscdizhvvutdxh.supabase.co"
    const val DEFAULT_SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpremVyc2pzY2Rpemh2dnV0ZHhoIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODE5Njk4MTgsImV4cCI6MjA5NzU0NTgxOH0.6sSvvwu5aTqdlKJuKFaglDj_X1VWLqrXNzrCaoSrqKA"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val tripFlow = MutableSharedFlow<SupabaseTrip>(extraBufferCapacity = 64)
    val realTimeTripFlow = tripFlow.asSharedFlow()

    private var activeSocket: WebSocket? = null
    private var isWebSocketConnected = false
    private var heartbeatJob: Job? = null
    private var webSocketScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun getSupabaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, DEFAULT_SUPABASE_URL) ?: DEFAULT_SUPABASE_URL
        return if (url.trim().isEmpty() || url == "https://your-supabase-project.supabase.co") {
            DEFAULT_SUPABASE_URL
        } else {
            url.trim()
        }
    }

    fun getSupabaseKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_KEY, DEFAULT_SUPABASE_KEY) ?: DEFAULT_SUPABASE_KEY
        return if (key.trim().isEmpty() || key == "YOUR_SUPABASE_ANON_KEY") {
            DEFAULT_SUPABASE_KEY
        } else {
            key.trim()
        }
    }

    fun saveSupabaseConfig(context: Context, url: String, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_KEY, key.trim())
            .apply()
    }

    fun getDriverId(context: Context): String {
        val prefs = context.getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
        var driverId = prefs.getString("unique_driver_id", "") ?: ""
        if (driverId.isEmpty()) {
            driverId = UUID.randomUUID().toString()
            prefs.edit().putString("unique_driver_id", driverId).apply()
        }
        return driverId
    }

    // Checking if config is set properly to prevent blank requests
    fun isConfigured(context: Context): Boolean {
        return true
    }

    // --- DB REST API SERVICES ---

    suspend fun upsertDriver(context: Context, driver: SupabaseDriver): Boolean {
        if (!isConfigured(context)) return false
        val url = "${getSupabaseUrl(context)}/rest/v1/drivers"
        val key = getSupabaseKey(context)
        val json = moshi.adapter(SupabaseDriver::class.java).toJson(driver)

        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "upsertDriver response: ${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error upserting driver profile", e)
                false
            }
        }
    }

    suspend fun fetchDrivers(context: Context): List<SupabaseDriver> {
        if (!isConfigured(context)) return emptyList()
        val url = "${getSupabaseUrl(context)}/rest/v1/drivers?select=*"
        val key = getSupabaseKey(context)

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, SupabaseDriver::class.java)
                        val adapter = moshi.adapter<List<SupabaseDriver>>(listType)
                        adapter.fromJson(bodyString) ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching drivers from Supabase", e)
                emptyList()
            }
        }
    }

    suspend fun fetchAllTrips(context: Context): List<SupabaseTrip> {
        if (!isConfigured(context)) return emptyList()
        val url = "${getSupabaseUrl(context)}/rest/v1/trips?select=*&order=created_at.desc"
        val key = getSupabaseKey(context)

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, SupabaseTrip::class.java)
                        adapter<List<SupabaseTrip>>(listType).fromJson(bodyString) ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching trips", e)
                emptyList()
            }
        }
    }

    suspend fun postTrip(context: Context, trip: SupabaseTrip): Boolean {
        if (!isConfigured(context)) return false
        val url = "${getSupabaseUrl(context)}/rest/v1/trips"
        val key = getSupabaseKey(context)
        val json = moshi.adapter(SupabaseTrip::class.java).toJson(trip)

        val body = json.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "postTrip response: ${response.code}")
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error posting trip", e)
                false
            }
        }
    }

    /**
     * Atomically accept a trip only if status is currently OPEN.
     * Uses filter status=eq.OPEN on modification to lock trip cleanly.
     */
    suspend fun acceptTrip(context: Context, tripId: String, driverId: String): Boolean {
        if (!isConfigured(context)) return false
        val url = "${getSupabaseUrl(context)}/rest/v1/trips?id=eq.$tripId&status=eq.OPEN"
        val key = getSupabaseKey(context)

        // Fetch driver profile settings
        val driverPrefs = context.getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
        val driverName = driverPrefs.getString("driver_name", "Driver") ?: "Driver"
        val driverVehicle = driverPrefs.getString("vehicle_number", "N/A") ?: "N/A"
        val driverMobileNumber = driverPrefs.getString("driver_mobile", "N/A") ?: "N/A"

        val nowIso = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .format(java.util.Date(com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()))

        val updateFields = mapOf(
            "status" to "ACCEPTED",
            "driver_id" to driverId,
            "driver_name" to driverName,
            "driver_vehicle_number" to driverVehicle,
            "driver_mobile" to driverMobileNumber,
            "accepted_at" to nowIso,
            "completed_at" to null
        )
        val json = moshi.adapter(Map::class.java).toJson(updateFields)
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .patch(body)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Prefer", "return=representation")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "acceptTrip result: $responseBody")
                        // If it successfully updated, it returns a JSON list with 1 item. If already taken, empty list [].
                        responseBody.contains("\"id\"") && !responseBody.equals("[]", ignoreCase = true)
                    } else {
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accepting trip via Supabase", e)
                false
            }
        }
    }

    suspend fun completeTrip(context: Context, tripId: String): Boolean {
        if (!isConfigured(context)) return false
        val url = "${getSupabaseUrl(context)}/rest/v1/trips?id=eq.$tripId"
        val key = getSupabaseKey(context)

        val nowIso = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .format(java.util.Date(com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()))

        val updateFields = mapOf(
            "status" to "COMPLETED",
            "completed_at" to nowIso
        )
        val json = moshi.adapter(Map::class.java).toJson(updateFields)
        val body = json.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .patch(body)
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error completing trip via Supabase", e)
                false
            }
        }
    }

    private fun <T> adapter(type: java.lang.reflect.Type) = moshi.adapter<T>(type)

    // --- REALTIME WEBSOCKET SYSTEM ---

    fun startListeningToRealtime(context: Context) {
        if (!isConfigured(context)) {
            Log.e(TAG, "Cannot start Realtime listeners: Supabase not configured!")
            return
        }
        if (isWebSocketConnected) return

        webSocketScope.cancel()
        webSocketScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val baseUrl = getSupabaseUrl(context).replace("https://", "").replace("http://", "")
        val key = getSupabaseKey(context)
        val socketUrl = "wss://$baseUrl/realtime/v1/websocket?apikey=$key&vsn=1.0.0"

        val request = Request.Builder().url(socketUrl).build()

        activeSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Supabase Realtime WebSocket successfully connected!")
                isWebSocketConnected = true

                // Subscribe to public.trips table postgres changes
                val subscribeMsg = """
                {
                  "topic": "realtime:public:trips",
                  "event": "phx_join",
                  "payload": {
                    "config": {
                      "postgres_changes": [
                        {
                          "event": "*",
                          "schema": "public",
                          "table": "trips"
                        }
                      ]
                    }
                  },
                  "ref": "subscribe_trips_postgres"
                }
                """.trimIndent()
                webSocket.send(subscribeMsg)

                // Start periodic hearbeat sender
                heartbeatJob?.cancel()
                heartbeatJob = webSocketScope.launch {
                    while (isActive) {
                        delay(25000)
                        val ping = """
                        {
                          "topic": "phoenix",
                          "event": "heartbeat",
                          "payload": {},
                          "ref": "hb_pulse"
                        }
                        """.trimIndent()
                        if (isWebSocketConnected) {
                            webSocket.send(ping)
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    Log.d(TAG, "WS Message: $text")
                    // Parse Realtime payload
                    if (text.contains("postgres_changes")) {
                        parseAndEmitTripChange(text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing server WebSocket notification", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Supabase Realtime WebSocket closed: $reason ($code)")
                isWebSocketConnected = false
                heartbeatJob?.cancel()
                reconnectSoon(context)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Supabase Realtime WebSocket failure: ${t.message}", t)
                isWebSocketConnected = false
                heartbeatJob?.cancel()
                reconnectSoon(context)
            }
        })
    }

    private fun reconnectSoon(context: Context) {
        webSocketScope.launch {
            if (!isWebSocketConnected) {
                delay(10000) // retry connection in 10 seconds
                Log.d(TAG, "Attempting background reconnection to Supabase Realtime Socket...")
                startListeningToRealtime(context)
            }
        }
    }

    fun stopListeningToRealtime() {
        heartbeatJob?.cancel()
        activeSocket?.close(1000, "Normal termination")
        activeSocket = null
        isWebSocketConnected = false
        webSocketScope.cancel()
        Log.d(TAG, "Stopped all active Supabase Realtime operations.")
    }

    private fun parseAndEmitTripChange(rawJson: String) {
        try {
            // We can parse the json dynamically using simple map matching rather than rigid custom nested serializers
            val mapAdapter = moshi.adapter(Map::class.java)
            val root = mapAdapter.fromJson(rawJson) ?: return
            val payload = root["payload"] as? Map<*, *> ?: return
            val data = payload["data"] as? Map<*, *> ?: return
            val record = data["record"] as? Map<*, *> ?: return

            // Parse individual trip fields
            val status = record["status"] as? String ?: "OPEN"
            val idStr = record["id"]?.toString() ?: ""
            val tripIdStr = record["trip_id"]?.toString() ?: ""
            val customerName = record["customer_name"]?.toString() ?: "Customer"
            val customerMobileNumStr = record["customer_mobile"]?.toString() ?: ""
            val pickupLocation = record["pickup_location"]?.toString() ?: ""
            val dropLocation = record["drop_location"]?.toString() ?: ""
            val vehicleTypeStr = record["vehicle_type"]?.toString() ?: "Mini"
            val tripTypeStr = record["trip_type"]?.toString() ?: "One-Way"
            val estimatedFareDouble = (record["estimated_fare"]?.toString()?.toDoubleOrNull()) ?: 0.0
            val notesStr = record["notes"]?.toString() ?: ""
            val driverIdStr = record["driver_id"]?.toString()
            val createdAtStr = record["created_at"]?.toString() ?: ""
            val acceptedAtStr = record["accepted_at"]?.toString()
            val completedAtStr = record["completed_at"]?.toString()
            val baseFareDouble = record["base_fare"]?.toString()?.toDoubleOrNull()
            val kmsFareDouble = record["kms_fare"]?.toString()?.toDoubleOrNull()
            val dispatchTypeVal = record["dispatch_type"]?.toString()
            val pickupLatVal = record["pickup_latitude"]?.toString()?.toDoubleOrNull()
            val pickupLngVal = record["pickup_longitude"]?.toString()?.toDoubleOrNull()
            val radiusKmsVal = record["radius_kms"]?.toString()?.toDoubleOrNull()

            val trip = SupabaseTrip(
                id = idStr,
                trip_id = tripIdStr,
                customer_name = customerName,
                customer_mobile = customerMobileNumStr,
                pickup_location = pickupLocation,
                drop_location = dropLocation,
                vehicle_type = vehicleTypeStr,
                trip_type = tripTypeStr,
                estimated_fare = estimatedFareDouble,
                notes = notesStr,
                status = status,
                driver_id = driverIdStr,
                created_at = createdAtStr,
                accepted_at = acceptedAtStr,
                completed_at = completedAtStr,
                base_fare = baseFareDouble,
                kms_fare = kmsFareDouble,
                dispatch_type = dispatchTypeVal,
                pickup_latitude = pickupLatVal,
                pickup_longitude = pickupLngVal,
                radius_kms = radiusKmsVal
            )

            // Emit to flow
            webSocketScope.launch {
                tripFlow.emit(trip)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing Realtime postgres_changes record payload", e)
        }
    }
}
