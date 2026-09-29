package com.covaimetertaxi.driver

import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SwapHoriz

import com.covaimetertaxi.driver.util.tr
import com.covaimetertaxi.driver.util.LanguageManager
import com.covaimetertaxi.driver.util.RideOtpManager


import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.covaimetertaxi.driver.network.FirebaseManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import com.covaimetertaxi.driver.data.AppDatabase
import com.covaimetertaxi.driver.data.Trip
import com.covaimetertaxi.driver.data.TripRepository
import com.covaimetertaxi.driver.service.LiveTripState
import com.covaimetertaxi.driver.service.TaxiMeterService
import com.covaimetertaxi.driver.service.TripStatus
import com.covaimetertaxi.driver.ui.TaxiMeterViewModel
import com.covaimetertaxi.driver.ui.TaxiMeterViewModelFactory
import com.covaimetertaxi.driver.ui.theme.*
import com.covaimetertaxi.driver.util.PdfReceiptGenerator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import java.io.FileOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: TaxiMeterViewModel

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        com.covaimetertaxi.driver.util.LanguageManager.init(applicationContext)
        com.covaimetertaxi.driver.util.NetworkTimeHelper.init(applicationContext)

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TripRepository(database.tripDao())

        viewModel = ViewModelProvider(
            this,
            TaxiMeterViewModelFactory(repository, applicationContext)
        )[TaxiMeterViewModel::class.java]

        viewModel.checkAndRestoreActiveTrip(this)

        // Pre-initialize the TTS voice engine immediately for instant voice announcements
        com.covaimetertaxi.driver.util.TtsAnnouncer.preInitialize(this)

        setContent {
            MyApplicationTheme {
                MainLayout(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.checkAndRestoreActiveTrip(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::viewModel.isInitialized) {
            viewModel.checkAndRestoreActiveTrip(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.covaimetertaxi.driver.util.TtsAnnouncer.shutdown()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun MainLayout(viewModel: TaxiMeterViewModel) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("CovaiMeterTaxiPrefs", Context.MODE_PRIVATE) }
    val currentScreen by viewModel.currentScreen.collectAsState()
    val serviceState by viewModel.serviceState.collectAsState()
    val sDriverName by viewModel.driverName.collectAsState()
    val sVehicleNum by viewModel.vehicleNumber.collectAsState()
    val sDriverSelfiePath by viewModel.driverSelfiePath.collectAsState()
    val hasAcceptedTerms by viewModel.hasAcceptedTerms.collectAsState()
    val sDriverId by viewModel.driverId.collectAsState()

    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val isProfileSetupNeeded = !isLoggedIn || sDriverName.trim().isEmpty() || sVehicleNum.trim().isEmpty()
    val isSelfieMissing = !isLoggedIn && sDriverSelfiePath.trim().isEmpty()

    // State to check if required permissions and location settings are enabled
    val initialFine = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val initialNotification = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    val initialBackground = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    val initialGps = remember(context) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    val initialBattery = remember(context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    var hasFineState by remember { mutableStateOf(initialFine) }
    var hasNotificationState by remember { mutableStateOf(initialNotification) }
    var hasBackgroundState by remember { mutableStateOf(initialBackground) }
    var gpsEnabledState by remember { mutableStateOf(initialGps) }
    var batteryOptimizationIgnoredState by remember { mutableStateOf(initialBattery) }

    fun checkPermissionsAndGps() {
        hasFineState = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasNotificationState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasBackgroundState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        gpsEnabledState = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        batteryOptimizationIgnoredState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    // Refresh status when returning to app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissionsAndGps()
                viewModel.checkAndRestoreActiveTrip(context)
                viewModel.checkDailyDriverDocumentStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Daily once driver document check (rate-limited to max once every 24h)
    LaunchedEffect(isLoggedIn, sDriverId) {
        if (isLoggedIn && sDriverId.isNotBlank()) {
            viewModel.checkDailyDriverDocumentStatus(context)
        }
    }

    var autoPromptStage by remember { mutableStateOf(0) }

    // Request launchers for different permissions
    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        checkPermissionsAndGps()
        val isFineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (isFineGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationState) {
                autoPromptStage = 2
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundState) {
                autoPromptStage = 3
            } else {
                autoPromptStage = 4
            }
        } else {
            val activity = (context as? Activity)
                ?: ((context as? ContextWrapper)?.baseContext as? Activity)
            val shouldShow = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            if (!shouldShow) {
                openAppSettingsSafely(context)
            }
            autoPromptStage = 4
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        checkPermissionsAndGps()
        if (isGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundState && hasFineState) {
                autoPromptStage = 3
            } else {
                autoPromptStage = 4
            }
        } else {
            val activity = (context as? Activity)
                ?: ((context as? ContextWrapper)?.baseContext as? Activity)
            val shouldShow = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
            if (!shouldShow) {
                val notifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                if (!safeStartActivity(context, notifIntent)) {
                    openAppSettingsSafely(context)
                }
            }
            autoPromptStage = 4
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkPermissionsAndGps()
        autoPromptStage = 4
    }

    LaunchedEffect(Unit) {
        checkPermissionsAndGps()
        if (hasFineState && hasNotificationState && hasBackgroundState && gpsEnabledState) {
            autoPromptStage = 4
        } else {
            // Slight delay for a smoother visual startup, then begin automatic prompts
            kotlinx.coroutines.delay(800)
            if (!hasFineState) {
                autoPromptStage = 1
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationState) {
                autoPromptStage = 2
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundState) {
                autoPromptStage = 3
            } else {
                autoPromptStage = 4
            }
        }
    }

    LaunchedEffect(autoPromptStage) {
        when (autoPromptStage) {
            1 -> {
                val perms = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                foregroundPermissionLauncher.launch(perms)
            }
            2 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationState) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    autoPromptStage = 3
                }
            }
            3 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundState && hasFineState) {
                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    autoPromptStage = 4
                }
            }
        }
    }

    val isAllGrantedAndGpsOn = hasFineState && hasNotificationState && hasBackgroundState && gpsEnabledState && batteryOptimizationIgnoredState
    val hasActiveTrip = (serviceState.status == TripStatus.RUNNING || serviceState.status == TripStatus.PAUSED) ||
            com.covaimetertaxi.driver.service.TaxiMeterService.hasActiveTrip(context)

    if (hasActiveTrip) {
        // When a trip is actively running or paused, NEVER block the driver with permission gates, selfie, or login!
        // The driver must be able to view their fare, live tracking, and END THE TRIP to bill the customer.
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {}
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TaxiBackground)
                    .padding(innerPadding)
            ) {
                LiveDisplayScreen(viewModel = viewModel, context = context)
            }
        }
    } else if (!isAllGrantedAndGpsOn) {
        MandatoryPermissionsGateScreen(
            context = context,
            hasFine = hasFineState,
            hasNotification = hasNotificationState,
            hasBackground = hasBackgroundState,
            gpsEnabled = gpsEnabledState,
            batteryOptimizationIgnored = batteryOptimizationIgnoredState,
            onRequestForeground = {
                val perms = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                try {
                    foregroundPermissionLauncher.launch(perms)
                } catch (e: Exception) {
                    openAppSettingsSafely(context)
                }
            },
            onRequestNotification = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (e: Exception) {
                        val notifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        if (!safeStartActivity(context, notifIntent)) {
                            openAppSettingsSafely(context)
                        }
                    }
                } else {
                    checkPermissionsAndGps()
                }
            },
            onRequestBackground = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (!hasFineState) {
                        Toast.makeText(context, "Please grant Location permission first", Toast.LENGTH_SHORT).show()
                        val perms = arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        try {
                            foregroundPermissionLauncher.launch(perms)
                        } catch (e: Exception) {
                            openAppSettingsSafely(context)
                        }
                    } else {
                        Toast.makeText(context, "Tap 'Permissions' → 'Location' → 'Allow all the time'", Toast.LENGTH_LONG).show()
                        openAppSettingsSafely(context)
                    }
                } else {
                    checkPermissionsAndGps()
                }
            },
            onOpenGpsSettings = {
                val gpsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                if (!safeStartActivity(context, gpsIntent)) {
                    val fallback = Intent(Settings.ACTION_SETTINGS)
                    safeStartActivity(context, fallback)
                }
            },
            onRequestBatteryOptimization = {
                var opened = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        opened = safeStartActivity(context, intent)
                    } catch (e: Exception) {
                        opened = false
                    }
                }
                if (!opened) {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    opened = safeStartActivity(context, fallback)
                }
                if (!opened) {
                    openAppSettingsSafely(context)
                }
                checkPermissionsAndGps()
            },
            onOpenAppSettings = {
                openAppSettingsSafely(context)
            },
            onReCheck = {
                checkPermissionsAndGps()
            }
        )
    } else if (!hasAcceptedTerms) {
        MandatoryTermsScreen(viewModel = viewModel)
    } else if (isProfileSetupNeeded) {
        DriverLoginScreen(viewModel = viewModel)
    } else if (isSelfieMissing) {
        MandatorySelfieSetupScreen(viewModel = viewModel)
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                // Only show bottom navigation when NOT actively tracking a live trip in real-time
                if (serviceState.status != TripStatus.RUNNING && serviceState.status != TripStatus.PAUSED) {
                    NavigationBar(
                        containerColor = TaxiWhite,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = Slate100,
                                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                            )
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == "FORM" || currentScreen == "LIVE",
                            onClick = { viewModel.setNavigation("FORM") },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("HOME".tr, fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TaxiBlack,
                                selectedTextColor = TaxiBlack,
                                unselectedTextColor = Slate400,
                                unselectedIconColor = Slate400,
                                indicatorColor = TaxiYellowLight
                            ),
                            modifier = Modifier.testTag("nav_btn_meter")
                        )
                        NavigationBarItem(
                            selected = currentScreen == "HISTORY",
                            onClick = { viewModel.setNavigation("HISTORY") },
                            icon = { Icon(Icons.Default.History, contentDescription = "History") },
                            label = { Text("HISTORY".tr, fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TaxiBlack,
                                selectedTextColor = TaxiBlack,
                                unselectedTextColor = Slate400,
                                unselectedIconColor = Slate400,
                                indicatorColor = TaxiYellowLight
                            ),
                            modifier = Modifier.testTag("nav_btn_history")
                        )
                        NavigationBarItem(
                            selected = currentScreen == "SETTINGS",
                            onClick = { viewModel.setNavigation("SETTINGS") },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                            label = { Text("SETTINGS".tr, fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TaxiBlack,
                                selectedTextColor = TaxiBlack,
                                unselectedTextColor = Slate400,
                                unselectedIconColor = Slate400,
                                indicatorColor = TaxiYellowLight
                            ),
                            modifier = Modifier.testTag("nav_btn_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            val contentModifier = if (currentScreen == "SETTINGS") {
                Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            } else {
                Modifier
                    .fillMaxSize()
                    .background(TaxiBackground)
                    .padding(innerPadding)
            }
            Box(
                modifier = contentModifier
            ) {
                when (currentScreen) {
                    "FORM" -> DriverDetailsScreen(viewModel = viewModel, onStart = { mobile, isPackage ->
                        if (isPackage) {
                            viewModel.startTaxiTrip(
                                context = context,
                                customerMobile = mobile,
                                isPackage = true,
                                pkgName = viewModel.packageName.value,
                                pkgBaseFare = viewModel.packageBaseFare.value,
                                pkgIncludedKm = viewModel.packageIncludedKm.value,
                                pkgIncludedMinutes = viewModel.packageIncludedMinutes.value,
                                pkgExtraKmRate = viewModel.packageExtraKmRate.value,
                                pkgExtraTimeRate = viewModel.packageExtraTimeRate.value,
                                pkgWaitingCharge = viewModel.packageWaitingChargePerMin.value,
                                pkgPerHourRate = viewModel.packagePerHourRate.value,
                                pkgPerKmRate = viewModel.packagePerKmRate.value
                            )
                        } else {
                            viewModel.startTaxiTrip(context, mobile)
                        }
                    })
                    "LIVE" -> LiveDisplayScreen(viewModel = viewModel, context = context)
                    "SUMMARY" -> TripSummaryScreen(viewModel = viewModel, context = context)
                    "HISTORY" -> HistoryScreen(viewModel = viewModel)
                    "SETTINGS" -> SettingsScreen(viewModel = viewModel)
                    else -> DriverDetailsScreen(viewModel = viewModel, onStart = { mobile, isPackage ->
                        if (isPackage) {
                            viewModel.startTaxiTrip(
                                context = context,
                                customerMobile = mobile,
                                isPackage = true,
                                pkgName = viewModel.packageName.value,
                                pkgBaseFare = viewModel.packageBaseFare.value,
                                pkgIncludedKm = viewModel.packageIncludedKm.value,
                                pkgIncludedMinutes = viewModel.packageIncludedMinutes.value,
                                pkgExtraKmRate = viewModel.packageExtraKmRate.value,
                                pkgExtraTimeRate = viewModel.packageExtraTimeRate.value,
                                pkgWaitingCharge = viewModel.packageWaitingChargePerMin.value,
                                pkgPerHourRate = viewModel.packagePerHourRate.value,
                                pkgPerKmRate = viewModel.packagePerKmRate.value
                            )
                        } else {
                            viewModel.startTaxiTrip(context, mobile)
                        }
                    })
                }
            }
        }
    }
}

// 1. DRIVER DETAILS ENTRY SCREEN
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDetailsScreen(viewModel: TaxiMeterViewModel, onStart: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val sDriverName by viewModel.driverName.collectAsState()
    val sVehicleNum by viewModel.vehicleNumber.collectAsState()
    val sVehicleCat by viewModel.vehicleCategory.collectAsState()
    val sDriverSelfiePath by viewModel.driverSelfiePath.collectAsState()
    val sBaseFare by viewModel.baseFare.collectAsState()
    val sPerKmFare by viewModel.perKmFare.collectAsState()

    var showFareDialog by remember { mutableStateOf(false) }

    val initials = remember(sDriverName) {
        sDriverName.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifEmpty { "TX" }
    }

    val selfieBitmap = remember(sDriverSelfiePath) {
        if (sDriverSelfiePath.isNotEmpty()) {
            try {
                BitmapFactory.decodeFile(sDriverSelfiePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    var customerMobile by remember { mutableStateOf("") }
    var enteredOtp by remember { mutableStateOf("") }
    val defaultMode by viewModel.defaultMeterMode.collectAsState()
    var selectedMode by remember { mutableStateOf("REGULAR") } // "REGULAR" by default, or "PACKAGE" if switched

    LaunchedEffect(defaultMode) {
        selectedMode = defaultMode
    }

    val pkgName by viewModel.packageName.collectAsState()
    val pkgBaseFare by viewModel.packageBaseFare.collectAsState()
    val pkgIncludedKm by viewModel.packageIncludedKm.collectAsState()
    val pkgIncludedMinutes by viewModel.packageIncludedMinutes.collectAsState()
    val pkgExtraKmRate by viewModel.packageExtraKmRate.collectAsState()
    val pkgExtraTimeRate by viewModel.packageExtraTimeRate.collectAsState()
    val pkgWaitingCharge by viewModel.packageWaitingChargePerMin.collectAsState()
    val pkgPerHourRate by viewModel.packagePerHourRate.collectAsState()
    val pkgPerKmRate by viewModel.packagePerKmRate.collectAsState()

    var currentIstHour by remember { mutableStateOf(RideOtpManager.getCurrentIstHour()) }
    LaunchedEffect(Unit) {
        while (true) {
            val hour = RideOtpManager.getCurrentIstHour()
            if (hour != currentIstHour) {
                currentIstHour = hour
            }
            kotlinx.coroutines.delay(3000L)
        }
    }

    val calculatedOtp = remember(customerMobile, currentIstHour) {
        val sanitized = customerMobile.filter { it.isDigit() }
        if (sanitized.length == 10) {
            RideOtpManager.calculateOtp(sanitized, currentIstHour)
        } else {
            ""
        }
    }

    val isOtpCorrect = customerMobile.length == 10 && calculatedOtp.isNotEmpty() && enteredOtp == calculatedOtp

    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isOtpCorrect) {
        if (isOtpCorrect) {
            keyboardController?.hide()
        }
    }

    if (showFareDialog) {
        SetFareRatesDialog(
            currentBaseFare = sBaseFare,
            currentPerKmFare = sPerKmFare,
            onDismiss = { showFareDialog = false },
            onSave = { newBase, newPerKm ->
                viewModel.updateBaseAndPerKmFare(newBase, newPerKm)
                showFareDialog = false
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val isCompact = maxWidth < 360.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isCompact) 12.dp else 20.dp)
                .padding(top = 12.dp, bottom = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App header banner (Geometric theme style)
            GeometricHeader()

            Spacer(modifier = Modifier.height(8.dp))


            // Unified Meter Configuration Card with integrated Mode Switcher
            Card(
                colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, if (selectedMode == "PACKAGE") TaxiYellowGlow else Color(0xFFFFD4D4)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header inside the configuration card
                    Text(
                        text = "METER CONFIGURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TaxiBlack,
                        letterSpacing = 1.sp
                    )

                    // Seamless Switcher Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate100, shape = RoundedCornerShape(14.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(
                                    color = if (selectedMode == "REGULAR") TaxiYellow else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedMode = "REGULAR"
                                    viewModel.setDefaultMeterMode("REGULAR")
                                }
                                .testTag("home_switch_regular_meter"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "REGULAR METER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedMode == "REGULAR") TaxiBlack else Slate600
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(
                                    color = if (selectedMode == "PACKAGE") TaxiYellowGlow else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .then(
                                    if (selectedMode == "PACKAGE") Modifier.border(1.dp, TaxiBlack, RoundedCornerShape(10.dp)) else Modifier
                                )
                                .clickable {
                                    selectedMode = "PACKAGE"
                                    viewModel.setDefaultMeterMode("PACKAGE")
                                }
                                .testTag("home_switch_package_meter"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PACKAGE METER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedMode == "PACKAGE") TaxiBlack else Slate600
                            )
                        }
                    }

                    // Direct Rate Input Fields based on the selected mode
                    if (selectedMode == "PACKAGE") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            var inputPerHourRate by remember { mutableStateOf(if (pkgPerHourRate <= 0.0) "" else if (pkgPerHourRate % 1.0 == 0.0) pkgPerHourRate.toInt().toString() else pkgPerHourRate.toString()) }
                            var inputPerKmRate by remember { mutableStateOf(if (pkgPerKmRate <= 0.0) "" else if (pkgPerKmRate % 1.0 == 0.0) pkgPerKmRate.toInt().toString() else pkgPerKmRate.toString()) }

                            LaunchedEffect(pkgPerHourRate) {
                                val currentLocalDouble = inputPerHourRate.toDoubleOrNull() ?: 0.0
                                if (currentLocalDouble != pkgPerHourRate) {
                                    inputPerHourRate = if (pkgPerHourRate <= 0.0) "" else if (pkgPerHourRate % 1.0 == 0.0) pkgPerHourRate.toInt().toString() else pkgPerHourRate.toString()
                                }
                            }

                            LaunchedEffect(pkgPerKmRate) {
                                val currentLocalDouble = inputPerKmRate.toDoubleOrNull() ?: 0.0
                                if (currentLocalDouble != pkgPerKmRate) {
                                    inputPerKmRate = if (pkgPerKmRate <= 0.0) "" else if (pkgPerKmRate % 1.0 == 0.0) pkgPerKmRate.toInt().toString() else pkgPerKmRate.toString()
                                }
                            }

                            OutlinedTextField(
                                value = inputPerHourRate,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() || it == '.' }) {
                                        inputPerHourRate = newValue
                                        val parsedRate = newValue.toDoubleOrNull() ?: 0.0
                                        val currentKmRate = inputPerKmRate.toDoubleOrNull() ?: 0.0
                                        viewModel.updatePackageSettings(
                                            name = "Hourly Package",
                                            baseFare = parsedRate,
                                            includedKm = 10.0,
                                            includedMinutes = 60,
                                            extraKmRate = currentKmRate,
                                            extraTimeRate = 2.0833,
                                            waitingCharge = pkgWaitingCharge,
                                            perHourRate = parsedRate,
                                            perKmRate = currentKmRate
                                        )
                                    }
                                },
                                placeholder = { Text("0", color = Slate400, fontSize = 13.sp) },
                                label = { Text("Per Hour Fare (₹)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pkg_per_hour_rate_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TaxiYellow,
                                    unfocusedBorderColor = Slate200,
                                    focusedLabelColor = TaxiBlack,
                                    unfocusedLabelColor = Slate400
                                )
                            )

                            OutlinedTextField(
                                value = inputPerKmRate,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() || it == '.' }) {
                                        inputPerKmRate = newValue
                                        val parsedKmRate = newValue.toDoubleOrNull() ?: 0.0
                                        val currentHourRate = inputPerHourRate.toDoubleOrNull() ?: 0.0
                                        viewModel.updatePackageSettings(
                                            name = "Hourly Package",
                                            baseFare = currentHourRate,
                                            includedKm = 10.0,
                                            includedMinutes = 60,
                                            extraKmRate = parsedKmRate,
                                            extraTimeRate = 2.0833,
                                            waitingCharge = pkgWaitingCharge,
                                            perHourRate = currentHourRate,
                                            perKmRate = parsedKmRate
                                        )
                                    }
                                },
                                readOnly = false,
                                placeholder = { Text("0", color = Slate400, fontSize = 13.sp) },
                                label = { Text("KMS Fare (₹/KM)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pkg_per_km_rate_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TaxiYellow,
                                    unfocusedBorderColor = Slate200,
                                    focusedLabelColor = TaxiBlack,
                                    unfocusedLabelColor = Slate400
                                )
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            var inputBaseFare by remember { mutableStateOf(if (sBaseFare <= 0.0) "" else if (sBaseFare % 1.0 == 0.0) sBaseFare.toInt().toString() else sBaseFare.toString()) }
                            var inputPerKmFare by remember { mutableStateOf(if (sPerKmFare <= 0.0) "" else if (sPerKmFare % 1.0 == 0.0) sPerKmFare.toInt().toString() else sPerKmFare.toString()) }

                            LaunchedEffect(sBaseFare) {
                                val current = inputBaseFare.toDoubleOrNull() ?: 0.0
                                if (current != sBaseFare) {
                                    inputBaseFare = if (sBaseFare <= 0.0) "" else if (sBaseFare % 1.0 == 0.0) sBaseFare.toInt().toString() else sBaseFare.toString()
                                }
                            }

                            LaunchedEffect(sPerKmFare) {
                                val current = inputPerKmFare.toDoubleOrNull() ?: 0.0
                                if (current != sPerKmFare) {
                                    inputPerKmFare = if (sPerKmFare <= 0.0) "" else if (sPerKmFare % 1.0 == 0.0) sPerKmFare.toInt().toString() else sPerKmFare.toString()
                                }
                            }

                            OutlinedTextField(
                                value = inputBaseFare,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() || it == '.' }) {
                                        inputBaseFare = newValue
                                        val parsedBase = newValue.toDoubleOrNull() ?: 0.0
                                        val currentKm = inputPerKmFare.toDoubleOrNull() ?: 0.0
                                        viewModel.updateBaseAndPerKmFare(parsedBase, currentKm)
                                    }
                                },
                                placeholder = { Text("0", color = Slate400, fontSize = 13.sp) },
                                label = { Text("Base Fare (₹)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("regular_base_fare_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TaxiYellow,
                                    unfocusedBorderColor = Slate200,
                                    focusedLabelColor = TaxiBlack,
                                    unfocusedLabelColor = Slate400
                                )
                            )

                            OutlinedTextField(
                                value = inputPerKmFare,
                                onValueChange = { newValue ->
                                    if (newValue.all { it.isDigit() || it == '.' }) {
                                        inputPerKmFare = newValue
                                        val parsedKm = newValue.toDoubleOrNull() ?: 0.0
                                        val currentBase = inputBaseFare.toDoubleOrNull() ?: 0.0
                                        viewModel.updateBaseAndPerKmFare(currentBase, parsedKm)
                                    }
                                },
                                placeholder = { Text("0", color = Slate400, fontSize = 13.sp) },
                                label = { Text("KMS Fare (₹/KM)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("regular_per_km_fare_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = TaxiYellow,
                                    unfocusedBorderColor = Slate200,
                                    focusedLabelColor = TaxiBlack,
                                    unfocusedLabelColor = Slate400
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CUSTOMER DETAILS CARD (with OTP hidden as requested)
            Card(
                colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Slate100),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFFF0F0), shape = RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = TaxiBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "TRIP VERIFICATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TaxiBlack,
                            letterSpacing = 1.sp
                        )
                    }

                    HorizontalDivider(color = Slate500.copy(alpha = 0.1f))

                    // Mobile number field
                    OutlinedTextField(
                        value = customerMobile,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }
                            val sanitized = if (digits.length > 10) {
                                digits.takeLast(10)
                            } else {
                                digits
                            }
                            customerMobile = sanitized
                            if (sanitized.length == 10) {
                                keyboardController?.hide()
                            }
                        },
                        label = { Text("Passenger No.") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone icon", tint = Slate500) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("10-digit mobile") },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TaxiYellow,
                            focusedLabelColor = TaxiBlack,
                            unfocusedBorderColor = Slate200,
                            unfocusedLabelColor = Slate400,
                            focusedContainerColor = Color(0xFFFFFDFD)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("customer_mobile_input")
                    )

                    if (customerMobile.length == 10) {
                        // OTP Input Field for local ride validation
                        OutlinedTextField(
                            value = enteredOtp,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }
                                if (digits.length <= 4) {
                                    enteredOtp = digits
                                    if (digits.length == 4) {
                                        keyboardController?.hide()
                                    }
                                }
                            },
                            label = { Text("Ride OTP") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "OTP Icon", tint = Slate500) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("Enter OTP") },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (isOtpCorrect) Color(0xFF2E7D32) else TaxiYellow,
                                focusedLabelColor = if (isOtpCorrect) Color(0xFF2E7D32) else TaxiBlack,
                                unfocusedBorderColor = if (isOtpCorrect) Color(0xFF2E7D32) else Slate200,
                                unfocusedLabelColor = Slate400,
                                focusedContainerColor = Color(0xFFFFFDFD)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("entered_otp_input"),
                            trailingIcon = {
                                if (isOtpCorrect) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Correct OTP", tint = Color(0xFF4CAF50))
                                }
                            }
                        )

                        if (isOtpCorrect) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Valid",
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "OTP verified! Ready to initiate trip.",
                                    color = Color(0xFF2E7D32),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (enteredOtp.length == 4) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFF0F0), shape = RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Invalid",
                                    tint = TaxiBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Incorrect OTP. Ask the passenger for correct OTP.",
                                    color = TaxiBlack,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "Enter the 4-digit trip OTP to begin",
                                color = Slate500,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    } else if (customerMobile.isNotEmpty()) {
                        Text(
                            text = "Please enter 10 digits (${customerMobile.length}/10)",
                            color = Slate400,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

        }

        // Sticky Start Trip Action Button with background gradient container at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            TaxiBackground.copy(alpha = 0.95f),
                            TaxiBackground
                        )
                    )
                )
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp)
        ) {
            // Modern Swipe to Start Action
            SwipeToActionButton(
                text = "SWIPE TO START TRIP",
                onSwipeComplete = { onStart(customerMobile, selectedMode == "PACKAGE") },
                isEnabled = isOtpCorrect,
                containerColor = TaxiYellow,
                contentColor = TaxiBlack,
                thumbColor = TaxiWhite,
                thumbIconColor = TaxiBlack,
                modifier = Modifier
                    .shadow(if (isOtpCorrect) 6.dp else 0.dp, shape = RoundedCornerShape(32.dp))
                    .testTag("start_trip_button")
            )
        }
    }
}

