import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

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
            .background(Color(0xFFF3F4F6))
            .verticalScroll(rememberScrollState())
    ) {
        // Screen Title
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Slate900,
            modifier = Modifier.padding(top = 32.dp, start = 24.dp, bottom = 16.dp)
        )

        // PROFILE SECTION
        val selfieBitmap = remember(sDriverSelfiePath) {
            if (sDriverSelfiePath.isNotEmpty()) {
                try { BitmapFactory.decodeFile(sDriverSelfiePath)?.asImageBitmap() } catch (e: Exception) { null }
            } else null
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selfieBitmap != null) {
                    Image(
                        bitmap = selfieBitmap,
                        contentDescription = "Driver Photo",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Slate100, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Slate400, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = driverNameVal.ifEmpty { "Driver Name" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(Color(0xFFF0FDF4), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Verified Account", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    }
                }
            }
        }

        Text(
            text = "VEHICLE DETAILS",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Slate500,
            modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                // Registration
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Slate100, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Slate600) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Registration No.", fontSize = 13.sp, color = Slate500, fontWeight = FontWeight.Medium)
                        Text(vehicleNumVal.ifEmpty { "N/A" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }
                HorizontalDivider(color = Slate100, modifier = Modifier.padding(horizontal = 20.dp))
                // Category
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Slate100, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.LocalTaxi, contentDescription = null, tint = Slate600) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Category", fontSize = 13.sp, color = Slate500, fontWeight = FontWeight.Medium)
                        Text(vehicleCatVal, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }
            }
        }

        Text(
            text = "ACTIONS",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Slate500,
            modifier = Modifier.padding(start = 32.dp, top = 24.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                // Call Support
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+9104224939999"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open dialer context!", Toast.LENGTH_SHORT).show()
                        }
                    }.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0xFFE0E7FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.SupportAgent, contentDescription = null, tint = Color(0xFF4F46E5)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Call Office", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Text("+91 04224939999", fontSize = 13.sp, color = Slate500, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Slate400)
                }
                HorizontalDivider(color = Slate100, modifier = Modifier.padding(horizontal = 20.dp))
                // Logout
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.logoutDriver(context)
                        Toast.makeText(context, "Logged out successfully!", Toast.LENGTH_SHORT).show()
                    }.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0xFFFCE7F3), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFDB2777)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDB2777))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
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
