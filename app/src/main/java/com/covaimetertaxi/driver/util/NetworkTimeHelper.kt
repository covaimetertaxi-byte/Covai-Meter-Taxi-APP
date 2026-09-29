package com.covaimetertaxi.driver.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object NetworkTimeHelper {
    private const val TAG = "NetworkTimeHelper"
    private const val PREFS_NAME = "covai_network_time_prefs"
    private const val KEY_TIME_OFFSET_MS = "key_time_offset_ms"
    private const val KEY_LAST_SYNC_TIMESTAMP = "key_last_sync_timestamp"

    val IST_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
    
    @Volatile
    private var timeOffsetMs: Long? = null

    private var sharedPrefs: SharedPreferences? = null

    /**
     * Initializes the helper with an application context to restore cached time offset.
     * This protects against wrong device clocks even when offline or before network connects.
     */
    fun init(context: Context) {
        if (sharedPrefs == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs = prefs
            if (prefs.contains(KEY_TIME_OFFSET_MS)) {
                timeOffsetMs = prefs.getLong(KEY_TIME_OFFSET_MS, 0L)
                Log.d(TAG, "Restored cached network time offset: $timeOffsetMs ms")
            }
        }
    }

    /**
     * Queries reliable web servers to read the server's correct HTTP Date header.
     * This calculates the offset relative to the local system clock to ensure pinpoint overall accuracy
     * even if the phone's date or time is incorrectly set by the user.
     */
    suspend fun syncTimeOffset(context: Context? = null) {
        if (context != null && sharedPrefs == null) {
            init(context)
        }
        withContext(Dispatchers.IO) {
            val serverUrls = listOf(
                "https://www.google.com",
                "https://cloudflare.com",
                "https://www.apple.com"
            )
            for (url in serverUrls) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .head()
                        .build()
                    client.newCall(request).execute().use { response ->
                        val dateStr = response.header("Date")
                        if (dateStr != null) {
                            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                            val serverDate = format.parse(dateStr)
                            if (serverDate != null) {
                                val systemTime = System.currentTimeMillis()
                                val networkTime = serverDate.time
                                val calculatedOffset = networkTime - systemTime
                                timeOffsetMs = calculatedOffset
                                sharedPrefs?.edit()
                                    ?.putLong(KEY_TIME_OFFSET_MS, calculatedOffset)
                                    ?.putLong(KEY_LAST_SYNC_TIMESTAMP, systemTime)
                                    ?.apply()
                                Log.d(TAG, "Successfully synced network time from $url. System time offset: $calculatedOffset ms")
                                return@withContext
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Sync failed for $url: ${e.message}")
                }
            }
        }
    }

    /**
     * Computes the current time in milliseconds with precision compensation if synced, otherwise falls back to system.
     */
    fun getCurrentTimeMillis(): Long {
        val offset = timeOffsetMs
        return if (offset != null) {
            System.currentTimeMillis() + offset
        } else {
            System.currentTimeMillis()
        }
    }

    /**
     * Returns true if accurate network time offset has been synced or restored from cache.
     */
    fun isTimeOffsetAvailable(): Boolean = timeOffsetMs != null

    /**
     * Returns the current Calendar instance synchronized to Asia/Kolkata timezone with accurate compensated time.
     */
    fun getIstCalendar(): Calendar {
        val cal = Calendar.getInstance(IST_TIMEZONE)
        cal.timeInMillis = getCurrentTimeMillis()
        return cal
    }

    /**
     * Returns current IST hour (0–23) in Asia/Kolkata timezone.
     */
    fun getIstHour(): Int {
        return getIstCalendar().get(Calendar.HOUR_OF_DAY)
    }

    /**
     * Helper to retrieve SimpleDateFormat instances tied exactly to Asia/Kolkata TimeZone.
     */
    fun getAsiaKolkataFormatter(pattern: String): SimpleDateFormat {
        return SimpleDateFormat(pattern, Locale.getDefault()).apply {
            timeZone = IST_TIMEZONE
        }
    }
}

