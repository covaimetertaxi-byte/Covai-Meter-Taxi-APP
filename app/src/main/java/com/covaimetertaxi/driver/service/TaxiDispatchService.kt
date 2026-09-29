package com.covaimetertaxi.driver.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.covaimetertaxi.driver.MainActivity
import com.covaimetertaxi.driver.data.Trip
import com.covaimetertaxi.driver.network.SupabaseDriver
import com.covaimetertaxi.driver.network.SupabaseManager
import com.covaimetertaxi.driver.network.SupabaseTrip
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

object TaxiDispatchServiceState {
    private val _isOnline = MutableStateFlow(false)
    val isOnline = _isOnline.asStateFlow()

    private val _openTrips = MutableStateFlow<List<SupabaseTrip>>(emptyList())
    val openTrips = _openTrips.asStateFlow()

    private val _activeTrip = MutableStateFlow<SupabaseTrip?>(null)
    val activeTrip = _activeTrip.asStateFlow()

    private val _latestTripAlert = MutableStateFlow<SupabaseTrip?>(null)
    val latestTripAlert = _latestTripAlert.asStateFlow()

    private val _ignoredTrips = MutableStateFlow<Map<String, Long>>(emptyMap())
    val ignoredTrips = _ignoredTrips.asStateFlow()

    fun setOnline(online: Boolean) {
        _isOnline.value = online
    }

    fun setOpenTrips(list: List<SupabaseTrip>) {
        _openTrips.value = list
    }

    fun setActiveTrip(trip: SupabaseTrip?) {
        _activeTrip.value = trip
    }

    fun triggerNewTripAlert(trip: SupabaseTrip?) {
        _latestTripAlert.value = trip
    }

    fun ignoreTrip(tripId: String) {
        val updated = _ignoredTrips.value.toMutableMap()
        updated[tripId] = System.currentTimeMillis()
        _ignoredTrips.value = updated
    }
}

class TaxiDispatchService : Service() {
    companion object {
        const val TAG = "TaxiDispatchService"
        const val CHANNEL_ID = "TaxiDispatchChannel"
        const val NOTIFICATION_ID = 2002

        var activeInstance: TaxiDispatchService? = null

        fun startService(context: Context) {
            val intent = Intent(context, TaxiDispatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TaxiDispatchService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var locationManager: LocationManager? = null
    private var isGpsListening = false
    var lastKnownLocation: Location? = null

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
            Log.d(TAG, "Driver location updated: ${location.latitude}, ${location.longitude}")
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        
        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Starting TaxiDispatchService (ONLINE modes)...")
        
        // Register foreground notification immediately to satisfy OS requirements
        val notification = createServiceNotification("Covai Meter Taxi Online", "Awaiting new trip requests near you...")
        startForeground(NOTIFICATION_ID, notification)

        // Acquire persistent CPU wake lock to survive screen sleep, locks, calls, background states
        try {
            if (cpuWakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                cpuWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "CovaiMeterTaxi:DispatchCpuWakeLock"
                )?.apply {
                    acquire()
                }
                Log.d(TAG, "Dispatch CPU WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed acquiring cpuWakeLock", e)
        }

        TaxiDispatchServiceState.setOnline(true)

        // 1. Initialize Supabase configurations and connect WS Realtime Socket
        SupabaseManager.startListeningToRealtime(this)

        // 2. Start GPS location listening & database coordinates push loop (every 30s)
        startGpsListening()
        startCoordinatesUploadLoop()

        // 3. Bind Websocket Flows to capture realtime database rows insertion/assignments
        startRealtimeDbObservation()

        // 4. Initial database pull to view current OPEN trips
        pullOpenTripsSilently()

        // 5. Start periodic poll to check trip states and retrigger alerts (every 10s)
        startPeriodicTripPoller()

        // 6. Observe open trips flow to stop loud alerts if the ringing trip is removed or closed
        startOpenTripsObserver()

        return START_STICKY
    }

    private fun startGpsListening() {
        if (isGpsListening) return

        // Explicitly check for location permissions before requesting updates
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Cannot start GPS listening: location permissions not granted")
            return
        }

        try {
            locationManager?.let { mgr ->
                var registered = false
                // Register for both GPS and network to ensure high availability (30s interval)
                if (mgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 2f, locationListener)
                    registered = true
                }
                if (mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    mgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000L, 2f, locationListener)
                    registered = true
                }
                lastKnownLocation = mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                    ?: mgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                isGpsListening = registered
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking registers", e)
        }
    }