// 2. LIVE DISPLAY METER PANEL
@RequiresApi(Build.VERSION_CODES.M)
@Composable
fun LiveDisplayScreen(viewModel: TaxiMeterViewModel, context: Context) {
    val state by viewModel.serviceState.collectAsState()
    var showEndConfirmationDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            // Service stays up in foreground, nothing cleanup
        }
    }

    val sDriverSelfiePath by viewModel.driverSelfiePath.collectAsState()

    val initials = remember(state.driverName) {
        state.driverName.split(" ")
            .filter { it.isNotEmpty() }
            .take(2)
            .map { it.first().uppercase() }
            .joinToString("")
            .ifEmpty { "TX" }
    }

    val selfieBitmap = remember(sDriverSelfiePath) {
        if (sDriverSelfiePath.isNotEmpty()) {
            try {
                BitmapFactory.decodeFile(sDriverSelfiePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val isCompact = maxWidth < 360.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(if (isCompact) 12.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App header (Geometric style)
            GeometricHeader()

            Spacer(modifier = Modifier.height(14.dp))

            // Driver/Vehicle Info Card (Geometric Balance style)
            val hasLiveProfile = state.driverName.trim().isNotEmpty() || state.vehicleNumber.trim().isNotEmpty()
            if (hasLiveProfile) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Slate100),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar photo or RK block
                        if (selfieBitmap != null) {
                            Image(
                                bitmap = selfieBitmap,
                                contentDescription = "Driver Selfie",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, Slate200, RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Slate100, shape = RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TaxiBlack
                                )
                            }
                        }

                        // Driver details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.driverName.trim(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = "${state.vehicleCategory} • ${state.vehicleNumber.trim()}",
                                fontSize = 12.sp,
                                color = Slate500,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Sedan / category pill
                        Box(
                            modifier = Modifier
                                .background(GreenPillBg, shape = RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.vehicleCategory.uppercase(Locale.ROOT),
                                fontSize = 10.sp,
                                color = GreenPillText,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            val activeDispatchTripState = com.covaimetertaxi.driver.service.TaxiDispatchServiceState.activeTrip.collectAsState().value
            if (activeDispatchTripState != null) {
                val dTrip = activeDispatchTripState
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Slate100),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ONGOING DISPATCH TRIP",
                                fontWeight = FontWeight.Black,
                                color = TaxiBlack,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .background(TaxiYellowLight, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ID: ${dTrip.trip_id.orEmpty()}",
                                    color = TaxiBlack,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Customer: ${dTrip.customer_name.orEmpty()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate900
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mobile: ${dTrip.customer_mobile.orEmpty()}",
                                fontSize = 13.sp,
                                color = Slate500,
                                fontWeight = FontWeight.Medium
                            )

                            // Call Customer Button
                            if (!dTrip.customer_mobile.isNullOrBlank()) {
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${dTrip.customer_mobile}"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60), contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Dial", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call".tr, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Slate100)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(8.dp)
                                    .background(Color(0xFF2ECC71), RoundedCornerShape(50))
                            )
                            Column {
                                Text("PICKUP LOCATION".tr, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                Text(
                                    text = dTrip.pickup_location.orEmpty(),
                                    fontSize = 13.sp,
                                    color = Slate700,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(8.dp)
                                    .background(Color(0xFFE74C3C), RoundedCornerShape(50))
                            )
                            Column {
                                Text("DROP LOCATION".tr, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate400)
                                Text(
                                    text = dTrip.drop_location.orEmpty(),
                                    fontSize = 13.sp,
                                    color = Slate700,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!dTrip.notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Instructions: ${dTrip.notes}",
                                fontSize = 11.sp,
                                style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = Slate500
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Slate100)
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+914223596446"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = Slate900),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call Office",
                                tint = TaxiBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CALL OFFICE: +91 422-3596446",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // The Digital Meter Panel in Geometric Balance
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "VEHICLE SPEED",
                    fontSize = 12.sp,
                    color = Slate400,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${state.speedKmH.toInt()}",
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black,
                        color = TaxiBlack,
                        letterSpacing = (-2.0).sp,
                        modifier = Modifier.testTag("live_speed_display")
                    )
                    Text(
                        text = " KM/H",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TaxiBlack,
                        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3-Column Info Matrix with thin vertical dividers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(width = 1.dp, color = Slate100, shape = RoundedCornerShape(20.dp))
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Distance column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "DISTANCE",
                            fontSize = 10.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${String.format("%.2f", state.distanceKm)} KM",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate900
                        )
                    }

                    // Vertical Divider 1
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Slate100)
                    )

                    // Total Time column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Text(
                            text = "DURATION",
                            fontSize = 10.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TaxiMeterService.formatDuration(state.durationSeconds),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate900
                        )
                    }

                    // Vertical Divider 2
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(Slate100)
                    )

                    // Standby column
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "STANDBY",
                            fontSize = 10.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = TaxiMeterService.formatDuration(state.waitingSeconds),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate700
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Controls Area: START/PAUSE/RESUME/FINISH
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Swipe to End Trip Action
                SwipeToActionButton(
                    text = "SWIPE TO END TRIP",
                    onSwipeComplete = { showEndConfirmationDialog = true },
                    containerColor = TaxiYellow,
                    contentColor = TaxiBlack,
                    thumbColor = TaxiWhite,
                    thumbIconColor = TaxiBlack,
                    resetKey = showEndConfirmationDialog,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("end_trip_button")
                )
            }

            if (showEndConfirmationDialog) {
                AlertDialog(
                    onDismissRequest = { showEndConfirmationDialog = false },
                    title = { Text("End Trip?".tr) },
                    text = { Text("Are you sure you want to end and close the trip? This action cannot be undone.".tr) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.endTaxiTrip(context)
                                showEndConfirmationDialog = false
                            }
                        ) {
                            Text("END TRIP".tr, color = TaxiBlack, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEndConfirmationDialog = false }) {
                            Text("CANCEL".tr, color = Slate500, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }
}

// 3. TRIP RECEIPT SUMMARY & UPI PAYMENT VIEW
@Composable
fun TripSummaryScreen(viewModel: TaxiMeterViewModel, context: Context) {
    val trip by viewModel.selectedTripForInvoice.collectAsState()

    val safeTrip = trip ?: return // Safely fallback if active null

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = GreenPillText,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "TRIP BILLINGS COMPLETE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = GreenPillText,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Total Charged Amount",
                fontSize = 11.sp,
                color = Slate500,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "₹${String.format("%.2f", safeTrip.totalFare)}",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = TaxiBlack,
                letterSpacing = (-1.5).sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Printable Ticket visual card
            Card(
                colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Slate100),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // Trip Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRIP INVOICE",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = TaxiBlack,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = safeTrip.dateStr.split(",").firstOrNull() ?: "",
                            fontSize = 11.sp,
                            color = Slate500,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Slate100)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Metadata list
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReceiptRow(label = "Trip Id".tr, value = if (safeTrip.tripIdCode.isNotEmpty()) safeTrip.tripIdCode else safeTrip.id.toString())
                        ReceiptRow(label = "Driver Name".tr, value = safeTrip.driverName)
                        ReceiptRow(label = "Vehicle Registration".tr, value = safeTrip.vehicleNumber)
                        ReceiptRow(label = "Category".tr, value = safeTrip.vehicleCategory.replace("(?i)\\s*taxi\\b".toRegex(), "").trim().ifEmpty { "Mini" })
                        val timeFormatter = com.covaimetertaxi.driver.util.NetworkTimeHelper.getAsiaKolkataFormatter("hh:mm a")
                        ReceiptRow(label = "Start Timing".tr, value = timeFormatter.format(Date(safeTrip.startTime)))
                        ReceiptRow(label = "End Timing".tr, value = timeFormatter.format(Date(safeTrip.endTime)))
                        val startMapsUrl = getMapsUrlForTripLocation(safeTrip.startLatitude, safeTrip.startLongitude, safeTrip.startLocation)
                        val endMapsUrl = getMapsUrlForTripLocation(safeTrip.endLatitude, safeTrip.endLongitude, safeTrip.endLocation)

                        ReceiptRow(
                            label = "Start Location",
                            value = safeTrip.startLocation,
                            onClick = if (startMapsUrl.isNotEmpty()) { { openMapForTripLocation(context, safeTrip.startLatitude, safeTrip.startLongitude, safeTrip.startLocation) } } else null
                        )
                        ReceiptRow(
                            label = "End Location",
                            value = safeTrip.endLocation,
                            onClick = if (endMapsUrl.isNotEmpty()) { { openMapForTripLocation(context, safeTrip.endLatitude, safeTrip.endLongitude, safeTrip.endLocation) } } else null
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = Slate100)
                        Spacer(modifier = Modifier.height(4.dp))

                        ReceiptRow(label = "Total Distance", value = "${String.format("%.2f", safeTrip.distance)} KM")
                        ReceiptRow(label = "Trip Time Details", value = TaxiMeterService.formatDuration(safeTrip.durationSeconds))

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = Slate100)
                        Spacer(modifier = Modifier.height(4.dp))

                        if (safeTrip.isPackageMeter) {
                            val totalMinutes = safeTrip.durationSeconds.toDouble() / 60.0
                            val packageHours = maxOf(1, kotlin.math.floor(totalMinutes / 60.0).toInt())
                            val extraKm = maxOf(0.0, safeTrip.distance - safeTrip.includedKm)
                            val effectiveKmRate = if (safeTrip.extraKmRate > 0.0) safeTrip.extraKmRate else 20.0
                            val extraKmFare = extraKm * effectiveKmRate
                            val extraMinutes = maxOf(0.0, totalMinutes - (packageHours * 60.0))
                            val effectiveTimeRate = if (safeTrip.extraTimeRate > 0.0) safeTrip.extraTimeRate else 2.0833
                            val extraTimeFare = extraMinutes * effectiveTimeRate
                            val packageFare = safeTrip.packageBaseFare
                            val waitingMinutes = safeTrip.waitingSeconds.toDouble() / 60.0
                            val waitingFare = if (safeTrip.packageWaitingChargePerMin > 0.0) waitingMinutes * safeTrip.packageWaitingChargePerMin else 0.0

                            ReceiptRow(label = "Package Active".tr, value = safeTrip.packageName)
                            ReceiptRow(label = "Package Base Fare".tr, value = "₹${String.format("%.2f", packageFare)}")
                            if (extraKm > 0.0) {
                                ReceiptRow(label = "Extra Distance (${String.format("%.2f", extraKm)} KM @ ₹${String.format("%.2f", effectiveKmRate)}/KM)", value = "₹${String.format("%.2f", extraKmFare)}")
                            }
                            if (extraMinutes > 0.0) {
                                ReceiptRow(label = "Extra Duration (${String.format("%.1f", extraMinutes)} Min @ ₹${String.format("%.2f", effectiveTimeRate)}/Min)", value = "₹${String.format("%.2f", extraTimeFare)}")
                            }
                            if (waitingFare > 0.0) {
                                ReceiptRow(label = "Standby Waiting Time".tr, value = "₹${String.format("%.2f", waitingFare)} (₹${safeTrip.packageWaitingChargePerMin}/Min)")
                            }
                        } else {
                            ReceiptRow(label = "Base Fare Minimum".tr, value = "₹${String.format("%.2f", safeTrip.baseFare)}")
                            ReceiptRow(label = "Distance Fare (₹${safeTrip.perKmFare}/km)", value = "₹${String.format("%.2f", safeTrip.distance * safeTrip.perKmFare)}")
                            ReceiptRow(label = "Standby Waiting Time".tr, value = "₹${String.format("%.2f", (safeTrip.waitingSeconds.toDouble() / 60.0) * safeTrip.waitingChargePerMin)}")

                            val rawFare = safeTrip.baseFare + (safeTrip.distance * safeTrip.perKmFare) + ((safeTrip.waitingSeconds.toDouble() / 60.0) * safeTrip.waitingChargePerMin)
                            if (safeTrip.totalFare >= safeTrip.minimumFare && rawFare < safeTrip.minimumFare) {
                                ReceiptRow(
                                    label = "Minimum Fare Adjustment",
                                    value = "₹${String.format("%.2f", safeTrip.minimumFare - rawFare)}"
                                )
                            }

                            if (safeTrip.nightChargePercent > 0) {
                                ReceiptRow(
                                    label = "Night Surcharge Premium",
                                    value = "₹${String.format("%.2f", safeTrip.totalFare - maxOf(safeTrip.minimumFare, rawFare))} [${safeTrip.nightChargePercent.roundToInt()}%]"
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = TaxiYellow, thickness = 2.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Grand cash due
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL NET DUE".tr, fontWeight = FontWeight.Black, fontSize = 14.sp, color = Slate900, letterSpacing = 0.5.sp)
                        Text("₹ ${String.format("%.2f", safeTrip.totalFare)}", fontWeight = FontWeight.Black, fontSize = 24.sp, color = TaxiBlack)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Slate100),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "RECEIPTS & PRINTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TaxiBlack,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Export/Share PDF Receipt
                        OutlinedButton(
                            onClick = {
                                val pdfFile = PdfReceiptGenerator.generateTripReceipt(context, safeTrip)
                                if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0L) {
                                    sharePdfReceipt(context, pdfFile)
                                } else {
                                    Toast.makeText(context, "Could not generate invoice. Please try again.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            border = BorderStroke(1.dp, TaxiYellow),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = TaxiBlack)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share Invoice".tr, color = TaxiBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Print action
                        OutlinedButton(
                            onClick = {
                                val pdfFile = PdfReceiptGenerator.generateTripReceipt(context, safeTrip)
                                if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0L) {
                                    requestFilePrint(context, pdfFile)
                                } else {
                                    Toast.makeText(context, "Could not prepare receipt for printing.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            border = BorderStroke(1.dp, Slate500),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1.0f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = Slate700)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Print Receipt".tr, color = Slate700, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sharing channels
            Card(
                colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Slate100),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "TEXT CHANNEL SHARING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TaxiBlack,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                    )

                    val standardShares = buildTripShareText(safeTrip, includeCustomerMobile = false)
                    val vendorShares = buildTripShareText(safeTrip, includeCustomerMobile = true)

                    // Direct sharing channels (Customer number omitted for privacy)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val shareChannels = listOf(
                            Triple("WhatsApp", Color(0xFF25D366), Icons.Default.Share),
                            Triple("Telegram", Color(0xFF0088CC), Icons.Default.Send),
                            Triple("Copy", TaxiYellow, Icons.Default.ContentCopy)
                        )

                        shareChannels.forEach { (label, color, icon) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color.copy(alpha = 0.08f))
                                    .border(BorderStroke(1.dp, color.copy(alpha = 0.25f)), RoundedCornerShape(16.dp))
                                    .clickable {
                                        when (label) {
                                            "WhatsApp" -> launchPlatformShare(context, "com.whatsapp", standardShares)
                                            "Telegram" -> launchTelegramOrChooser(context, standardShares)
                                            "Copy" -> {
                                                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clip.setPrimaryClip(android.content.ClipData.newPlainText("Trip Receipt Details", standardShares))
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = color,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate700,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Separate Vendor Sharing Button (Shares complete bill WITH customer number to any app)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TaxiYellowLight),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.2.dp, TaxiYellow.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val chooserIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, vendorShares)
                                }
                                context.startActivity(Intent.createChooser(chooserIntent, "Share Vendor Bill (With Customer No)"))
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {

                                Column {
                                    Text(
                                        text = "VENDOR BILL",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TaxiBlack,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = if (safeTrip.customerMobile.isNotBlank()) "With Customer No: ${safeTrip.customerMobile} • Share anywhere" else "With Customer Contact • Share anywhere",
                                        fontSize = 10.sp,
                                        color = Slate700,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = TaxiBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// 4. TRIPS LOG DETAILS
@Composable
fun HistoryScreen(viewModel: TaxiMeterViewModel) {
    val history by viewModel.tripHistory.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TRIPS RECORD JOURNAL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TaxiBlack,
                        letterSpacing = 1.sp
                    )
                }
                if (history.isNotEmpty()) {
                    var showClearDialog by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        border = BorderStroke(1.dp, TaxiYellow),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TaxiBlack),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear History",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CLEAR ALL".tr, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (showClearDialog) {
                        AlertDialog(
                            onDismissRequest = { showClearDialog = false },
                            title = { Text("Clear All History?".tr) },
                            text = { Text("Are you sure you want to permanently delete all trip records? This cannot be undone.".tr) },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.clearTripHistory()
                                        showClearDialog = false
                                    }
                                ) {
                                    Text("CLEAR ALL".tr, color = TaxiBlack, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showClearDialog = false }) {
                                    Text("CANCEL".tr, color = Slate500)
                                }
                            }
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Slate100
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No billing logs currently saved".tr, color = Slate500, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("trips_logs_list")
                ) {
                    items(history) { trip ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.viewInvoice(trip) }
                                .border(1.dp, Slate100, RoundedCornerShape(20.dp)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = trip.vehicleNumber,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 15.sp,
                                            color = Slate900
                                        )
                                        Text(
                                            text = trip.dateStr,
                                            fontSize = 11.sp,
                                            color = Slate400,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = "₹${String.format("%.2f", trip.totalFare)}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TaxiBlack
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Slate100)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DirectionsCar,
                                            contentDescription = null,
                                            tint = Slate400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = trip.vehicleCategory.replace("(?i)\\s*taxi\\b".toRegex(), "").trim().ifEmpty { "Mini" },
                                            fontSize = 12.sp,
                                            color = Slate500,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Map,
                                            contentDescription = null,
                                            tint = Slate400,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${String.format("%.2f", trip.distance)} KM",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. RATES CARD SETTINGS PORTAL
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TaxiMeterViewModel) {
    val context = LocalContext.current
    val sDriverId by viewModel.driverId.collectAsState()
    val sDriverName by viewModel.driverName.collectAsState()
    val sVehicleNum by viewModel.vehicleNumber.collectAsState()
    val sVehicleCat by viewModel.vehicleCategory.collectAsState()
    val isNightActive by viewModel.isNightModeActive.collectAsState()
    val sDriverSelfiePath by viewModel.driverSelfiePath.collectAsState()

    var driverNameVal by remember { mutableStateOf(sDriverName) }
    var vehicleNumVal by remember { mutableStateOf(sVehicleNum) }
    var vehicleCatVal by remember { mutableStateOf(sVehicleCat) }
    var defaultNightVal by remember { mutableStateOf(isNightActive) }

    LaunchedEffect(sDriverName, sVehicleNum, sVehicleCat, isNightActive, sDriverSelfiePath) {
        driverNameVal = sDriverName
        vehicleNumVal = sVehicleNum
        vehicleCatVal = sVehicleCat
        defaultNightVal = isNightActive
    }

    // Auto-save driver details whenever driver details change
    LaunchedEffect(driverNameVal, vehicleNumVal, vehicleCatVal, defaultNightVal) {
        if (driverNameVal.trim().isNotEmpty() && vehicleNumVal.trim().isNotEmpty()) {
            viewModel.saveDriverDetails(
                name = driverNameVal,
                vNumber = vehicleNumVal,
                category = vehicleCatVal,
                isNight = defaultNightVal
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Top Bar with Taxi Theme - fills top status bar area seamlessly
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(TaxiYellow, TaxiYellowDark.copy(alpha = 0.95f))
                        )
                    )
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Covai Meter Taxi",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TaxiBlack
                    )

                    Surface(
                        onClick = { viewModel.logoutDriver(context) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = "Logout".tr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE11D48),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // PROFILE SECTION
            val selfieBitmap = remember(sDriverSelfiePath) {
                if (sDriverSelfiePath.isNotEmpty()) {
                    try { BitmapFactory.decodeFile(sDriverSelfiePath)?.asImageBitmap() } catch (e: Exception) { null }
                } else null
            }

            val initials = remember(driverNameVal) {
                driverNameVal.split(" ")
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .map { it.first().uppercase() }
                    .joinToString("")
                    .ifEmpty { "TX" }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-14).dp)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile photo fully visible without cropping
                        if (selfieBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(20.dp))
                                    .border(1.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(20.dp))
                                    .clip(RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = selfieBitmap,
                                    contentDescription = "Driver Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(TaxiYellow.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                                    .border(1.5.dp, TaxiYellow.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Slate800
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = driverNameVal.ifEmpty { "Driver Name" },
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = vehicleCatVal,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate500
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pill Chips Row (No icons)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (sDriverId.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFF1F5F9),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "ID: $sDriverId",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate800
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFF0FDF4),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Verified Account".tr,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }
            }

            // Section 1: VEHICLE DETAILS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 13.dp)
                        .background(TaxiYellow, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VEHICLE DETAILS".tr,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate600,
                    letterSpacing = 0.8.sp
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Registration Tile
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Registration No.".tr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate500,
                                letterSpacing = 0.2.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = vehicleNumVal.ifEmpty { "N/A" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Slate900,
                                maxLines = 1
                            )
                        }
                    }

                    // Category Tile
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Category".tr,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate500,
                                letterSpacing = 0.2.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = vehicleCatVal,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Slate900,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Section 2: APP PREFERENCES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 13.dp)
                        .background(TaxiYellow, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "App Preferences".tr,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate600,
                    letterSpacing = 0.8.sp
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    val currentLanguage = com.covaimetertaxi.driver.util.LanguageManager.currentLanguage.collectAsState().value
                    var showLangDialog by remember { mutableStateOf(false) }

                    if (showLangDialog) {
                        AlertDialog(
                            onDismissRequest = { showLangDialog = false },
                            title = { Text("Select App Language".tr) },
                            text = {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                com.covaimetertaxi.driver.util.LanguageManager.setLanguage(context, "EN")
                                                showLangDialog = false
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("English (Default)".tr, fontSize = 16.sp)
                                    }
                                    HorizontalDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                com.covaimetertaxi.driver.util.LanguageManager.setLanguage(context, "TA")
                                                showLangDialog = false
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Tamil".tr, fontSize = 16.sp)
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showLangDialog = false }) {
                                    Text("CANCEL".tr)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLangDialog = true }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Language Setting".tr,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (currentLanguage == "EN") "English (Default)".tr else "Tamil".tr,
                                fontSize = 13.sp,
                                color = Slate500,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Modern language capsule indicator
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TaxiYellowLight,
                            border = BorderStroke(1.dp, TaxiYellow.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (currentLanguage == "EN") "EN" else "TA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TaxiBlack,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // Section 3: ACTIONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 13.dp)
                        .background(TaxiYellow, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACTIONS".tr,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate600,
                    letterSpacing = 0.8.sp
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+9104224939999")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Call Office".tr,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "+91 04224939999",
                                fontSize = 13.sp,
                                color = Slate500,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Circular Green Call Button Icon matching attached reference
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(2.dp, CircleShape)
                                .background(Color(0xFF22C55E), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call Office".tr,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// SUB-COMPOSABLES HELPER UTILS

@Composable
fun SetFareRatesDialog(
    currentBaseFare: Double,
    currentPerKmFare: Double,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit
) {
    var baseFareText by remember {
        mutableStateOf(if (currentBaseFare <= 0.0) "" else if (currentBaseFare % 1.0 == 0.0) currentBaseFare.toInt().toString() else currentBaseFare.toString())
    }
    var perKmText by remember {
        mutableStateOf(if (currentPerKmFare <= 0.0) "" else if (currentPerKmFare % 1.0 == 0.0) currentPerKmFare.toInt().toString() else currentPerKmFare.toString())
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(TaxiYellowLight, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Fare settings",
                        tint = TaxiBlack,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "SET FARE RATES",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Set Base Fare & KMS Rate for rides",
                        fontSize = 11.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Base Fare field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "BASE FARE (₹)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 0.5.sp
                    )
                    OutlinedTextField(
                        value = baseFareText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == '.' }) {
                                baseFareText = input
                                errorMessage = null
                            }
                        },
                        placeholder = { Text("0".tr, color = Slate400, fontSize = 14.sp) },
                        leadingIcon = {
                            Text(
                                text = "₹",
                                fontWeight = FontWeight.Bold,
                                color = TaxiBlack,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TaxiYellow,
                            focusedLabelColor = TaxiBlack,
                            unfocusedBorderColor = Slate200,
                            unfocusedLabelColor = Slate400
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_base_fare_input")
                    )
                }

                // KMS Fare field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "KMS FARE (₹ / KM)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 0.5.sp
                    )
                    OutlinedTextField(
                        value = perKmText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == '.' }) {
                                perKmText = input
                                errorMessage = null
                            }
                        },
                        placeholder = { Text("0".tr, color = Slate400, fontSize = 14.sp) },
                        leadingIcon = {
                            Text(
                                text = "₹",
                                fontWeight = FontWeight.Bold,
                                color = TaxiBlack,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TaxiYellow,
                            focusedLabelColor = TaxiBlack,
                            unfocusedBorderColor = Slate200,
                            unfocusedLabelColor = Slate400
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_per_km_input")
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = TaxiBlack,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = baseFareText.toDoubleOrNull()
                    val perKm = perKmText.toDoubleOrNull()
                    if (base == null || base < 0.0) {
                        errorMessage = "Please enter a valid Base Fare"
                    } else if (perKm == null || perKm < 0.0) {
                        errorMessage = "Please enter a valid KMS Fare"
                    } else {
                        onSave(base, perKm)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = TaxiBlack),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("dialog_save_fare_btn")
            ) {
                Text("SAVE FARE".tr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate300),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate700)
            ) {
                Text("CANCEL".tr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = TaxiWhite
    )
}

@Composable
fun GeometricHeader(modifier: Modifier = Modifier.padding(vertical = 4.dp)) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(TaxiYellow, shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Covai Meter Taxi",
                color = TaxiBlack,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
    }
}

fun getMapsUrl(loc: String): String {
    if (loc.isBlank() || loc.startsWith("Awaiting") || loc == "Unknown") return ""
    try {
        val parts = loc.split(",")
        if (parts.size == 2) {
            val latText = parts[0].trim()
            val lngText = parts[1].trim()

            var latVal = latText.replace("°", "").replace("N", "").replace("S", "").trim().toDoubleOrNull()
            var lngVal = lngText.replace("°", "").replace("E", "").replace("W", "").replace(" ", "").trim().toDoubleOrNull()
            if (latVal != null && lngVal != null) {
                if (latText.contains("S")) {
                    latVal = -latVal
                }
                if (lngText.contains("W")) {
                    lngVal = -lngVal
                }
                return "https://www.google.com/maps/search/?api=1&query=$latVal,$lngVal"
            }
        }
    } catch (e: Exception) {
        // Fallback to query encoding below
    }
    return "https://www.google.com/maps/search/?api=1&query=${Uri.encode(loc)}"
}

fun openMap(context: Context, locationStr: String) {
    val mapsUrl = getMapsUrl(locationStr)
    if (mapsUrl.isNotEmpty()) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun getMapsUrlForTripLocation(latitude: Double?, longitude: Double?, fallbackAddress: String): String {
    if (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0) {
        return "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
    }
    return getMapsUrl(fallbackAddress)
}

fun openMapForTripLocation(context: Context, latitude: Double?, longitude: Double?, fallbackAddress: String) {
    val mapsUrl = getMapsUrlForTripLocation(latitude, longitude, fallbackAddress)
    if (mapsUrl.isNotEmpty()) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = Slate500, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (onClick != null) TaxiBlack else Slate900,
            textDecoration = if (onClick != null) androidx.compose.ui.text.style.TextDecoration.Underline else null,
            modifier = Modifier.weight(1f, fill = false),
            textAlign = TextAlign.End
        )
    }
}


// TEXT BILLING SHARING LAYOUT FORMULATORS

fun buildTripShareText(trip: Trip, includeCustomerMobile: Boolean = false): String {
    val distanceStr = String.format("%.2f", trip.distance)
    val fareStr = String.format("%.2f", trip.totalFare)

    val customerLine = if (includeCustomerMobile && trip.customerMobile.isNotBlank()) {
        "\nCustomer Contact: ${trip.customerMobile}"
    } else {
        ""
    }

    val istTimeFormatter = SimpleDateFormat("hh:mm a", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    }
    val startTimeFormatted = if (trip.startTime > 0) "${istTimeFormatter.format(Date(trip.startTime))} IST" else ""
    val endTimeFormatted = if (trip.endTime > 0) "${istTimeFormatter.format(Date(trip.endTime))} IST" else ""

    val timingLines = buildString {
        if (startTimeFormatted.isNotBlank()) {
            append("\nStart Time: $startTimeFormatted")
        }
        if (endTimeFormatted.isNotBlank()) {
            append("\nEnd Time: $endTimeFormatted")
        }
    }

    val routeMapLink = if (trip.startLatitude != null && trip.startLongitude != null &&
        trip.endLatitude != null && trip.endLongitude != null &&
        trip.startLatitude != 0.0 && trip.endLatitude != 0.0) {
        "https://www.google.com/maps/dir/?api=1&origin=${trip.startLatitude},${trip.startLongitude}&destination=${trip.endLatitude},${trip.endLongitude}"
    } else if (trip.startLocation.isNotBlank() && trip.endLocation.isNotBlank() &&
        !trip.startLocation.startsWith("Awaiting") && !trip.endLocation.startsWith("Awaiting") &&
        trip.startLocation != "Unknown" && trip.endLocation != "Unknown") {
        "https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(trip.startLocation)}&destination=${Uri.encode(trip.endLocation)}"
    } else {
        ""
    }

    val pickupSection = "Pickup Location: ${trip.startLocation}"
    val dropSection = "Drop Location: ${trip.endLocation}"

    val routeSection = if (routeMapLink.isNotEmpty()) {
        "\nRoute Map: $routeMapLink"
    } else {
        ""
    }

    val cleanCategory = trip.vehicleCategory.replace("(?i)\\s*taxi\\b".toRegex(), "").trim().ifEmpty { "Mini" }

    if (trip.isPackageMeter) {
        val totalMinutes = trip.durationSeconds.toDouble() / 60.0
        val packageHours = maxOf(1, kotlin.math.floor(totalMinutes / 60.0).toInt())
        val extraKm = maxOf(0.0, trip.distance - trip.includedKm)
        val kmRate = if (trip.extraKmRate > 0.0) trip.extraKmRate else 20.0
        val extraKmFare = extraKm * kmRate
        val extraMinutes = maxOf(0.0, totalMinutes - (packageHours * 60.0))
        val timeRate = if (trip.extraTimeRate > 0.0) trip.extraTimeRate else 2.0833
        val extraTimeFare = extraMinutes * timeRate
        val packageFare = trip.packageBaseFare
        val waitingMinutes = trip.waitingSeconds.toDouble() / 60.0
        val waitingFare = if (trip.packageWaitingChargePerMin > 0.0) waitingMinutes * trip.packageWaitingChargePerMin else 0.0

        val waitingSection = if (waitingFare > 0.0) {
            "\nWaiting Charges: INR ${String.format("%.2f", waitingFare)} (₹${trip.packageWaitingChargePerMin}/Min)"
        } else {
            ""
        }

        val extraKmSection = if (extraKm > 0.0) {
            "\nExtra KM Fare: INR ${String.format("%.2f", extraKmFare)} (${String.format("%.2f", extraKm)} KM @ ₹${String.format("%.2f", kmRate)}/KM)"
        } else {
            ""
        }

        val extraTimeSection = if (extraMinutes > 0.0) {
            "\nExtra Time Fare: INR ${String.format("%.2f", extraTimeFare)} (${String.format("%.1f", extraMinutes)} Mins @ ₹${String.format("%.2f", timeRate)}/Min)"
        } else {
            ""
        }

        return """
TAXI PACKAGE RECEIPT

Trip Id: ${if (trip.tripIdCode.isNotEmpty()) trip.tripIdCode else trip.id.toString()}
Date: ${trip.dateStr}$timingLines
Driver: ${trip.driverName} (${trip.vehicleNumber})
Vehicle: $cleanCategory$customerLine

Package Details:
Package: ${trip.packageName}
Included KM: ${trip.includedKm} KM
Included Time: ${trip.includedMinutes} Mins

Fare Summary:
Package Fare: INR ${String.format("%.2f", packageFare)}$extraKmSection$extraTimeSection$waitingSection

Distance: *$distanceStr KM*
Duration: ${TaxiMeterService.formatDuration(trip.durationSeconds)}

Total Payable: *INR $fareStr*

$pickupSection
$dropSection$routeSection

Thank you for your business!
""".trimIndent()
    } else {
        val distanceFare = trip.distance * trip.perKmFare
        val waitMins = trip.waitingSeconds.toDouble() / 60.0
        val waitFare = waitMins * trip.waitingChargePerMin
        val rawFare = trip.baseFare + distanceFare + waitFare
        val minAdjustmentSection = if (trip.totalFare >= trip.minimumFare && rawFare < trip.minimumFare) {
            val adjustment = trip.minimumFare - rawFare
            "\nMinimum Fare Adjustment: INR ${String.format("%.2f", adjustment)}"
        } else {
            ""
        }
        val nightSection = if (trip.nightChargePercent > 0.0) {
            val baseCost = maxOf(trip.minimumFare, rawFare)
            val surcharge = baseCost * (trip.nightChargePercent / 100.0)
            "\nNight Surcharge Premium: INR ${String.format("%.2f", surcharge)} (${trip.nightChargePercent.roundToInt()}%)"
        } else {
            ""
        }

        return """
TAXI RIDE RECEIPT

Trip Id: ${if (trip.tripIdCode.isNotEmpty()) trip.tripIdCode else trip.id.toString()}
Date: ${trip.dateStr}$timingLines
Driver: ${trip.driverName} (${trip.vehicleNumber})
Vehicle: $cleanCategory$customerLine

Fare Summary:
Base Fare: INR ${String.format("%.2f", trip.baseFare)}
Distance Fare: INR ${String.format("%.2f", distanceFare)}
Waiting Fare: INR ${String.format("%.2f", waitFare)}$minAdjustmentSection$nightSection

Distance: *$distanceStr KM*
Duration: ${TaxiMeterService.formatDuration(trip.durationSeconds)}

Total Payable: *INR $fareStr*

$pickupSection
$dropSection$routeSection

Thank you for your business!
""".trimIndent()
    }
}

// ACTION UTILITIES

fun launchPlatformShare(context: Context, targetPackage: String, message: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }

    // Test for package availability
    val pm = context.packageManager
    val resolved = pm.queryIntentActivities(intent, 0)
    var supportsDirect = false
    for (res in resolved) {
        if (res.activityInfo.packageName.startsWith(targetPackage)) {
            intent.setPackage(res.activityInfo.packageName)
            supportsDirect = true
            break
        }
    }

    try {
        if (supportsDirect) {
            context.startActivity(intent)
        } else {
            // General fallback chooser
            val chooser = Intent.createChooser(intent, "Share Trip Details")
            context.startActivity(chooser)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun launchTelegramOrChooser(context: Context, message: String) {
    // Exact spec: When clicked, Open Telegram and pasteGeneratedTripDetails and user sends to group
    val telegramIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        setPackage("org.telegram.messenger")
    }
    try {
        context.startActivity(telegramIntent)
    } catch (e: Exception) {
        // Fallback to standard chooser if Telegram is not direct available
        // Fallback to standard chooser if Telegram is not direct available
        val chooserIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(chooserIntent, "Share customized receipt"))
    }
}

fun launchSmsApp(context: Context, rawMessage: String) {
    val smsUri = Uri.parse("smsto:")
    val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
        putExtra("sms_body", rawMessage)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun sharePdfReceipt(context: Context, file: File) {
    try {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Invoice document could not be prepared", Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val fileUri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            clipData = android.content.ClipData.newRawUri("PDF Invoice", fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(intent, "Share PDF Invoice").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Grant explicit read URI permissions to target applications (WhatsApp, Gmail, PDF viewers, etc.)
        val resInfoList = context.packageManager.queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val targetPackage = resolveInfo.activityInfo.packageName
            context.grantUriPermission(targetPackage, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to share invoice: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
    }
}

fun requestFilePrint(context: Context, file: File) {
    try {
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(context, "Receipt document could not be prepared", Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val fileUri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/pdf")
            clipData = android.content.ClipData.newRawUri("PDF Receipt", fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        val chooserIntent = Intent.createChooser(intent, "Open receipt to print / save").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val resInfoList = context.packageManager.queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val targetPackage = resolveInfo.activityInfo.packageName
            context.grantUriPermission(targetPackage, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooserIntent)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Unable to open receipt: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
    }
}

fun saveAndNormalizeSelfie(context: Context, bitmap: Bitmap, viewModel: TaxiMeterViewModel) {
    val file = File(context.filesDir, "driver_selfie_${System.currentTimeMillis()}.jpg")
    try {
        // Auto-rotate 270 degrees to portrait if captured in landscape
        val finalBitmap = if (bitmap.width > bitmap.height) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(270f)
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        val fos = FileOutputStream(file)
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        fos.close()
        viewModel.saveDriverSelfiePath(file.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun rotateDriverSelfie(context: Context, currentPath: String, viewModel: TaxiMeterViewModel) {
    if (currentPath.isEmpty()) return
    try {
        val file = File(currentPath)
        if (!file.exists()) return

        // Load original bitmap
        val originalBitmap = BitmapFactory.decodeFile(currentPath) ?: return

        // Rotate 90 degrees clockwise
        val matrix = android.graphics.Matrix()
        matrix.postRotate(90f)
        val rotatedBitmap = Bitmap.createBitmap(
            originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
        )

        // Save to a new file to force compose cache bust
        val newFile = File(context.filesDir, "driver_selfie_${System.currentTimeMillis()}.jpg")
        val fos = FileOutputStream(newFile)
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        fos.close()

        // Clean up old file
        try {
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Save new path in viewModel
        viewModel.saveDriverSelfiePath(newFile.absolutePath)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverLoginScreen(viewModel: TaxiMeterViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var driverIdInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentLanguage by LanguageManager.currentLanguage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Decorative background curves matching the reference design edge-to-edge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top curved wave in warm golden yellow stretching all the way to top of display
            val topWave = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.34f)
                cubicTo(
                    w * 0.72f, h * 0.42f,
                    w * 0.28f, h * 0.42f,
                    0f, h * 0.35f
                )
                close()
            }
            drawPath(
                path = topWave,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFBBF24), // Taxi Amber Light
                        Color(0xFFF59E0B), // Taxi Amber
                        Color(0xFFF5A623)  // Vibrant Golden Yellow
                    )
                )
            )

            // Subtle decorative dots on the upper-left (like reference image)
            val dotRadius = 2.5f
            val dotSpacing = 16f
            for (row in 0..8) {
                for (col in 0..4) {
                    val dotX = 16f + col * dotSpacing
                    val dotY = h * 0.09f + row * dotSpacing
                    if (dotY < h * 0.28f) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.22f),
                            radius = dotRadius,
                            center = Offset(dotX, dotY)
                        )
                    }
                }
            }

            // Bottom-right & bottom-left subtle corner accents matching reference image
            val bottomRightWave = Path().apply {
                moveTo(w, h)
                lineTo(w, h * 0.93f)
                cubicTo(w * 0.90f, h * 0.96f, w * 0.82f, h, w * 0.78f, h)
                close()
            }
            drawPath(
                path = bottomRightWave,
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    colors = listOf(Color(0xFFFBBF24).copy(alpha = 0.65f), Color(0xFFF59E0B).copy(alpha = 0.75f))
                )
            )

            val bottomLeftWave = Path().apply {
                moveTo(0f, h)
                lineTo(0f, h * 0.88f)
                cubicTo(w * 0.12f, h * 0.90f, w * 0.18f, h * 0.97f, w * 0.15f, h)
                close()
            }
            drawPath(
                path = bottomLeftWave,
                color = Color(0xFFFBBF24).copy(alpha = 0.50f)
            )
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 440.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar: Language Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            val nextLang = if (currentLanguage == "EN") "TA" else "EN"
                            LanguageManager.setLanguage(context, nextLang)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (currentLanguage == "TA") "தமிழ்" else "English",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Brand Name & Greeting (Covai Meter Taxi)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Covai Meter\nTaxi",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        lineHeight = 38.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Driver Sign In".tr,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Unified Elevated Card with Driver ID and PIN (High Contrast & Clear Visibility)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = Color(0x33000000)),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        // 1. Driver ID Field
                        Text(
                            text = "Driver ID".tr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (driverIdInput.isNotEmpty()) Color(0xFFF59E0B) else Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = if (driverIdInput.isNotEmpty()) Color(0xFFD97706) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                BasicTextField(
                                    value = driverIdInput,
                                    onValueChange = {
                                        driverIdInput = it
                                        errorMessage = null
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Characters,
                                        imeAction = ImeAction.Next
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("driver_id_input"),
                                    decorationBox = { innerTextField ->
                                        if (driverIdInput.isEmpty()) {
                                            Text(
                                                text = "Enter Driver ID".tr,
                                                fontSize = 14.sp,
                                                color = Color(0xFF94A3B8),
                                                fontWeight = FontWeight.Normal
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (driverIdInput.isNotBlank()) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Valid",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. PIN Field
                        Text(
                            text = "PIN".tr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, if (pinInput.isNotEmpty()) Color(0xFFF59E0B) else Color(0xFFCBD5E1))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (pinInput.isNotEmpty()) Color(0xFFD97706) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                BasicTextField(
                                    value = pinInput,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }.take(4)
                                        pinInput = digits
                                        errorMessage = null
                                        if (digits.length == 4) {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    textStyle = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        letterSpacing = if (isPinVisible) 2.sp else 6.sp
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("driver_pin_input"),
                                    decorationBox = { innerTextField ->
                                        if (pinInput.isEmpty()) {
                                            Text(
                                                text = "Enter 4-digit PIN".tr,
                                                fontSize = 14.sp,
                                                color = Color(0xFF94A3B8),
                                                letterSpacing = 0.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                IconButton(
                                    onClick = { isPinVisible = !isPinVisible },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Forgot Password / PIN Link
                Text(
                    text = "Forgot password?".tr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF64748B),
                    modifier = Modifier
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+9104224939999"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .padding(vertical = 4.dp, horizontal = 12.dp)
                )

                // Error Message Banner (Strictly says "Incorrect PIN" when invalid, no database mentions)
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 13.sp,
                                color = Color(0xFFB91C1C),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Pill Shaped Login Button (Matching reference design)
                Button(
                    onClick = {
                        val cleanId = driverIdInput.trim()
                        val cleanPin = pinInput.trim()

                        if (cleanId.isEmpty() || cleanPin.isEmpty()) {
                            errorMessage = "Please enter both Driver ID and PIN."
                            return@Button
                        }

                        isLoading = true
                        errorMessage = null

                        scope.launch {
                            val result = FirebaseManager.authenticateDriver(context, cleanId, cleanPin)
                            isLoading = false

                            result.onSuccess { driverData ->
                                viewModel.applyFirebaseDriverLogin(
                                    driverId = driverData.id,
                                    driverName = driverData.driverName,
                                    mobileNumber = driverData.mobileNumber,
                                    vehicleNumber = driverData.vehicleNumber,
                                    vehicleCategory = driverData.vehicleCategory,
                                    vehicleModel = driverData.vehicleModel,
                                    photoPath = driverData.localPhotoPath ?: ""
                                )
                            }.onFailure { error ->
                                val rawMsg = error.localizedMessage ?: "Login failed"
                                errorMessage = if (rawMsg.contains("PIN", ignoreCase = true) || rawMsg.contains("pin", ignoreCase = true)) {
                                    "Incorrect PIN"
                                } else {
                                    rawMsg
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFE2E8F0),
                        disabledContentColor = Slate400
                    ),
                    contentPadding = PaddingValues(),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(6.dp, RoundedCornerShape(26.dp), spotColor = Color(0x33F5A623))
                        .background(
                            brush = if (!isLoading) {
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFFBBF24), Color(0xFFF59E0B), Color(0xFFF5A623))
                                )
                            } else {
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                                )
                            },
                            shape = RoundedCornerShape(26.dp)
                        )
                        .testTag("login_submit_btn")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Verifying...".tr,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Login".tr,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Reference Text: "Don't have an account? Call Office"
                Row(
                    modifier = Modifier
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+9104224939999"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .padding(bottom = 20.dp)
                        .testTag("login_office_call_btn"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account? ".tr,
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Normal
                    )
                    Text(
                        text = "Call Office".tr,
                        fontSize = 13.sp,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MandatoryProfileSetupScreen(viewModel: TaxiMeterViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDriverName by viewModel.driverName.collectAsState()
    val currentVehicleNumber by viewModel.vehicleNumber.collectAsState()
    val currentDriverMobile by viewModel.driverMobile.collectAsState()
    val currentVehicleCategory by viewModel.vehicleCategory.collectAsState()

    var driverNameVal by remember(currentDriverName) { mutableStateOf(currentDriverName) }
    var vehicleNumVal by remember(currentVehicleNumber) { mutableStateOf(currentVehicleNumber) }
    var driverMobileVal by remember(currentDriverMobile) { mutableStateOf(currentDriverMobile) }
    var vehicleCatVal by remember(currentVehicleCategory) { mutableStateOf(currentVehicleCategory) }

    val sDriverSelfiePath by viewModel.driverSelfiePath.collectAsState()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            saveAndNormalizeSelfie(context, bitmap, viewModel)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePictureLauncher.launch(null)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)) // Clean, modern off-white background
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        val screenHeight = maxHeight
        val isVeryShort = screenHeight < 640.dp
        val isShort = screenHeight < 740.dp

        val horizontalPadding = if (isVeryShort) 12.dp else if (isShort) 16.dp else 20.dp
        val verticalPadding = if (isVeryShort) 6.dp else if (isShort) 10.dp else 14.dp
        val cardInnerPadding = if (isVeryShort) 10.dp else if (isShort) 12.dp else 16.dp
        val itemSpacing = if (isVeryShort) 5.dp else if (isShort) 7.dp else 10.dp
        val headerTitleSize = if (isVeryShort) 18.sp else if (isShort) 20.sp else 22.sp
        val selfieAvatarSize = if (isVeryShort) 44.dp else if (isShort) 52.dp else 60.dp
        val buttonHeight = if (isVeryShort) 42.dp else if (isShort) 46.dp else 48.dp
        val catChipHeight = if (isVeryShort) 28.dp else if (isShort) 30.dp else 34.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header: Brand Badge + Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isVeryShort) 2.dp else 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(TaxiYellow.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = if (isVeryShort) 3.dp else 4.dp)
                ) {
                    Text(
                        text = "Covai Meter Taxi",
                        color = TaxiBlack,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Text(
                    text = "Driver Registration",
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = headerTitleSize,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        letterSpacing = (-0.5).sp
                    )
                )
            }

            // Main Content Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(cardInnerPadding),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {

                    // Driver Name
                    OutlinedTextField(
                        value = driverNameVal,
                        onValueChange = { driverNameVal = it },
                        label = { Text("Driver Name".tr, fontSize = if (isVeryShort) 11.sp else 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Driver Name icon",
                                tint = Slate400,
                                modifier = Modifier.size(if (isVeryShort) 18.dp else 20.dp)
                            )
                        },
                        singleLine = true,
                        placeholder = { Text("Driver name".tr, fontSize = if (isVeryShort) 12.sp else 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = if (isVeryShort) 13.sp else 14.sp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TaxiYellow,
                            focusedLabelColor = TaxiBlack,
                            unfocusedBorderColor = Slate200,
                            unfocusedLabelColor = Slate400,
                            focusedContainerColor = Color(0xFFFAFAFA)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_driver_name")
                    )

                    // Driver Mobile Number (Exactly 10 Digits, digits only)
                    OutlinedTextField(
                        value = driverMobileVal,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }
                            if (digits.length <= 10) {
                                driverMobileVal = digits
                            }
                        },
                        label = { Text("Mobile Number (10 Digits)".tr, fontSize = if (isVeryShort) 11.sp else 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Driver Mobile icon",
                                tint = Slate400,
                                modifier = Modifier.size(if (isVeryShort) 18.dp else 20.dp)
                            )
                        },
                        singleLine = true,
                        prefix = { Text("+91 ".tr, fontSize = if (isVeryShort) 13.sp else 14.sp) },
                        placeholder = { Text("9876543210".tr, fontSize = if (isVeryShort) 12.sp else 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = if (isVeryShort) 13.sp else 14.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TaxiYellow,
                            focusedLabelColor = TaxiBlack,
                            unfocusedBorderColor = Slate200,
                            unfocusedLabelColor = Slate400,
                            focusedContainerColor = Color(0xFFFAFAFA)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_driver_mobile")
                    )

                    // Vehicle Number
                    OutlinedTextField(
                        value = vehicleNumVal,
                        onValueChange = { vehicleNumVal = it.uppercase(Locale.ROOT) },
                        label = { Text("Vehicle Number".tr, fontSize = if (isVeryShort) 11.sp else 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Pin,
                                contentDescription = "Vehicle Plate icon",
                                tint = Slate400,
                                modifier = Modifier.size(if (isVeryShort) 18.dp else 20.dp)
                            )
                        },
                        singleLine = true,
                        placeholder = { Text("e.g. TN 66 AB 1234".tr, fontSize = if (isVeryShort) 12.sp else 13.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = if (isVeryShort) 13.sp else 14.sp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TaxiYellow,
                            focusedLabelColor = TaxiBlack,
                            unfocusedBorderColor = Slate200,
                            unfocusedLabelColor = Slate400,
                            focusedContainerColor = Color(0xFFFAFAFA)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_vehicle_number")
                    )

                    // Vehicle Category Choice Label
                    Text(
                        text = "VEHICLE CATEGORY",
                        fontSize = if (isVeryShort) 9.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(top = 1.dp)
                    )

                    // Beautifully formatted single horizontal row where all 6 choices are visible at once!
                    val categories = listOf("Mini", "Sedan", "SUV", "SUV+", "Innova", "Traveller")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSelected = vehicleCatVal == cat
                            Card(
                                onClick = { vehicleCatVal = cat },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) TaxiYellow else Slate200
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) TaxiYellowLight else TaxiWhite
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(catChipHeight)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) TaxiBlack else Slate700,
                                        fontSize = if (cat == "Traveller") {
                                            if (isVeryShort) 7.5.sp else 8.sp
                                        } else if (cat == "Innova") {
                                            if (isVeryShort) 8.sp else 8.5.sp
                                        } else {
                                            if (isVeryShort) 8.5.sp else 9.5.sp
                                        },
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Highly Visible Selfie Photo Capture Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFAFAFA), shape = RoundedCornerShape(10.dp))
                            .border(1.dp, Slate200, shape = RoundedCornerShape(10.dp))
                            .padding(if (isVeryShort) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val selfieBitmap = remember(sDriverSelfiePath) {
                            if (sDriverSelfiePath.isNotEmpty()) {
                                try {
                                    BitmapFactory.decodeFile(sDriverSelfiePath)?.asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                        }

                        // Full preview without cropping
                        Box(
                            modifier = Modifier
                                .size(selfieAvatarSize)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Slate100, RoundedCornerShape(12.dp))
                                .border(2.dp, if (sDriverSelfiePath.isNotEmpty()) TaxiYellow else Slate200, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selfieBitmap != null) {
                                Image(
                                    bitmap = selfieBitmap,
                                    contentDescription = "Selfie Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Camera placeholder",
                                    tint = Slate400,
                                    modifier = Modifier.size(if (isVeryShort) 20.dp else 24.dp)
                                )
                            }
                        }

                        // Actions and status on the right, stacked vertically
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            if (sDriverSelfiePath.isNotEmpty()) {
                                Text(
                                    text = "PHOTO CAPTURED ✓",
                                    fontSize = if (isVeryShort) 9.sp else 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                                takePictureLauncher.launch(null)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        border = BorderStroke(1.dp, TaxiYellow),
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = TaxiYellowLight, contentColor = TaxiBlack),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                        modifier = Modifier.height(if (isVeryShort) 26.dp else 28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Re-take",
                                            modifier = Modifier.size(12.dp),
                                            tint = TaxiBlack
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("RE-TAKE".tr, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { rotateDriverSelfie(context, sDriverSelfiePath, viewModel) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = Slate900),
                                        border = BorderStroke(1.dp, Slate300),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                        modifier = Modifier.height(if (isVeryShort) 26.dp else 28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Rotate",
                                            modifier = Modifier.size(12.dp),
                                            tint = Slate900
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("ROTATE".tr, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Text(
                                    text = "Selfie Photo is Required",
                                    fontSize = if (isVeryShort) 9.sp else 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate500
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Button(
                                    onClick = {
                                        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                            takePictureLauncher.launch(null)
                                        } else {
                                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = TaxiBlack),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(if (isVeryShort) 28.dp else 32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Camera Icon",
                                        modifier = Modifier.size(14.dp),
                                        tint = TaxiBlack
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "TAKE SELFIE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // SAVE & START USING METER button
                    Button(
                        onClick = {
                            if (driverNameVal.trim().isBlank() || vehicleNumVal.trim().isBlank() || driverMobileVal.trim().isBlank()) {
                                return@Button
                            }

                            if (driverMobileVal.trim().length != 10) {
                                return@Button
                            }

                            if (sDriverSelfiePath.trim().isBlank()) {
                                return@Button
                            }

                            viewModel.saveDriverDetails(
                                name = driverNameVal.trim(),
                                vNumber = vehicleNumVal.trim(),
                                category = vehicleCatVal,
                                model = "",
                                isNight = false,
                                mobile = driverMobileVal.trim()
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TaxiYellow,
                            contentColor = TaxiBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(buttonHeight)
                            .testTag("save_and_enter_btn")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Save icon",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SAVE & START USING METER",
                                fontSize = if (isVeryShort) 11.sp else 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Text(
                text = "Meter Network • Covai Meter Taxi Support",
                fontSize = if (isVeryShort) 8.sp else 9.sp,
                fontWeight = FontWeight.Medium,
                color = Slate400,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

fun safeStartActivity(context: Context, intent: Intent): Boolean {
    return try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = (context as? Activity)
            ?: ((context as? ContextWrapper)?.baseContext as? Activity)
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(intent)
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun openAppSettingsSafely(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    if (!safeStartActivity(context, intent)) {
        val fallback = Intent(Settings.ACTION_SETTINGS)
        safeStartActivity(context, fallback)
    }
}

@Composable
fun MandatoryPermissionsGateScreen(
    context: Context,
    hasFine: Boolean,
    hasNotification: Boolean,
    hasBackground: Boolean,
    gpsEnabled: Boolean,
    batteryOptimizationIgnored: Boolean,
    onRequestForeground: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestBackground: () -> Unit,
    onOpenGpsSettings: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onReCheck: () -> Unit = {}
) {
    val totalSteps = 3 + (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 1 else 0) + (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) 1 else 0)
    var completedSteps = 0
    if (hasFine) completedSteps++
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasNotification) completedSteps++
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasBackground) completedSteps++
    if (gpsEnabled) completedSteps++
    if (batteryOptimizationIgnored) completedSteps++

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SETUP REQUIRED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Slate900,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Grant permissions to use the Taxi Meter.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Slate500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Step Progress Pill
            Surface(
                color = if (completedSteps == totalSteps) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (completedSteps == totalSteps) Color(0xFF16A34A) else TaxiYellow,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$completedSteps of $totalSteps Complete",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (completedSteps == totalSteps) Color(0xFF16A34A) else Slate700
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 1. Foreground Location Card
            PermissionStatusCard(
                title = "GPS Location Access",
                description = "Enables accurate trip measurement.",
                isGranted = hasFine,
                actionLabel = "GRANT LOCATION ACCESS",
                onAction = onRequestForeground
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Notification Card (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionStatusCard(
                    title = "Notifications Permission",
                    description = "Required for background meter notifications.",
                    isGranted = hasNotification,
                    actionLabel = "ENABLE NOTIFICATIONS",
                    onAction = onRequestNotification
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 3. Background Location Card (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionStatusCard(
                    title = "Background Location (Allow All The Time)",
                    description = "Allow all-time location access for accurate trip tracking.",
                    isGranted = hasBackground,
                    actionLabel = "SELECT 'ALLOW ALL THE TIME'",
                    onAction = onRequestBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 4. GPS Enabled Card
            PermissionStatusCard(
                title = "Phone Location (GPS) Enabled",
                description = "Enable GPS for accurate tracking.",
                isGranted = gpsEnabled,
                actionLabel = "ENABLE GPS SERVICES",
                onAction = onOpenGpsSettings
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Battery Optimization Card (MANDATORY)
            PermissionStatusCard(
                title = "Battery Optimization Bypass (Mandatory)",
                description = "Disable battery optimization for continuous tracking.",
                isGranted = batteryOptimizationIgnored,
                actionLabel = "DISABLE BATTERY OPTIMIZATION",
                onAction = onRequestBatteryOptimization,
                isMandatory = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Open App Settings (Re-check button removed per user request)
            OutlinedButton(
                onClick = onOpenAppSettings,
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate800),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_app_settings_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Slate700,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "OPEN APP SETTINGS PAGE".tr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    isMandatory: Boolean = true
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isGranted) 0.5.dp else 1.5.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isGranted) Color(0xFFE2E8F0) else Color(0xFFCBD5E1),
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                if (!isGranted) Modifier.clickable { onAction() } else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (isGranted) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Info,
                        contentDescription = if (isGranted) "Granted" else "Required",
                        tint = if (isGranted) Color(0xFF16A34A) else Color(0xFFD97706),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    if (isGranted) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = Slate500
                        )
                    }
                }

                if (isGranted) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "READY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (!isGranted) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate600,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(start = 48.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TaxiYellow,
                        contentColor = TaxiBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MandatorySelfieSetupScreen(viewModel: TaxiMeterViewModel) {
    val context = LocalContext.current
    val sDriverSelfiePath by viewModel.driverSelfiePath.collectAsState()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            saveAndNormalizeSelfie(context, bitmap, viewModel)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePictureLauncher.launch(null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF9F9),
                        Color(0xFFFFF5F5),
                        Color(0xFFFFF0F0)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0x22FFE1E1),
                radius = 320.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.1f)
            )
            drawCircle(
                color = Color(0x11FFE1E1),
                radius = 260.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.85f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(TaxiYellow, Color(0xFFFF4D4D))
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Covai Meter Taxi",
                    color = TaxiBlack,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Selfie Photo Required",
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    letterSpacing = (-0.5).sp
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We have updated our taxi meter app. Please take a selfie photo of yourself. This photo is required for safety verification and will be printed on customer receipts.",
                fontSize = 13.sp,
                color = Slate500,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Selfie Display Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    val selfieBitmap = remember(sDriverSelfiePath) {
                        if (sDriverSelfiePath.isNotEmpty()) {
                            try {
                                BitmapFactory.decodeFile(sDriverSelfiePath)?.asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    if (selfieBitmap != null) {
                        Image(
                            bitmap = selfieBitmap,
                            contentDescription = "Captured Selfie Preview",
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .border(3.dp, TaxiYellow, RoundedCornerShape(20.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .background(Slate100, RoundedCornerShape(20.dp))
                                .border(2.dp, Slate300, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera Icon",
                                tint = Slate400,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }

                    if (sDriverSelfiePath.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    takePictureLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            border = BorderStroke(1.5.dp, TaxiYellow),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = TaxiYellowLight, contentColor = TaxiBlack),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = TaxiBlack)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "RE-TAKE SELFIE PHOTO",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TaxiBlack
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                    takePictureLauncher.launch(null)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = TaxiBlack),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = TaxiBlack)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CAPTURE SELFIE PHOTO",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (sDriverSelfiePath.isNotEmpty()) {
                        Button(
                            onClick = { rotateDriverSelfie(context, sDriverSelfiePath, viewModel) },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = Slate900),
                            border = BorderStroke(1.dp, Slate300),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rotate Photo 90 degrees",
                                    tint = Slate900
                                )
                                Text(
                                    text = "ROTATE PHOTO 90°",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (sDriverSelfiePath.isNotEmpty()) {
                        Button(
                            onClick = {
                                // Selfie is saved and stored, we can proceed
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Proceed")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PROCEED TO TAXI METER",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "",
                fontSize = 11.sp,
                color = Slate400
            )
        }
    }
}

@Composable
fun SwipeToActionButton(
    text: String,
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    resetKey: Any? = null,
    containerColor: Color = TaxiYellow,
    contentColor: Color = TaxiBlack,
    thumbColor: Color = Color.White,
    thumbIconColor: Color = TaxiBlack
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isSwiped by remember { mutableStateOf(false) }

    LaunchedEffect(resetKey) {
        offsetX = 0f
        isSwiped = false
    }

    val alpha = if (isEnabled) 1f else 0.5f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(containerColor.copy(alpha = alpha), RoundedCornerShape(32.dp))
    ) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val buttonSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val maxSwipePx = widthPx - buttonSizePx

        // Background Text
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = contentColor.copy(alpha = alpha),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 1.sp
            )
        }

        val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "swipe")

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(animatedOffsetX.roundToInt(), 0) }
                .size(64.dp)
                .padding(4.dp)
                .background(thumbColor.copy(alpha = alpha), CircleShape)
                .pointerInput(isEnabled, isSwiped) {
                    if (isEnabled && !isSwiped) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX > maxSwipePx * 0.7f) {
                                    offsetX = maxSwipePx
                                    isSwiped = true
                                    onSwipeComplete()
                                } else {
                                    offsetX = 0f
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (!isSwiped) {
                                offsetX = (offsetX + dragAmount).coerceIn(0f, maxSwipePx)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardDoubleArrowRight,
                contentDescription = "Swipe",
                tint = thumbIconColor.copy(alpha = alpha)
            )
        }
    }
}

@Composable
fun getCovaiTermsList(): List<String> = listOf(
    "By installing, registering, accessing, or using the Covai Meter Taxi App, you agree to the following terms and conditions of Covai Meter Taxi:".tr,
    "1. This App is a taxi meter and trip management tool designed to assist drivers in calculating fares, tracking trips, and maintaining trip records.".tr,
    "2. Fare amounts displayed by the App are estimates generated based on configured tariff settings, GPS data, distance, time, and waiting charges. Drivers are solely responsible for verifying, determining, and collecting the final fare from passengers.".tr,
    "3. Drivers are solely responsible for complying with all applicable laws, permits, licences, insurance requirements, taxes, and transport regulations.".tr,
    "4. Covai Meter Taxi and the App developer do not guarantee the accuracy of GPS signals, location tracking, distance calculations, travel time calculations, route information, or fare calculations under all circumstances.".tr,
    "5. Drivers must verify trip details, fare details, and trip completion information before collecting payment from passengers.".tr,
    "6. The App is only a fare calculation tool. Covai Meter Taxi does not participate in fare negotiations, fare collection, payment recovery, or dispute resolution between drivers and passengers.".tr,
    "7. Drivers are solely responsible for any fare disputes, refund requests, overcharge claims, undercharge claims, customer complaints, legal notices, penalties, fines, or regulatory actions arising from fares or services provided by the driver.".tr,
    "8. Drivers are responsible for collecting toll charges, parking charges, permit charges, waiting charges, state entry taxes, and any other applicable charges from passengers where permitted by law.".tr,
    "9. Drivers are responsible for maintaining their device, internet connection, GPS permissions, battery level, and other technical requirements necessary for proper App operation.".tr,
    "10. Any misuse of the App, unauthorized modification, reverse engineering, fraudulent activity, false trip creation, or unlawful use may result in suspension or permanent termination of access without notice.".tr,
    "11. Covai Meter Taxi and the App developer shall not be liable for any loss of income, business interruption, customer disputes, fare disputes, data loss, vehicle damage, accidents, penalties, legal claims, or indirect damages arising from the use of the App.".tr,
    "12. Registration fees, activation fees, subscription fees, renewal fees, verification fees, compliance fees, and any other payments made to Covai Meter Taxi are non-refundable.".tr,
    "13. Covai Meter Taxi may modify App features, tariff settings, pricing structures, policies, or these Terms & Conditions at any time without prior notice.".tr,
    "14. Continued use of the App after any modification constitutes acceptance of the revised Terms & Conditions of Covai Meter Taxi.".tr,
    "15. Any disputes relating to the App or Covai Meter Taxi shall be subject exclusively to the jurisdiction of the courts located in Coimbatore, Tamil Nadu, India.".tr,
    "16. The App developer and Covai Meter Taxi are not responsible for fare disputes, customer disputes, trip issues, route selection, GPS inaccuracies, payment collection, transportation services, customer claims, customer complaints, legal compliance, accidents, damages, penalties, service interruptions, data loss, or any direct or indirect business losses arising from the use of the App.".tr,
    "17. Vendors and Drivers are solely responsible for customer interactions, customer satisfaction, fare collection, OTP verification, transportation services, complaint resolution, dispute handling, legal compliance, and all obligations arising from their taxi operations.".tr,
    "18. The App is provided on an \"AS IS\" and \"AS AVAILABLE\" basis without warranties of any kind. The App developer reserves the right to modify, suspend, restrict, discontinue, or terminate any feature, account, or service at any time without prior notice.".tr,
    "By tapping \"I Agree\", you acknowledge that you have read, understood, and accepted these Terms & Conditions of Covai Meter Taxi and agree that all fare collection, customer interactions, legal compliance, and liabilities relating to taxi services remain solely your responsibility.".tr
)

@Composable
fun MandatoryTermsScreen(viewModel: TaxiMeterViewModel) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TaxiBackground),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GeometricHeader(modifier = Modifier.padding(top = 24.dp, bottom = 16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = TaxiWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Covai Meter Taxi – Terms & Conditions".tr,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    val terms = getCovaiTermsList()

                    for (paragraph in terms) {
                        Text(
                            text = paragraph,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Slate700,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.acceptTermsAndConditions() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TaxiYellow, contentColor = TaxiBlack),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("I Agree".tr, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
