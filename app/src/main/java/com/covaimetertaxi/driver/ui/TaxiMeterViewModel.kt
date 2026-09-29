package com.covaimetertaxi.driver.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.covaimetertaxi.driver.data.Trip
import com.covaimetertaxi.driver.data.TripRepository
import com.covaimetertaxi.driver.service.LiveTripState
import com.covaimetertaxi.driver.service.TaxiMeterService
import com.covaimetertaxi.driver.service.TripStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.covaimetertaxi.driver.util.GeocoderHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaxiMeterViewModel(private val repository: TripRepository, context: Context) : ViewModel() {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)

    // Flow of historical trips
    val tripHistory: StateFlow<List<Trip>> = repository.allTrips
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current screen navigation state inside MVVM
    // Screens: "FORM" (Standby form), "LIVE" (Running/Paused), "SUMMARY" (Invoice/UPI Receipt), "HISTORY" (Past trips list), "SETTINGS" (Rates edit)
    private val _currentScreen = MutableStateFlow(if (TaxiMeterService.hasActiveTrip(context)) "LIVE" else "FORM")
    val currentScreen = _currentScreen.asStateFlow()

    // Active customer mobile number for the current trip
    private val _customerMobileNum = MutableStateFlow("")
    val customerMobileNum = _customerMobileNum.asStateFlow()

    // For viewing invoice details
    private val _selectedTripForInvoice = MutableStateFlow<Trip?>(null)
    val selectedTripForInvoice = _selectedTripForInvoice.asStateFlow()

    // Driver configurations state
    private val _hasAcceptedTerms = MutableStateFlow(prefs.getBoolean("has_accepted_terms", false))
    val hasAcceptedTerms = _hasAcceptedTerms.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(
        prefs.getBoolean("is_driver_logged_in", false) ||
                (prefs.getString("driver_name", "")?.trim()?.isNotEmpty() == true &&
                        prefs.getString("vehicle_number", "")?.trim()?.isNotEmpty() == true)
    )
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _driverId = MutableStateFlow(prefs.getString("unique_driver_id", "") ?: "")
    val driverId = _driverId.asStateFlow()

    private val _driverName = MutableStateFlow(prefs.getString("driver_name", "") ?: "")
    val driverName = _driverName.asStateFlow()

    private val _vehicleNumber = MutableStateFlow(prefs.getString("vehicle_number", "") ?: "")
    val vehicleNumber = _vehicleNumber.asStateFlow()

    private val _driverMobile = MutableStateFlow(prefs.getString("driver_mobile", "") ?: "")
    val driverMobile = _driverMobile.asStateFlow()

    private val _vehicleCategory = MutableStateFlow(prefs.getString("vehicle_category", "Mini") ?: "Mini")
    val vehicleCategory = _vehicleCategory.asStateFlow()

    private val _vehicleModel = MutableStateFlow(prefs.getString("vehicle_model", "") ?: "")
    val vehicleModel = _vehicleModel.asStateFlow()

    private val _driverSelfiePath = MutableStateFlow(prefs.getString("driver_selfie_path", "") ?: "")
    val driverSelfiePath = _driverSelfiePath.asStateFlow()

    fun saveDriverSelfiePath(path: String) {
        _driverSelfiePath.value = path
        prefs.edit().putString("driver_selfie_path", path).apply()
    }

    // Meter Fare Configurations (editable in Settings)
    private val _baseFare = MutableStateFlow(prefs.getFloat("base_fare", 80.0f).toDouble())
    val baseFare = _baseFare.asStateFlow()

    private val _perKmFare = MutableStateFlow(prefs.getFloat("per_km_fare", 28.0f).toDouble())
    val perKmFare = _perKmFare.asStateFlow()

    private val _waitingChargePerMin = MutableStateFlow(
        prefs.getFloat("waiting_charge", 1.0f).toDouble()
    )
    val waitingChargePerMin = _waitingChargePerMin.asStateFlow()

    private val _minimumFare = MutableStateFlow(
        prefs.getFloat("minimum_fare", 198.0f).toDouble()
    )
    val minimumFare = _minimumFare.asStateFlow()

    private val _nightChargePercent = MutableStateFlow(prefs.getFloat("night_charge", 0.0f).toDouble())
    val nightChargePercent = _nightChargePercent.asStateFlow()

    private val _isNightModeActive = MutableStateFlow(prefs.getBoolean("is_night_mode_active", false))
    val isNightModeActive = _isNightModeActive.asStateFlow()

    private val _isAdvancedUnlocked = MutableStateFlow(prefs.getBoolean("is_advanced_unlocked", false))
    val isAdvancedUnlocked = _isAdvancedUnlocked.asStateFlow()

    fun setAdvancedUnlocked(unlocked: Boolean) {
        _isAdvancedUnlocked.value = unlocked
        prefs.edit().putBoolean("is_advanced_unlocked", unlocked).apply()
    }

    private val _networkUserCount = MutableStateFlow(prefs.getInt("network_user_count", 0))
    val networkUserCount = _networkUserCount.asStateFlow()

    fun checkNetworkUserCountAndProfile(context: Context) {
        val currentName = _driverName.value.trim()
        val currentVNum = _vehicleNumber.value.trim()
        val currentCat = _vehicleCategory.value.trim()
        val currentModel = _vehicleModel.value.trim()
        val currentMobile = _driverMobile.value.trim()

        com.covaimetertaxi.driver.network.GoogleSheetsSyncManager.fetchUserCountAndProfile(
            context = context,
            driverName = currentName,
            vehicleNumber = currentVNum,
            vehicleCategory = currentCat,
            vehicleModel = currentModel,
            driverMobile = currentMobile
        ) { count, profile ->
            if (count > 0) {
                _networkUserCount.value = count
                prefs.edit().putInt("network_user_count", count).apply()
            }
            if (profile != null && profile.status == "success") {
                if (currentName.isEmpty() && currentVNum.isEmpty() && !profile.driverName.isNullOrBlank()) {
                    val pName = profile.driverName.uppercase(Locale.ROOT).trim()
                    val pVNum = (profile.vehicleNumber ?: "").uppercase(Locale.ROOT).trim()
                    val pCat = profile.vehicleCategory ?: "Mini"
                    val pModel = profile.vehicleModel ?: ""

                    saveDriverDetails(
                        name = pName,
                        vNumber = pVNum,
                        category = pCat,
                        model = pModel,
                        isNight = false
                    )
                    Log.i("TaxiMeterViewModel", "Auto-restored profile from Google Sheets sync: $pName - $pVNum")
                }
            }
        }
    }

    private val _defaultMeterMode = MutableStateFlow("REGULAR")
    val defaultMeterMode = _defaultMeterMode.asStateFlow()

    fun setDefaultMeterMode(mode: String) {
        _defaultMeterMode.value = mode
    }

    // Package configuration details (editable in Settings or a separate section)
    private val _packageName = MutableStateFlow(prefs.getString("pkg_name", "Hourly Package") ?: "Hourly Package")
    val packageName = _packageName.asStateFlow()

    private val _packageBaseFare = MutableStateFlow(prefs.getFloat("pkg_base_fare", 350.0f).toDouble())
    val packageBaseFare = _packageBaseFare.asStateFlow()

    private val _packagePerHourRate = MutableStateFlow(prefs.getFloat("pkg_per_hour_rate", 350.0f).toDouble())
    val packagePerHourRate = _packagePerHourRate.asStateFlow()

    private val _packagePerKmRate = MutableStateFlow(prefs.getFloat("pkg_per_km_rate", 20.0f).toDouble())
    val packagePerKmRate = _packagePerKmRate.asStateFlow()

    private val _packageIncludedKm = MutableStateFlow(prefs.getFloat("pkg_included_km", 10.0f).toDouble())
    val packageIncludedKm = _packageIncludedKm.asStateFlow()

    private val _packageIncludedMinutes = MutableStateFlow(prefs.getInt("pkg_included_minutes", 60))
    val packageIncludedMinutes = _packageIncludedMinutes.asStateFlow()

    private val _packageExtraKmRate = MutableStateFlow(prefs.getFloat("pkg_extra_km_rate", 20.0f).toDouble())
    val packageExtraKmRate = _packageExtraKmRate.asStateFlow()

    private val _packageExtraTimeRate = MutableStateFlow(2.0833)
    val packageExtraTimeRate = _packageExtraTimeRate.asStateFlow()

    private val _packageWaitingChargePerMin = MutableStateFlow(prefs.getFloat("pkg_waiting_charge", 0.0f).toDouble())
    val packageWaitingChargePerMin = _packageWaitingChargePerMin.asStateFlow()

    fun updatePackageSettings(
        name: String,
        baseFare: Double,
        includedKm: Double,
        includedMinutes: Int,
        extraKmRate: Double,
        extraTimeRate: Double,
        waitingCharge: Double,
        perHourRate: Double = _packagePerHourRate.value,
        perKmRate: Double = _packagePerKmRate.value
    ) {
        _packageName.value = name
        _packageBaseFare.value = baseFare
        _packageIncludedKm.value = includedKm
        _packageIncludedMinutes.value = includedMinutes
        _packageExtraKmRate.value = extraKmRate
        val calculatedExtraTimeRate = 2.0833
        _packageExtraTimeRate.value = calculatedExtraTimeRate
        _packageWaitingChargePerMin.value = waitingCharge
        _packagePerHourRate.value = perHourRate
        _packagePerKmRate.value = perKmRate

        prefs.edit().apply {
            putString("pkg_name", name)
            putFloat("pkg_base_fare", baseFare.toFloat())
            putFloat("pkg_included_km", includedKm.toFloat())
            putInt("pkg_included_minutes", includedMinutes)
            putFloat("pkg_extra_km_rate", extraKmRate.toFloat())
            putFloat("pkg_extra_time_rate", calculatedExtraTimeRate.toFloat())
            putFloat("pkg_waiting_charge", waitingCharge.toFloat())
            putFloat("pkg_per_hour_rate", perHourRate.toFloat())
            putFloat("pkg_per_km_rate", perKmRate.toFloat())
            apply()
        }
    }

    // Service status observer
    val serviceState: StateFlow<LiveTripState> = TaxiMeterService.tripState

    init {
        if (_baseFare.value <= 0.0) {
            _baseFare.value = 80.0
            prefs.edit().putFloat("base_fare", 80.0f).apply()
        }
        if (_perKmFare.value <= 0.0) {
            _perKmFare.value = 28.0
            prefs.edit().putFloat("per_km_fare", 28.0f).apply()
        }
        if (_packageBaseFare.value <= 0.0) {
            _packageBaseFare.value = 350.0
            prefs.edit().putFloat("pkg_base_fare", 350.0f).apply()
        }
        if (_packagePerHourRate.value <= 0.0) {
            _packagePerHourRate.value = 350.0
            prefs.edit().putFloat("pkg_per_hour_rate", 350.0f).apply()
        }
        if (_packagePerKmRate.value <= 0.0 || _packagePerKmRate.value == 25.0) {
            _packagePerKmRate.value = 20.0
            prefs.edit().putFloat("pkg_per_km_rate", 20.0f).apply()
        }
        if (_packageExtraKmRate.value <= 0.0 || _packageExtraKmRate.value == 25.0) {
            _packageExtraKmRate.value = 20.0
            prefs.edit().putFloat("pkg_extra_km_rate", 20.0f).apply()
        }
        // Detect App Update and Reset Driver Profile if it's an update (forces re-login & permission screen)
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val savedVersionCode = prefs.getLong("saved_version_code", -1L)
            val hasExistingProfile = (prefs.getString("driver_name", "") ?: "").isNotEmpty()

            if (savedVersionCode != currentVersionCode) {
                if (hasExistingProfile) {
                    prefs.edit().apply {
                        remove("driver_name")
                        remove("vehicle_number")
                        remove("vehicle_category")
                        remove("vehicle_model")
                        remove("driver_mobile")
                        remove("driver_selfie_path")
                        remove("last_synced_driver_signature")
                        apply()
                    }
                    _driverName.value = ""
                    _vehicleNumber.value = ""
                    _vehicleCategory.value = "Mini"
                    _vehicleModel.value = ""
                    _driverMobile.value = ""
                    _driverSelfiePath.value = ""
                    Log.i("TaxiMeterViewModel", "App upgraded from version $savedVersionCode to $currentVersionCode. Forcing fresh login profile setup.")
                }
                prefs.edit().putLong("saved_version_code", currentVersionCode).apply()
            }
        } catch (e: Exception) {
            Log.e("TaxiMeterViewModel", "Error checking/saving package version code: ${e.message}")
        }

        // Default both Package and Regular meter rates to 0.0 as requested
        if (!prefs.getBoolean("meter_default_zero_all_v4", false)) {
            _baseFare.value = 80.0
            _perKmFare.value = 28.0
            _waitingChargePerMin.value = 1.0
            _minimumFare.value = 198.0
            _nightChargePercent.value = 0.0
            _packageBaseFare.value = 350.0
            _packagePerHourRate.value = 350.0
            _packagePerKmRate.value = 20.0
            _packageExtraKmRate.value = 20.0
            prefs.edit()
                .putFloat("base_fare", 80.0f)
                .putFloat("per_km_fare", 28.0f)
                .putFloat("waiting_charge", 1.0f)
                .putFloat("minimum_fare", 198.0f)
                .putFloat("night_charge", 0.0f)
                .putFloat("pkg_base_fare", 350.0f)
                .putFloat("pkg_per_hour_rate", 350.0f)
                .putFloat("pkg_per_km_rate", 20.0f)
                .putFloat("pkg_extra_km_rate", 20.0f)
                .putBoolean("meter_default_zero_all_v4", true)
                .apply()
        }

        // Ensure default minimum fare for Regular meter is set to ₹198
        if (!prefs.getBoolean("meter_default_regular_min_198_v1", false)) {
            _minimumFare.value = 198.0
            prefs.edit()
                .putFloat("minimum_fare", 198.0f)
                .putBoolean("meter_default_regular_min_198_v1", true)
                .apply()
        }

        // Synchronize accurate network time offset from internet for India timezone/clock safety
        viewModelScope.launch {
            com.covaimetertaxi.driver.util.NetworkTimeHelper.syncTimeOffset(context)
        }

        // Auto-recover stale active trips or restore live active trips on boot
        viewModelScope.launch {
            try {
                val savedState = TaxiMeterService.loadTripState(context)
                if (savedState != null && (savedState.status == TripStatus.RUNNING || savedState.status == TripStatus.PAUSED)) {
                    val nowMs = System.currentTimeMillis()
                    val diffHours = (nowMs - savedState.startTime) / (1000 * 60 * 60)
                    if (diffHours >= 18) {
                        Log.i("TaxiMeterViewModel", "Auto-finalizing stale active trip from $diffHours hours ago to prevent data loss")

                        // Apply minimum fare bounding on total fare (in regular meter, minimum bill amount is 198)
                        var finalFare = savedState.currentFare
                        val minFareBound = if (!savedState.isPackageMeter) {
                            maxOf(198.0, savedState.minimumFare)
                        } else {
                            0.0
                        }
                        if (!savedState.isPackageMeter) {
                            if (finalFare < minFareBound) {
                                finalFare = minFareBound
                            }
                        }

                        // Generate Trip ID: last 4 of vehicle number + random 4-digit number
                        val rawVNo = savedState.vehicleNumber
                        val cleanVNo = rawVNo.replace("\\s".toRegex(), "").filter { it.isLetterOrDigit() }
                        val last4 = if (cleanVNo.length >= 4) cleanVNo.takeLast(4) else if (cleanVNo.isNotEmpty()) cleanVNo else "9999"
                        val rNum = (1000..9999).random()
                        val generatedTripId = "${last4.uppercase()}$rNum"

                        val ccComm = when {
                            finalFare >= 200.0 && finalFare <= 300.0 -> 50.0
                            finalFare > 300.0 && finalFare <= 750.0 -> 75.0
                            finalFare > 750.0 -> finalFare * 0.10
                            else -> 0.0
                        }

                        val formatter = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("dd MMM yyyy, hh:mm a")
                        val currentDateStr = formatter.format(java.util.Date(savedState.startTime))

                        // Resolve address placeholders or get from geocoder
                        val sLat = savedState.startLatitude
                        val sLng = savedState.startLongitude
                        val eLat = savedState.endLatitude
                        val eLng = savedState.endLongitude

                        val finalStart = withContext(Dispatchers.IO) {
                            if (sLat != null && sLng != null) {
                                GeocoderHelper.getAddressFromLatLng(context, sLat, sLng)
                            } else null
                        } ?: savedState.startLocation.ifEmpty { "Unknown" }

                        val finalEnd = withContext(Dispatchers.IO) {
                            if (eLat != null && eLng != null) {
                                GeocoderHelper.getAddressFromLatLng(context, eLat, eLng)
                            } else null
                        } ?: savedState.endLocation.ifEmpty { "Unknown" }

                        val finalTrip = Trip(
                            driverName = savedState.driverName.ifEmpty { "N/A" },
                            vehicleNumber = savedState.vehicleNumber.ifEmpty { "N/A" },
                            vehicleCategory = savedState.vehicleCategory,
                            vehicleModel = savedState.vehicleModel.ifEmpty { "N/A" },
                            driverMobile = _driverMobile.value,
                            startTime = savedState.startTime,
                            endTime = nowMs,
                            distance = savedState.distanceKm,
                            durationSeconds = savedState.durationSeconds,
                            totalFare = finalFare,
                            baseFare = if (savedState.isPackageMeter) savedState.packageBaseFare else savedState.baseFare,
                            perKmFare = if (savedState.isPackageMeter) savedState.extraKmRate else savedState.perKmFare,
                            waitingChargePerMin = if (savedState.isPackageMeter) savedState.packageWaitingChargePerMin else savedState.waitingChargePerMin,
                            minimumFare = if (savedState.isPackageMeter) 0.0 else minFareBound,
                            nightChargePercent = if (!savedState.isPackageMeter && savedState.isNightMode) savedState.nightChargePercent else 0.0,
                            dateStr = currentDateStr,
                            startLocation = finalStart,
                            endLocation = finalEnd,
                            startLatitude = sLat,
                            startLongitude = sLng,
                            endLatitude = eLat,
                            endLongitude = eLng,
                            ccCommission = ccComm,
                            tripIdCode = generatedTripId,
                            customerMobile = "",
                            isPackageMeter = savedState.isPackageMeter,
                            packageName = savedState.packageName,
                            packageBaseFare = savedState.packageBaseFare,
                            includedKm = savedState.includedKm,
                            includedMinutes = savedState.includedMinutes,
                            extraKmRate = if (savedState.packagePerKmRate > 0.0) savedState.packagePerKmRate else (if (savedState.extraKmRate > 0.0) savedState.extraKmRate else 20.0),
                            extraTimeRate = if (savedState.extraTimeRate > 0.0) savedState.extraTimeRate else 2.0833,
                            packageWaitingChargePerMin = savedState.packageWaitingChargePerMin,
                            waitingSeconds = savedState.waitingSeconds,
                            routePathPoints = savedState.routePathPoints
                        )

                        withContext(Dispatchers.IO) {
                            repository.insertTrip(finalTrip)
                        }

                        TaxiMeterService.clearSavedTripState(context)
                    } else {
                        Log.i("TaxiMeterViewModel", "Active trip found on startup: restoring trip from $diffHours hours ago, distance=${savedState.distanceKm} km")
                        TaxiMeterService.restoreTripState(context)
                        TaxiMeterService.ensureServiceRunning(context)
                        _currentScreen.value = "LIVE"
                    }
                }
            } catch (e: Exception) {
                Log.e("TaxiMeterViewModel", "Error recovering stale active trip: ${e.message}", e)
            } finally {
                // Silent background sync catchup for any offline trips on startup
                com.covaimetertaxi.driver.network.GoogleSheetsSyncManager.syncAllUnsyncedTrips(context, repository)
            }
        }

        // Evaluate navigation screen on boot in case service was restarted in background
        viewModelScope.launch {
            serviceState.collect { live ->
                if (live.status == TripStatus.RUNNING || live.status == TripStatus.PAUSED) {
                    if (_currentScreen.value != "SUMMARY") {
                        _currentScreen.value = "LIVE"
                    }
                }
            }
        }
    }

    fun checkAndRestoreActiveTrip(activityContext: Context) {
        val saved = TaxiMeterService.loadTripState(activityContext)
        if (saved != null && (saved.status == TripStatus.RUNNING || saved.status == TripStatus.PAUSED)) {
            val nowMs = System.currentTimeMillis()
            val diffHours = (nowMs - saved.startTime) / (1000 * 60 * 60)
            if (diffHours < 18) {
                TaxiMeterService.restoreTripState(activityContext)
                TaxiMeterService.ensureServiceRunning(activityContext)
                if (_currentScreen.value != "SUMMARY") {
                    _currentScreen.value = "LIVE"
                }
            }
        } else if (serviceState.value.status == TripStatus.RUNNING || serviceState.value.status == TripStatus.PAUSED) {
            if (_currentScreen.value != "SUMMARY") {
                _currentScreen.value = "LIVE"
            }
        }
    }

    fun setNavigation(screen: String) {
        _currentScreen.value = screen
    }

    fun viewInvoice(trip: Trip) {
        _selectedTripForInvoice.value = trip
        _currentScreen.value = "SUMMARY"
    }

    // Settings actions
    fun updateBaseAndPerKmFare(base: Double, perKm: Double) {
        _baseFare.value = base
        _perKmFare.value = perKm
        prefs.edit().apply {
            putFloat("base_fare", base.toFloat())
            putFloat("per_km_fare", perKm.toFloat())
            apply()
        }
    }

    fun saveRates(
        base: Double,
        perKm: Double,
        waiting: Double,
        min: Double,
        nightPercent: Double
    ) {
        val effectiveMin = maxOf(198.0, min)
        _baseFare.value = base
        _perKmFare.value = perKm
        _waitingChargePerMin.value = waiting
        _minimumFare.value = effectiveMin
        _nightChargePercent.value = nightPercent

        prefs.edit().apply {
            putFloat("base_fare", base.toFloat())
            putFloat("per_km_fare", perKm.toFloat())
            putFloat("waiting_charge", waiting.toFloat())
            putFloat("minimum_fare", effectiveMin.toFloat())
            putFloat("night_charge", nightPercent.toFloat())
            apply()
        }
    }

    // Driver Details Saving
    fun acceptTermsAndConditions() {
        _hasAcceptedTerms.value = true
        prefs.edit().putBoolean("has_accepted_terms", true).apply()
    }

    fun saveDriverDetails(
        name: String,
        vNumber: String,
        category: String,
        model: String = "",
        isNight: Boolean,
        mobile: String = ""
    ) {
        val uppercaseName = name.uppercase(Locale.ROOT).trim()
        val uppercaseVNumber = vNumber.uppercase(Locale.ROOT).trim()
        val trimmedMobile = mobile.trim()
        val cleanCat = category.replace("(?i)\\s*taxi\\b".toRegex(), "").trim().ifBlank { "Mini" }

        _driverName.value = uppercaseName
        _vehicleNumber.value = uppercaseVNumber
        _vehicleCategory.value = cleanCat
        _vehicleModel.value = model
        _isNightModeActive.value = isNight
        _driverMobile.value = trimmedMobile

        prefs.edit().apply {
            putString("driver_name", uppercaseName)
            putString("vehicle_number", uppercaseVNumber)
            putString("vehicle_category", cleanCat)
            putString("vehicle_model", model)
            putBoolean("is_night_mode_active", isNight)
            putString("driver_mobile", trimmedMobile)
            apply()
        }

        // Trigger Google Sheets sync if details changed, preventing duplicate spamming
        if (uppercaseName.isNotBlank() && uppercaseVNumber.isNotBlank()) {
            val currentSignature = "$uppercaseName|$uppercaseVNumber|$category|$model|$trimmedMobile"
            val lastSyncedSignature = prefs.getString("last_synced_driver_signature", "") ?: ""
            if (currentSignature != lastSyncedSignature) {
                prefs.edit().putString("last_synced_driver_signature", currentSignature).apply()
                com.covaimetertaxi.driver.network.GoogleSheetsSyncManager.syncDriverLogin(
                    context = appContext,
                    driverName = uppercaseName,
                    vehicleNumber = uppercaseVNumber,
                    vehicleCategory = category,
                    vehicleModel = model,
                    driverMobile = trimmedMobile
                )
            }
        } else {
            prefs.edit().remove("last_synced_driver_signature").apply()
        }
    }

    /**
     * Applies driver details fetched from Firebase backend upon successful ID & PIN authentication.
     * Persists login state locally so the driver remains logged in without requiring daily login.
     */
    fun applyFirebaseDriverLogin(
        driverId: String,
        driverName: String,
        mobileNumber: String,
        vehicleNumber: String,
        vehicleCategory: String,
        vehicleModel: String = "",
        photoPath: String = ""
    ) {
        val uppercaseName = driverName.uppercase(Locale.ROOT).trim()
        val uppercaseVNumber = vehicleNumber.uppercase(Locale.ROOT).trim()
        val trimmedMobile = mobileNumber.trim()
        val trimmedDriverId = driverId.trim()
        val cleanCat = vehicleCategory.replace("(?i)\\s*taxi\\b".toRegex(), "").trim().ifBlank { "Mini" }

        _driverId.value = trimmedDriverId
        _driverName.value = uppercaseName
        _vehicleNumber.value = uppercaseVNumber
        _vehicleCategory.value = cleanCat
        _vehicleModel.value = vehicleModel
        _driverMobile.value = trimmedMobile
        _isLoggedIn.value = true
        _hasAcceptedTerms.value = true

        val resolvedPhoto = if (photoPath.isNotBlank()) {
            photoPath
        } else {
            com.covaimetertaxi.driver.network.FirebaseManager.generateInitialsAvatar(appContext, uppercaseName, trimmedDriverId)
        }
        _driverSelfiePath.value = resolvedPhoto

        prefs.edit().apply {
            putBoolean("is_driver_logged_in", true)
            putBoolean("has_accepted_terms", true)
            putString("unique_driver_id", trimmedDriverId)
            putString("driver_name", uppercaseName)
            putString("vehicle_number", uppercaseVNumber)
            putString("vehicle_category", cleanCat)
            putString("vehicle_model", vehicleModel)
            putString("driver_mobile", trimmedMobile)
            putString("driver_selfie_path", resolvedPhoto)
            putLong("last_daily_driver_doc_check_timestamp", System.currentTimeMillis())
            apply()
        }

        // Trigger Google Sheets sync if details changed
        com.covaimetertaxi.driver.network.GoogleSheetsSyncManager.syncDriverLogin(
            context = appContext,
            driverName = uppercaseName,
            vehicleNumber = uppercaseVNumber,
            vehicleCategory = cleanCat,
            vehicleModel = vehicleModel,
            driverMobile = trimmedMobile,
            driverId = trimmedDriverId
        )
    }

    /**
     * Checks if the driver's Document ID still exists in the backend.
     * STRICT RULE: Runs strictly once per 24 hours (daily once only) to minimize reads and writes.
     * If the Document ID has been deleted in the backend, the driver is automatically logged out.
     */
    fun checkDailyDriverDocumentStatus(context: Context) {
        val currentDriverId = _driverId.value.trim()
        val loggedIn = _isLoggedIn.value

        // Only check if driver is logged in and has a valid ID
        if (!loggedIn || currentDriverId.isBlank()) return

        // Safety: Do NOT disrupt an active or paused trip with logout checks
        if (com.covaimetertaxi.driver.service.TaxiMeterService.hasActiveTrip(context) ||
            serviceState.value.status == TripStatus.RUNNING ||
            serviceState.value.status == TripStatus.PAUSED) {
            return
        }

        val prefs = context.getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong("last_daily_driver_doc_check_timestamp", 0L)
        val now = System.currentTimeMillis()
        val twentyFourHoursMs = 24 * 60 * 60 * 1000L

        // Daily once check only: if checked less than 24 hours ago, DO NOT make any network call
        if (now - lastCheckTime < twentyFourHoursMs && lastCheckTime > 0L) {
            return
        }

        viewModelScope.launch {
            Log.d("TaxiMeterViewModel", "Executing daily backend document check for driver '$currentDriverId'...")
            val status = com.covaimetertaxi.driver.network.FirebaseManager.checkDriverDocumentStatus(context, currentDriverId)

            when (status) {
                com.covaimetertaxi.driver.network.FirebaseManager.DriverDocStatus.DELETED -> {
                    Log.w("TaxiMeterViewModel", "Driver Document ID '$currentDriverId' was deleted in backend! Logging out driver.")
                    logoutDriver(context)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "Driver account was removed or deleted in backend. Logged out.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                com.covaimetertaxi.driver.network.FirebaseManager.DriverDocStatus.ACTIVE -> {
                    Log.i("TaxiMeterViewModel", "Daily driver Document ID check verified active. Next check in 24 hours.")
                    prefs.edit().putLong("last_daily_driver_doc_check_timestamp", now).apply()
                }
                com.covaimetertaxi.driver.network.FirebaseManager.DriverDocStatus.NETWORK_UNAVAILABLE -> {
                    Log.d("TaxiMeterViewModel", "Daily driver doc check: network offline/unavailable. Driver remains logged in.")
                }
            }
        }
    }

    /**
     * Gracefully logs out the active driver, clearing device sessions on both Google Sheets & Firebase
     * so another device can log in immediately.
     */
    fun logoutDriver(context: Context) {
        val currentName = _driverName.value
        val currentVNum = _vehicleNumber.value
        val currentCat = _vehicleCategory.value
        val currentModel = _vehicleModel.value
        val currentMobile = _driverMobile.value
        val currentDriverId = _driverId.value

        // 1. Sync logout to Google Sheets (clears Active Device ID and sets status to Logged Out)
        com.covaimetertaxi.driver.network.GoogleSheetsSyncManager.syncDriverLogout(
            context = context,
            driverName = currentName,
            vehicleNumber = currentVNum,
            vehicleCategory = currentCat,
            vehicleModel = currentModel,
            driverMobile = currentMobile,
            driverId = currentDriverId
        )

        // 2. Sync logout to Firebase (clears active_device_id and sets is_logged_in to false)
        com.covaimetertaxi.driver.network.FirebaseManager.syncDriverLogout(
            context = context,
            driverId = currentDriverId
        )

        saveDriverDetails(
            name = "",
            vNumber = "",
            category = "Mini",
            model = "",
            isNight = false,
            mobile = ""
        )

        _isLoggedIn.value = false
        _driverId.value = ""
        _driverSelfiePath.value = ""

        val prefs = context.getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_driver_logged_in", false)
            .putString("unique_driver_id", "")
            .putString("driver_mobile", "")
            .putString("driver_selfie_path", "")
            .remove("last_synced_driver_signature")
            .remove("last_daily_driver_doc_check_timestamp")
            .apply()
    }

    // Taxi Operation Initiators
    fun startTaxiTrip(
        context: Context,
        customerMobile: String,
        customBaseFare: Double? = null,
        customKmsFare: Double? = null,
        isPackage: Boolean = false,
        pkgName: String = "",
        pkgBaseFare: Double = 0.0,
        pkgIncludedKm: Double = 0.0,
        pkgIncludedMinutes: Int = 0,
        pkgExtraKmRate: Double = 0.0,
        pkgExtraTimeRate: Double = 0.0,
        pkgWaitingCharge: Double = 0.0,
        pkgPerHourRate: Double = 0.0,
        pkgPerKmRate: Double = 0.0
    ) {
        _customerMobileNum.value = customerMobile

        if (isPackage) {
            _defaultMeterMode.value = "REGULAR"
            prefs.edit().putString("default_meter_mode", "REGULAR").apply()
        }

        val finalBaseFare = customBaseFare ?: _baseFare.value
        val finalPerKmFare = customKmsFare ?: _perKmFare.value

        val isUnlocked = _isAdvancedUnlocked.value
        val finalWaitingCharge = if (isPackage) 0.0 else (_waitingChargePerMin.value.takeIf { it > 0.0 } ?: 1.0)
        val finalMinFare = if (isPackage) 0.0 else maxOf(198.0, if (isUnlocked && _minimumFare.value > 0.0) _minimumFare.value else 198.0)
        val finalNightPercent = if (isUnlocked) _nightChargePercent.value else 0.0
        val finalNightMode = if (isUnlocked) _isNightModeActive.value else false

        TaxiMeterService.startTrip(
            context = context,
            driverName = _driverName.value,
            vehicleNumber = _vehicleNumber.value,
            category = _vehicleCategory.value,
            model = _vehicleModel.value,
            baseFare = finalBaseFare,
            perKmFare = finalPerKmFare,
            waitingCharge = finalWaitingCharge,
            minFare = finalMinFare,
            nightPercent = finalNightPercent,
            isNightMode = finalNightMode,

            isPackageMeter = isPackage,
            packageName = pkgName,
            packageBaseFare = pkgBaseFare,
            includedKm = pkgIncludedKm,
            includedMinutes = pkgIncludedMinutes,
            extraKmRate = pkgExtraKmRate,
            extraTimeRate = pkgExtraTimeRate,
            packageWaitingChargePerMin = pkgWaitingCharge,
            packagePerHourRate = pkgPerHourRate,
            packagePerKmRate = pkgPerKmRate
        )
        _currentScreen.value = "LIVE"
        com.covaimetertaxi.driver.util.TtsAnnouncer.speakTamil(context, "தயவுசெய்து சீட் பெல்ட் அணிந்து பாதுகாப்பாக பயணிக்கவும்.")
    }

    fun pauseTaxiTrip(context: Context) {
        TaxiMeterService.pauseTrip(context)
    }

    fun resumeTaxiTrip(context: Context) {
        TaxiMeterService.resumeTrip(context)
    }

    fun endTaxiTrip(context: Context) {
        val currentState = serviceState.value
        if (currentState.status == TripStatus.RUNNING || currentState.status == TripStatus.PAUSED) {
            if (currentState.isPackageMeter) {
                setDefaultMeterMode("REGULAR")
            }
            TaxiMeterService.endTrip(context)
            com.covaimetertaxi.driver.util.TtsAnnouncer.speakTamil(context)

            // Save the trip to the database
            viewModelScope.launch {
                val sLat = currentState.startLatitude
                val sLng = currentState.startLongitude
                val eLat = currentState.endLatitude
                val eLng = currentState.endLongitude

                // Fetch real geo names on thread pool before committing to DB
                val finalStart = withContext(Dispatchers.IO) {
                    if (sLat != null && sLng != null) {
                        GeocoderHelper.getAddressFromLatLng(context, sLat, sLng)
                    } else null
                } ?: currentState.startLocation

                val finalEnd = withContext(Dispatchers.IO) {
                    if (eLat != null && eLng != null) {
                        GeocoderHelper.getAddressFromLatLng(context, eLat, eLng)
                    } else null
                } ?: currentState.endLocation

                val formatter = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("dd MMM yyyy, hh:mm a")
                val currentDateStr = formatter.format(Date(currentState.startTime))

                // Apply minimum fare bounding on total fare after trip is ended (in regular meter, minimum bill amount is 198)
                var finalFare = currentState.currentFare
                val minFareBound = if (!currentState.isPackageMeter) {
                    maxOf(198.0, currentState.minimumFare)
                } else {
                    0.0
                }
                if (!currentState.isPackageMeter) {
                    if (finalFare < minFareBound) {
                        finalFare = minFareBound
                    }
                }

                // Generate Trip ID: last 4 of vehicle number + random 4-digit number
                val rawVNo = currentState.vehicleNumber
                val cleanVNo = rawVNo.replace("\\s".toRegex(), "").filter { it.isLetterOrDigit() }
                val last4 = if (cleanVNo.length >= 4) cleanVNo.takeLast(4) else if (cleanVNo.isNotEmpty()) cleanVNo else "9999"
                val rNum = (1000..9999).random()
                val generatedTripId = "${last4.uppercase()}$rNum"

                // Calculate CC commission:
                // If total fare is between ₹200 to ₹300 -> CC = ₹50
                // If total fare is above ₹300 to ₹750 -> CC = ₹75
                // If total fare is above ₹750 -> CC = 10% of total fare
                val ccComm = when {
                    finalFare >= 200.0 && finalFare <= 300.0 -> 50.0
                    finalFare > 300.0 && finalFare <= 750.0 -> 75.0
                    finalFare > 750.0 -> finalFare * 0.10
                    else -> 0.0
                }

                val finalTrip = Trip(
                    driverName = currentState.driverName.ifEmpty { "N/A" },
                    vehicleNumber = currentState.vehicleNumber.ifEmpty { "N/A" },
                    vehicleCategory = currentState.vehicleCategory,
                    vehicleModel = currentState.vehicleModel.ifEmpty { "N/A" },
                    driverMobile = _driverMobile.value,
                    startTime = currentState.startTime,
                    endTime = com.covaimetertaxi.driver.util.NetworkTimeHelper.getCurrentTimeMillis(),
                    distance = currentState.distanceKm,
                    durationSeconds = currentState.durationSeconds,
                    totalFare = finalFare,
                    baseFare = if (currentState.isPackageMeter) currentState.packageBaseFare else currentState.baseFare,
                    perKmFare = if (currentState.isPackageMeter) currentState.extraKmRate else currentState.perKmFare,
                    waitingChargePerMin = if (currentState.isPackageMeter) currentState.packageWaitingChargePerMin else currentState.waitingChargePerMin,
                    minimumFare = if (currentState.isPackageMeter) 0.0 else minFareBound,
                    nightChargePercent = if (!currentState.isPackageMeter && currentState.isNightMode) currentState.nightChargePercent else 0.0,
                    dateStr = currentDateStr,
                    startLocation = finalStart,
                    endLocation = finalEnd,
                    startLatitude = currentState.startLatitude,
                    startLongitude = currentState.startLongitude,
                    endLatitude = currentState.endLatitude,
                    endLongitude = currentState.endLongitude,
                    ccCommission = ccComm,
                    tripIdCode = generatedTripId,
                    customerMobile = _customerMobileNum.value,
                    isPackageMeter = currentState.isPackageMeter,
                    packageName = currentState.packageName,
                    packageBaseFare = currentState.packageBaseFare,
                    includedKm = currentState.includedKm,
                    includedMinutes = currentState.includedMinutes,
                    extraKmRate = if (currentState.packagePerKmRate > 0.0) currentState.packagePerKmRate else (if (currentState.extraKmRate > 0.0) currentState.extraKmRate else 20.0),
                    extraTimeRate = if (currentState.extraTimeRate > 0.0) currentState.extraTimeRate else 2.0833,
                    packageWaitingChargePerMin = currentState.packageWaitingChargePerMin,
                    waitingSeconds = currentState.waitingSeconds,
                    routePathPoints = currentState.routePathPoints
                )

                // Insert into db
                val insertedId = repository.insertTrip(finalTrip)
                val tripToSync = finalTrip.copy(id = insertedId)

                // Set the current trip as selected for view invoice screen
                _selectedTripForInvoice.value = tripToSync
                TaxiMeterService.resetMeter()
                _currentScreen.value = "SUMMARY"

                // Automatically reset rates to 0.0 for next trip on stop/conclude
                _baseFare.value = 80.0
                _perKmFare.value = 28.0
                _packageBaseFare.value = 350.0
                _packagePerHourRate.value = 350.0
                _packagePerKmRate.value = 20.0
                _packageExtraKmRate.value = 20.0
                prefs.edit().apply {
                    putFloat("base_fare", 80.0f)
                    putFloat("per_km_fare", 28.0f)
                    putFloat("pkg_base_fare", 350.0f)
                    putFloat("pkg_per_hour_rate", 350.0f)
                    putFloat("pkg_per_km_rate", 20.0f)
                    putFloat("pkg_extra_km_rate", 20.0f)
                    apply()
                }

                // Sync the ended trip to Google Sheets in a non-blocking background task
                viewModelScope.launch {
                    com.covaimetertaxi.driver.network.GoogleSheetsSyncManager.syncTrip(context, tripToSync, repository)
                }
            }
        }
    }

    fun clearTripHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}

class TaxiMeterViewModelFactory(private val repository: TripRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaxiMeterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaxiMeterViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