    private fun startCoordinatesUploadLoop() {
        serviceScope.launch {
            while (isActive) {
                // Read local SharedPreferences profile details
                val prefs = getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
                val name = prefs.getString("driver_name", "") ?: ""
                val mobile = prefs.getString("driver_mobile", "N/A") ?: "N/A"
                val vehicleNo = prefs.getString("vehicle_number", "") ?: ""
                val vehicleCat = prefs.getString("vehicle_category", "Mini") ?: "Mini"

                if (name.isNotEmpty() && vehicleNo.isNotEmpty()) {
                    val lat = lastKnownLocation?.latitude
                    val lng = lastKnownLocation?.longitude
                    
                    val nowIso = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                        .format(Date(com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis()))

                    val driverProfile = SupabaseDriver(
                        id = SupabaseManager.getDriverId(applicationContext),
                        driver_name = name,
                        mobile_number = mobile,
                        vehicle_number = vehicleNo,
                        vehicle_type = vehicleCat,
                        online_status = true,
                        latitude = lat,
                        longitude = lng,
                        last_seen = nowIso
                    )

                    Log.d(TAG, "Syncing driver position & active online status to Supabase...")
                    SupabaseManager.upsertDriver(applicationContext, driverProfile)
                }

                delay(30000) // update location exactly every 30 seconds
            }
        }
    }

    private fun isTripWithinRadius(trip: SupabaseTrip): Boolean {
        val dispatchType = trip.dispatch_type ?: "BROADCAST"
        if (dispatchType.uppercase(java.util.Locale.ROOT) != "RADIUS") {
            return true
        }
        val pickupLat = trip.pickup_latitude ?: return true
        val pickupLng = trip.pickup_longitude ?: return true
        val radiusKms = trip.radius_kms ?: 10.0
        
        val driverLoc = lastKnownLocation ?: return true
        
        val results = FloatArray(1)
        return try {
            Location.distanceBetween(driverLoc.latitude, driverLoc.longitude, pickupLat, pickupLng, results)
            val distanceKms = results[0] / 1000.0
            distanceKms <= radiusKms
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating distance for trip", e)
            true
        }
    }

    private fun startRealtimeDbObservation() {
        serviceScope.launch {
            SupabaseManager.realTimeTripFlow.collect { trip ->
                Log.d(TAG, "Realtime notification event captured! Trip ID: ${trip.trip_id}, Status: ${trip.status}")
                
                // Repull list whenever there's any trips modification
                pullOpenTripsSilently()

                // If the updated trip is the one currently alerting and it is no longer OPEN, stop the sound immediately
                val currentAlert = TaxiDispatchServiceState.latestTripAlert.value
                if (currentAlert != null && currentAlert.id == trip.id && trip.status != "OPEN") {
                    Log.d(TAG, "Realtime: Current alerting trip ${trip.id} status changed to ${trip.status}. Stopping loud alert!")
                    stopLoudAlert()
                }

                // If a new OPEN trip drops, trigger loudest possible alert indicators
                if (trip.status == "OPEN" && isTripWithinRadius(trip)) {
                    val ongoingDisp = TaxiDispatchServiceState.activeTrip.value != null
                    val meterStatus = com.covaimetertaxi.driver.service.TaxiMeterService.tripState.value.status
                    val meterRunning = meterStatus == com.covaimetertaxi.driver.service.TripStatus.RUNNING || meterStatus == com.covaimetertaxi.driver.service.TripStatus.PAUSED
                    val isOngoing = ongoingDisp || meterRunning

                    if (!isOngoing) {
                        val ignoredTime = TaxiDispatchServiceState.ignoredTrips.value[trip.id ?: ""] ?: 0L
                        if (System.currentTimeMillis() - ignoredTime >= 60000L) {
                            triggerLoudAlert(trip)
                        } else {
                            Log.d(TAG, "Trip is currently ignored. Squelching sound.")
                        }
                    } else {
                         Log.d(TAG, "Driver has active ongoing trip. Squelching sound.")
                    }
                }
                // If trip accepted by this driver, keep it active
                val myDriverId = SupabaseManager.getDriverId(applicationContext)
                if (trip.status == "ACCEPTED" && trip.driver_id?.trim()?.equals(myDriverId.trim(), ignoreCase = true) == true) {
                    TaxiDispatchServiceState.setActiveTrip(trip)
                    Log.d(TAG, "Trip accepted! Stop sounds.")
                    stopLoudAlert()
                } else if (trip.status == "COMPLETED" && trip.driver_id?.trim()?.equals(myDriverId.trim(), ignoreCase = true) == true) {
                    TaxiDispatchServiceState.setActiveTrip(null)
                    Log.d(TAG, "Trip completed! Stop sounds.")
                    stopLoudAlert()
                }
            }
        }
    }

