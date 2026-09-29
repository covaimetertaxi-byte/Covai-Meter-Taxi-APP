import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Define the start and end markers
start_marker = "fun SettingsScreen(viewModel: TaxiMeterViewModel) {"
end_marker = "// SUB-COMPOSABLES HELPER UTILS"

# Regex to match everything between the start of SettingsScreen and the end marker
pattern = re.compile(r'(@Composable\n)?fun SettingsScreen\(viewModel: TaxiMeterViewModel\) \{.*?(?=\n// SUB-COMPOSABLES HELPER UTILS)', re.DOTALL)

new_settings = """@Composable
fun SettingsScreen(viewModel: TaxiMeterViewModel) {
    val context = LocalContext.current
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

    LaunchedEffect(isNightActive) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
    ) {
        // --- HERO HEADER ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Slate900,
                    shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                )
                .padding(top = 40.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circular Selfie
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
                        contentDescription = "Driver Photo Settings Preview",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, TaxiYellow, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Slate700, CircleShape)
                            .border(3.dp, TaxiYellow, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "No Photo",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Name & Badge
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = driverNameVal.ifEmpty { "No Name Set" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(Color(0xFF22C55E).copy(alpha = 0.15f), RoundedCornerShape(50))
                            .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.3f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Driver Badge",
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "TRUSTED & VERIFIED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ADE80),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // --- CONTENT AREA ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Vehicle Details Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "VEHICLE INFORMATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500,
                    letterSpacing = 1.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Registration Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Slate200),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Slate400, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "REG NO.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate500
                            )
                            Text(
                                text = vehicleNumVal.ifEmpty { "N/A" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Slate900
                            )
                        }
                    }
                    
                    // Category Card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Slate200),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Category, contentDescription = null, tint = Slate400, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "CATEGORY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate500
                            )
                            Text(
                                text = vehicleCatVal,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Slate900
                            )
                        }
                    }
                }
            }
            
            // Support Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "SUPPORT & ACTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500,
                    letterSpacing = 1.sp
                )
                
                // Call Support Button
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+9104224939999"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open dialer context!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Slate200),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFEFF6FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.HeadsetMic, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Call Office Support",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = "+91 04224939999",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate500
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                    }
                }
                
                // Logout Button
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.logoutDriver(context)
                        Toast.makeText(context, "Logged out successfully!", Toast.LENGTH_SHORT).show()
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFFFE4E6)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFFFF1F2), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Logout Session",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE11D48)
                            )
                            Text(
                                text = "Sign out from the current meter",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate500
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
"""

if pattern.search(content):
    new_content = pattern.sub(new_settings, content)
    with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
        f.write(new_content)
    print("Replacement successful")
else:
    print("Failed to match pattern")
