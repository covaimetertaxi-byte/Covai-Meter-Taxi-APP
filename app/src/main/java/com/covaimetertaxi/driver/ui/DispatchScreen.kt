package com.covaimetertaxi.driver.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.covaimetertaxi.driver.network.SupabaseManager
import com.covaimetertaxi.driver.network.SupabaseTrip
import com.covaimetertaxi.driver.service.TaxiDispatchService
import com.covaimetertaxi.driver.service.TaxiDispatchServiceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.launch

// Color constants
val BrandRed = Color(0xFFE60000)
val BrandWhite = Color(0xFFFFFFFF)
val GraySlate = Color(0xFF2C3E50)
val SoftGrayBg = Color(0xFFF8F9FA)
val DividerGray = Color(0xFFE2E8F0)
val OnlineGreen = Color(0xFF2ECC71)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchScreen(viewModel: TaxiMeterViewModel) {
    val context = LocalContext.current
    val isOnline by TaxiDispatchServiceState.isOnline.collectAsState()
    val openTrips by TaxiDispatchServiceState.openTrips.collectAsState()
    val activeTrip by TaxiDispatchServiceState.activeTrip.collectAsState()
    val latestAlert by TaxiDispatchServiceState.latestTripAlert.collectAsState()
    val ignoredTimeMap by TaxiDispatchServiceState.ignoredTrips.collectAsState()
    
    val scope = rememberCoroutineScope()
    var isCheckingStatus by remember { mutableStateOf(false) }

    var hasArrivedAtPickup by remember { mutableStateOf(false) }
    var enteredOtp by remember { mutableStateOf("") }
    
    LaunchedEffect(activeTrip?.id) {
        hasArrivedAtPickup = false
        enteredOtp = ""
    }

    var currentIstHour by remember { mutableStateOf(com.covaimetertaxi.driver.util.RideOtpManager.getCurrentIstHour()) }
    LaunchedEffect(Unit) {
        while (true) {
            val hour = com.covaimetertaxi.driver.util.RideOtpManager.getCurrentIstHour()
            if (hour != currentIstHour) {
                currentIstHour = hour
            }
            kotlinx.coroutines.delay(5000L)
        }
    }

    val expectedOtp = remember(activeTrip?.customer_mobile, currentIstHour) {
        val sanitized = activeTrip?.customer_mobile.orEmpty().filter { it.isDigit() }
        if (sanitized.length >= 4) {
            com.covaimetertaxi.driver.util.RideOtpManager.calculateOtp(sanitized, currentIstHour)
        } else {
            sanitized.take(4).ifEmpty { "1234" }
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val isOtpSuccess = enteredOtp == expectedOtp && enteredOtp.isNotEmpty()

    LaunchedEffect(isOtpSuccess) {
        if (isOtpSuccess) {
            keyboardController?.hide()
        }
    }

    val isOngoing = activeTrip != null ||
                    viewModel.serviceState.collectAsState().value.status == com.covaimetertaxi.driver.service.TripStatus.RUNNING ||
                    viewModel.serviceState.collectAsState().value.status == com.covaimetertaxi.driver.service.TripStatus.PAUSED

    var currentTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000L)
            currentTick = System.currentTimeMillis()
        }
    }

    // Stop loud sound alert automatically if an ongoing trip starts or completes
    if (isOngoing && latestAlert != null) {
        TaxiDispatchService.activeInstance?.stopLoudAlert()
        TaxiDispatchServiceState.triggerNewTripAlert(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftGrayBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // --- 1. ONLINE STATUS SWITCH CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = BrandWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) OnlineGreen else BrandRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isOnline) OnlineGreen else Color.DarkGray
                        )
                    }
                    Text(
                        text = if (isOnline) "Ready to Receive Trips" else "Turn Online to Get Nearby Trips",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Switch(
                    checked = isOnline,
                    onCheckedChange = { online ->
                        if (!SupabaseManager.isConfigured(context)) {
                            return@Switch
                        }
                        if (online) {
                            TaxiDispatchService.startService(context)
                        } else {
                            TaxiDispatchService.stopService(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrandWhite,
                        checkedTrackColor = OnlineGreen,
                        uncheckedThumbColor = BrandWhite,
                        uncheckedTrackColor = Color.LightGray
                    ),
                    modifier = Modifier.testTag("online_toggle")
                )
            }
        }

        // Check if database coordinates require key configs
        if (!SupabaseManager.isConfigured(context)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BrandRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .background(BrandRed.copy(alpha = 0.05f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, contentDescription = "Config Error", tint = BrandRed, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Supabase API configuration is missing! Please configure your Supabase URL and Anon Key in the SETTINGS tab to enable the Real-time Trip Dispatch System.",
                        color = BrandRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return
        }

        // --- 2. ACTIVE ACCEPTED TRIP SCREEN (IF EX-LOCKED) ---
        if (isOngoing) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (activeTrip != null) {
                    val trip = activeTrip!!

            Text(
                text = "ACTIVE TRIP ASSIGNED",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = BrandRed,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = BrandWhite),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TRIP ID: ${trip.trip_id.orEmpty()}",
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFDF2E9))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ASSIGNED RECIPIENT",
                                color = Color(0xFFD35400),
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }

                    Text(
                        text = "DRIVER ID: ${com.covaimetertaxi.driver.network.SupabaseManager.getDriverId(LocalContext.current)}",
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    LocationRow(label = "Pickup Address", address = trip.pickup_location.orEmpty(), tint = OnlineGreen)
                    Spacer(modifier = Modifier.height(12.dp))
                    LocationRow(label = "Drop Address", address = trip.drop_location.orEmpty(), tint = BrandRed)

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Estimated Fare", color = Color.Gray, fontSize = 11.sp)
                            Text("₹${trip.estimated_fare ?: 0.0}", color = BrandRed, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Vehicle Category", color = Color.Gray, fontSize = 11.sp)
                            Text(trip.vehicle_type.orEmpty(), color = Color.DarkGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!trip.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Specific Instructions: ${trip.notes}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Dedicated Call Customer Button action (Modern Gradient Style)
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:${trip.customer_mobile.orEmpty()}"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Dialer unavailable
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(4.dp, shape = RoundedCornerShape(14.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF2ECC71), Color(0xFF27AE60))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Call, contentDescription = "Dial", tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CALL CUSTOMER",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val isMeterRunning = viewModel.serviceState.collectAsState().value.status == com.covaimetertaxi.driver.service.TripStatus.RUNNING ||
                                         viewModel.serviceState.collectAsState().value.status == com.covaimetertaxi.driver.service.TripStatus.PAUSED

                    if (isMeterRunning) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F8F5), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFA3E4D7), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speedometer",
                                tint = Color(0xFF117A65),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "RIDE IS IN PROGRESS",
                                color = Color(0xFF117A65),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "The local taxi meter is active and calculating fare. Use the swipe slider below to complete / stop the meter.",
                                color = Color(0xFF16A085),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Modern Swipe To End Slider replacing standard button
                            SwipeToEndButton(
                                onSwipeComplete = {
                                    viewModel.endTaxiTrip(context)
                                    TaxiDispatchService.activeInstance?.stopLoudAlert()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_complete_meter_and_trip")
                            )
                        }
                    } else {
                        // Stepwise Flow: ARRIVED / OTP Entrance
                        if (!hasArrivedAtPickup) {
                            Column {
                                // Premium, high-contrast white / light-colour Arrived At Pickup Location Button
                                Button(
                                    onClick = {
                                        hasArrivedAtPickup = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF0F172A)
                                    ),
                                    border = BorderStroke(2.dp, Color(0xFF2ECC71)), // Active Online Green border accent
                                    shape = RoundedCornerShape(18.dp),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 4.dp,
                                        pressedElevation = 2.dp
                                    ),
                                    contentPadding = PaddingValues(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(58.dp)
                                        .testTag("btn_arrived_at_pickup")
                                 ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle, 
                                            contentDescription = "Arrived Check Icon", 
                                            tint = Color(0xFF2ECC71), 
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "I HAVE ARRIVED AT PICKUP LOCATION",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A), // Clear, dark high-contrast text
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "NEED ASSISTANCE OR CANCELLATION?",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Office Call Button
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:+914223596446"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Dialer unavailable
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        contentPadding = PaddingValues(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .shadow(2.dp, shape = RoundedCornerShape(12.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                        colors = listOf(Color(0xFF34495E), Color(0xFF2C3E50))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Call, contentDescription = "Office Call", tint = Color.White, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("OFFICE CALL", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White)
                                            }
                                        }
                                    }

                                    // Trip Cancel Button (makes call to cancel)
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:+914223596446"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Dialer unavailable
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        contentPadding = PaddingValues(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(50.dp)
                                            .shadow(2.dp, shape = RoundedCornerShape(12.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                        colors = listOf(Color(0xFFE74C3C), Color(0xFFC0392B))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Cancel, contentDescription = "Cancel Call", tint = Color.White, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("CANCEL TRIP", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Column {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "READY TO START TRIP",
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF0F172A), // Slate 900
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Confirm passenger is on board and tap below to start the local taxi meter.",
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B), // Slate 500
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                lineHeight = 15.sp
                                            )
                                                                               // OTP input field (without hint of expected OTP per driver privacy)
                                        OutlinedTextField(
                                            value = enteredOtp,
                                            onValueChange = { if (it.length <= 4) enteredOtp = it.filter { c -> c.isDigit() } },
                                            label = { Text("Passenger Verification OTP") },
                                            placeholder = { Text("Enter 4-digit OTP from passenger") },
                                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "OTP Icon", tint = Color.Gray) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.Black,
                                                unfocusedTextColor = Color.Black,
                                                focusedBorderColor = if (enteredOtp == expectedOtp) Color(0xFF2E7D32) else Color(0xFFF12711),
                                                focusedLabelColor = if (enteredOtp == expectedOtp) Color(0xFF2E7D32) else Color(0xFFF12711),
                                                unfocusedBorderColor = if (enteredOtp == expectedOtp) Color(0xFF2E7D32) else Color.LightGray,
                                                unfocusedLabelColor = Color.Gray,
                                                focusedContainerColor = Color(0xFFFBFBFB),
                                                unfocusedContainerColor = Color.White
                                            ),
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                             ),
                                            shape = RoundedCornerShape(16.dp),
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("otp_input_field")
                                        )

                                        if (enteredOtp.isNotEmpty()) {
                                            if (enteredOtp == expectedOtp) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp))
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Success",
                                                        tint = Color(0xFF2E7D32),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("OTP Verified! Ready to start taxi meter.", color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else if (enteredOtp.length == 4) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFFFFF0F0), shape = RoundedCornerShape(12.dp))
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Cancel,
                                                        contentDescription = "Error",
                                                        tint = Color(0xFFC62828),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Incorrect OTP code. Please verify with passenger.", color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        val isDispatchOtpCorrect = enteredOtp == expectedOtp

                                        Button(
                                            onClick = {
                                                if (isDispatchOtpCorrect) {
                                                    viewModel.startTaxiTrip(
                                                        context = context,
                                                        customerMobile = trip.customer_mobile.orEmpty(),
                                                        customBaseFare = trip.base_fare,
                                                        customKmsFare = trip.kms_fare
                                                    )
                                                }
                                            },
                                            enabled = isDispatchOtpCorrect,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                                            contentPadding = PaddingValues(),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                                .shadow(if (isDispatchOtpCorrect) 4.dp else 0.dp, shape = RoundedCornerShape(16.dp))
                                                .testTag("btn_verify_otp_start")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        brush = if (isDispatchOtpCorrect) {
                                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                                colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)) // Beautiful active green gradient
                                                            )
                                                        } else {
                                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                                colors = listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)) // Elegant Slate disabled gradient
                                                            )
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                     Icon(
                                                         imageVector = Icons.Default.PlayArrow,
                                                         contentDescription = "Start Ride",
                                                         tint = if (isDispatchOtpCorrect) Color.White else Color(0xFF94A3B8),
                                                         modifier = Modifier.size(20.dp)
                                                     )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "START TAXI METER",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 13.sp,
                                                        color = if (isDispatchOtpCorrect) Color.White else Color(0xFF94A3B8),
                                                        letterSpacing = 0.5.sp
                                                    )
                                                }
                                            }
                                        }     }
                                    }
                                }

                                // Support & cancellation options instead of override complete option
                                Spacer(modifier = Modifier.height(18.dp))
                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "NEED ASSISTANCE OR CANCELLATION?",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Office Call Button
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:+914223596446"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Dialer unavailable
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        contentPadding = PaddingValues(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(50.dp)
                                            .shadow(2.dp, shape = RoundedCornerShape(12.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                        colors = listOf(Color(0xFF34495E), Color(0xFF2C3E50))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Call, contentDescription = "Office Call", tint = Color.White, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("OFFICE CALL", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White)
                                            }
                                        }
                                    }

                                    // Trip Cancel Button (makes call to cancel)
                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:+914223596446"))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Dialer unavailable
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                        contentPadding = PaddingValues(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(50.dp)
                                            .shadow(2.dp, shape = RoundedCornerShape(12.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                        colors = listOf(Color(0xFFE74C3C), Color(0xFFC0392B))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Cancel, contentDescription = "Cancel Call", tint = Color.White, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("CANCEL TRIP", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandWhite),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = "In Progress",
                            tint = BrandRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "RIDE CURRENTLY IN PROGRESS",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "A local taxi meter calculation is currently active. Use the METER tab to track and end the ongoing ride. Upcoming trips are paused until the current ride is completed.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            }
        } else {
            val displayedOpenTrips = openTrips.filter { trip ->
                val ignoredAt = ignoredTimeMap[trip.id ?: ""] ?: 0L
                currentTick - ignoredAt >= 60000L
            }

            // --- 3. OPEN TRIPS SYSTEM CONTAINER ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AVAILABLE OPEN TRIPS (${displayedOpenTrips.size})",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = GraySlate
                )
                
                IconButton(onClick = {
                    scope.launch {
                        val all = SupabaseManager.fetchAllTrips(context)
                        val driverLoc = com.covaimetertaxi.driver.service.TaxiDispatchService.activeInstance?.lastKnownLocation
                        val openOnes = all.filter { trip ->
                            if (trip.status != "OPEN") return@filter false
                            val dispatchType = trip.dispatch_type ?: "BROADCAST"
                            if (dispatchType.uppercase(java.util.Locale.ROOT) == "RADIUS" && driverLoc != null) {
                                val pickupLat = trip.pickup_latitude ?: return@filter true
                                val pickupLng = trip.pickup_longitude ?: return@filter true
                                val radiusKms = trip.radius_kms ?: 10.0
                                val results = FloatArray(1)
                                try {
                                    android.location.Location.distanceBetween(driverLoc.latitude, driverLoc.longitude, pickupLat, pickupLng, results)
                                    val distanceKms = results[0] / 1000.0
                                    distanceKms <= radiusKms
                                } catch (e: Exception) {
                                    true
                                }
                            } else {
                                true
                            }
                        }
                        TaxiDispatchServiceState.setOpenTrips(openOnes)
                    }
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync Now", tint = BrandRed)
                }
            }

            if (!isOnline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(BrandWhite, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = "Offline Mode",
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You are OFFLINE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Go ONLINE to start receiving trips",
                            fontSize = 13.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (displayedOpenTrips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(BrandWhite, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BrandRed)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Waiting for New Trips...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Stay online to receive live trip requests",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedOpenTrips, key = { it.id ?: "" }) { trip ->
                        OpenTripListCard(
                            trip = trip,
                            onAccept = {
                                scope.launch {
                                    TaxiDispatchService.activeInstance?.stopLoudAlert()
                                    val success = SupabaseManager.acceptTrip(
                                        context = context,
                                        tripId = trip.id ?: "",
                                        driverId = SupabaseManager.getDriverId(context)
                                    )
                                    if (success) {
                                        val myDriverId = SupabaseManager.getDriverId(context)
                                        val updatedTrip = trip.copy(status = "ACCEPTED", driver_id = myDriverId)
                                        TaxiDispatchServiceState.setActiveTrip(updatedTrip)
                                        val currentList = TaxiDispatchServiceState.openTrips.value
                                        TaxiDispatchServiceState.setOpenTrips(currentList.filter { it.id != trip.id })
                                    }
                                }
                            },
                            onIgnore = {
                                TaxiDispatchService.activeInstance?.stopLoudAlert()
                                TaxiDispatchServiceState.ignoreTrip(trip.id ?: "")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OpenTripListCard(trip: SupabaseTrip, onAccept: () -> Unit, onIgnore: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BrandWhite),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: ${trip.trip_id}",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE8F8F5))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = trip.vehicle_type.orEmpty(),
                        color = Color(0xFF117A65),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LocationRow(label = "Pickup", address = trip.pickup_location.orEmpty(), tint = OnlineGreen)
            Spacer(modifier = Modifier.height(8.dp))
            LocationRow(label = "Drop", address = trip.drop_location.orEmpty(), tint = BrandRed)

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerGray)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Customer Mobile", color = Color.Gray, fontSize = 10.sp)
                    Text("Hidden until accepted", color = Color.Gray, fontWeight = FontWeight.Normal, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Est Fare", color = Color.Gray, fontSize = 10.sp)
                    Text("₹${trip.estimated_fare}", color = BrandRed, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onIgnore,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Icon(Icons.Default.Block, contentDescription = "Ignore", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("IGNORE 60s", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    modifier = Modifier.weight(1.3f).height(44.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = "Accept", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ACCEPT TRIP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun LocationRow(label: String, address: String, tint: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label.uppercase(), color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(address, color = Color.DarkGray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SwipeToEndButton(
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragAmount by remember { mutableStateOf(0f) }
    val maxDragX = 240.dp
    val density = LocalDensity.current
    val maxDragPx = with(density) { maxDragX.toPx() }

    val animatedOffset by animateFloatAsState(
        targetValue = dragAmount,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "drag"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFFEAEDED))
            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        val progress = if (maxDragPx > 0) (animatedOffset / maxDragPx).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(Color(0xFFE74C3C), Color(0xFFC0392B))
                    )
                )
        )

        Text(
            text = "SWIPE TO END TRIP >>",
            color = if (progress > 0.6f) Color.White else Color(0xFF7F8C8D),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .size(64.dp)
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color(0xFFC0392B), CircleShape)
                .pointerInput(maxDragPx) {
                    detectDragGestures(
                        onDragEnd = {
                            if (dragAmount >= maxDragPx * 0.82f) {
                                dragAmount = maxDragPx
                                onSwipeComplete()
                            } else {
                                dragAmount = 0f
                            }
                        },
                        onDragCancel = {
                            dragAmount = 0f
                        },
                        onDrag = { change, dragAmountDelta ->
                            change.consume()
                            dragAmount = (dragAmount + dragAmountDelta.x).coerceIn(0f, maxDragPx)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Swipe arrow",
                tint = Color(0xFFC0392B),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}