    private fun startOpenTripsObserver() {
        serviceScope.launch {
            TaxiDispatchServiceState.openTrips.collect { openTrips ->
                val currentAlert = TaxiDispatchServiceState.latestTripAlert.value
                if (currentAlert != null) {
                    val stillOpen = openTrips.any { it.id == currentAlert.id }
                    if (!stillOpen) {
                        Log.d(TAG, "OpenTripsObserver: Current alerting trip ${currentAlert.id} is no longer in open list. Stopping loud alert!")
                        stopLoudAlert()
                    }
                }
            }
        }
    }

    private fun pullOpenTripsSilently() {
        serviceScope.launch {
            val allTrips = SupabaseManager.fetchAllTrips(applicationContext)
            val openOnes = allTrips.filter { it.status == "OPEN" && isTripWithinRadius(it) }
            TaxiDispatchServiceState.setOpenTrips(openOnes)

            val myId = SupabaseManager.getDriverId(applicationContext)
            val activeOne = allTrips.find { it.status == "ACCEPTED" && it.driver_id?.trim()?.equals(myId.trim(), ignoreCase = true) == true }
            TaxiDispatchServiceState.setActiveTrip(activeOne)
        }
    }

    private fun triggerLoudAlert(trip: SupabaseTrip) {
        // Stop any previous playing alerts
        stopLoudAlert()

        // 1. Play continuous ringtone
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) 
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
            }
            ringtone?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing loud ringtone", e)
        }

        // 2. Continuous Vibration pattern
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 500, 1000), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 1000, 500, 1000), 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing vibration", e)
        }

        // 3. Wake lock screen
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CovaiMeterTaxi:NewTripAlertWakeLock"
            )
            wakeLock?.acquire(8000L) // Keep screen bright for 8 seconds
        } catch (e: Exception) {
            Log.e(TAG, "Failed locking wakeup", e)
        }

        // 4. Update memory flow to display in-app popup dialog to user instantly
        TaxiDispatchServiceState.triggerNewTripAlert(trip)

        // 5. Send Heads-Up High-Priority Android Notification
        sendHeadsUpNotification(trip)
    }

    fun stopLoudAlert() {
        try {
            ringtone?.stop()
            ringtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping ringtone", e)
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibration", e)
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wakeLock", e)
        }

        TaxiDispatchServiceState.triggerNewTripAlert(null)
    }

    private fun sendHeadsUpNotification(trip: SupabaseTrip) {
        val clickIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_DISPATCH_ALERT", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 101, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.covaimetertaxi.driver.R.mipmap.ic_launcher)
            .setContentTitle("🚨 NEW TRIP AVAILABLE")
            .setContentText("Pickup: ${trip.pickup_location.orEmpty().take(30)} -> Drop: ${trip.drop_location.orEmpty().take(30)}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("🚕 New Trip Request!\n\nPickup: ${trip.pickup_location}\nDrop: ${trip.drop_location}\nFare: ₹${trip.estimated_fare}"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(Notification_ID_Alert, builder.build())
    }

    private val Notification_ID_Alert = 3003

    private fun startPeriodicTripPoller() {
        serviceScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(10000) // Run every 10 seconds
                try {
                    val ongoingDisp = TaxiDispatchServiceState.activeTrip.value != null
                    val meterStatus = com.covaimetertaxi.driver.service.TaxiMeterService.tripState.value.status
                    val meterRunning = meterStatus == com.covaimetertaxi.driver.service.TripStatus.RUNNING || meterStatus == com.covaimetertaxi.driver.service.TripStatus.PAUSED
                    val isOngoing = ongoingDisp || meterRunning

                    if (!isOngoing) {
                        // Driver is available for new trips! Fetch all trips from Supabase
                        val allTrips = SupabaseManager.fetchAllTrips(applicationContext)
                        val openTrips = allTrips.filter { it.status == "OPEN" && isTripWithinRadius(it) }
                        
                        // Update the list of open trips to trigger UI updates
                        TaxiDispatchServiceState.setOpenTrips(openTrips)

                        // Find any open trip that should be alerted
                        val tripToAlert = openTrips.firstOrNull { trip ->
                            val ignoredTime = TaxiDispatchServiceState.ignoredTrips.value[trip.id ?: ""] ?: 0L
                            val elapsed = System.currentTimeMillis() - ignoredTime
                            elapsed >= 60000L
                        }

                        if (tripToAlert != null) {
                            val currentAlert = TaxiDispatchServiceState.latestTripAlert.value
                            if (currentAlert?.id != tripToAlert.id || ringtone == null || ringtone?.isPlaying == false) {
                                Log.d(TAG, "Re-triggering audio alert and dialog for trip: ${tripToAlert.id} (60 seconds elapsed since ignored/new)")
                                triggerLoudAlert(tripToAlert)
                            }
                        }
                    } else {
                        // Squelch any active alerts if driver starts/is on an ongoing trip
                        stopLoudAlert()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic trip poller", e)
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying TaxiDispatchService (OFFLINE mode)...")
        if (activeInstance == this) {
            activeInstance = null
        }
        stopLoudAlert()

        // 1. Offload GPS position coordinates updates
        if (isGpsListening) {
            try {
                locationManager?.removeUpdates(locationListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing location registration on destroy", e)
            } finally {
                isGpsListening = false
            }
        }

        // 2. Shut down background active scopes
        serviceJob.cancel()

        // 3. Set driver offline in database to hide from map
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
            val name = prefs.getString("driver_name", "") ?: ""
            val vehicleNo = prefs.getString("vehicle_number", "") ?: ""
            val vehicleCat = prefs.getString("vehicle_category", "Mini") ?: "Mini"

            if (name.isNotEmpty() && vehicleNo.isNotEmpty()) {
                val driverProfile = SupabaseDriver(
                    id = SupabaseManager.getDriverId(applicationContext),
                    driver_name = name,
                    mobile_number = prefs.getString("driver_mobile", "N/A") ?: "N/A",
                    vehicle_number = vehicleNo,
                    vehicle_type = vehicleCat,
                    online_status = false
                )
                SupabaseManager.upsertDriver(applicationContext, driverProfile)
            }
            
            // Disconnect socket realtime listener
            SupabaseManager.stopListeningToRealtime()
        }

        try {
            if (cpuWakeLock?.isHeld == true) {
                cpuWakeLock?.release()
            }
            cpuWakeLock = null
            Log.d(TAG, "Dispatch CPU WakeLock released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing cpuWakeLock", e)
        }

        TaxiDispatchServiceState.setOnline(false)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved called in TaxiDispatchService - Scheduling service restart if Online")
        // Only reschedule if the driver is supposed to be online
        val prefs = getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
        val hasDriverProfile = (prefs.getString("driver_name", "") ?: "").isNotEmpty()
        if (hasDriverProfile) {
            val restartServiceIntent = Intent(applicationContext, TaxiDispatchService::class.java).apply {
                `package` = packageName
            }
            val restartServicePendingIntent = PendingIntent.getService(
                this, 2, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmService = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmService?.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createServiceNotification(title: String, text: String): Notification {
        val clickIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 100, clickIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(com.covaimetertaxi.driver.R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Taxi Dispatch Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channels high priority real-time dispatch taxi bookings and rides alerts."
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }
}
