import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# 1. Replace the header
header_orig = """        // Screen Title
        Text(
            text = "Covai Meter Taxi",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Slate900,
            modifier = Modifier.padding(top = 32.dp, start = 24.dp, bottom = 16.dp)
        )"""

header_new = """        // Screen Title & Logout
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Covai Meter Taxi",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Slate900
            )
            androidx.compose.material3.IconButton(
                onClick = {
                    viewModel.logoutDriver(context)
                    Toast.makeText(context, "Logged out successfully!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.background(Color(0xFFFCE7F3), CircleShape)
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Logout".tr, tint = Color(0xFFDB2777))
            }
        }"""

content = content.replace(header_orig, header_new)

# 2. Update Language Block
# We will match the entire Card containing App Preferences.
lang_orig = """        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                val currentLanguage = com.covaimetertaxi.driver.util.LanguageManager.currentLanguage.collectAsState().value
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val newLang = if (currentLanguage == "EN") "TA" else "EN"
                        com.covaimetertaxi.driver.util.LanguageManager.setLanguage(context, newLang)
                    }.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0xFFE0F2FE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFF0284C7)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Language Setting".tr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Text(if (currentLanguage == "EN") "English (Default)".tr else "Tamil".tr, fontSize = 13.sp, color = Slate500, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Slate400)
                }
            }
        }"""

lang_new = """        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        com.covaimetertaxi.driver.util.LanguageManager.setLanguage(context, "EN")
                                        showLangDialog = false
                                    }.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("English (Default)".tr, fontSize = 16.sp)
                                }
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        com.covaimetertaxi.driver.util.LanguageManager.setLanguage(context, "TA")
                                        showLangDialog = false
                                    }.padding(16.dp),
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
                    modifier = Modifier.fillMaxWidth().clickable {
                        showLangDialog = true
                    }.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Language Setting".tr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        Text(if (currentLanguage == "EN") "English (Default)".tr else "Tamil".tr, fontSize = 13.sp, color = Slate500, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }"""

content = content.replace(lang_orig, lang_new)


# 3. Remove Logout block from bottom actions
# We will use regex to remove the entire Logout row up to the end of the Column
logout_pattern = re.compile(r'\s*HorizontalDivider\(color = Slate100, modifier = Modifier\.padding\(horizontal = 20\.dp\)\)\s*// Logout\s*Row\(\s*modifier = Modifier\.fillMaxWidth\(\)\.clickable \{\s*viewModel\.logoutDriver\(context\)\s*Toast\.makeText\(context, "Logged out successfully!", Toast\.LENGTH_SHORT\)\.show\(\)\s*\}\.padding\(20\.dp\),\s*verticalAlignment = Alignment\.CenterVertically\s*\) \{\s*Box\(\s*modifier = Modifier\.size\(40\.dp\)\.background\(Color\(0xFFFCE7F3\), CircleShape\),\s*contentAlignment = Alignment\.Center\s*\) \{ Icon\(Icons\.Default\.Logout, contentDescription = null, tint = Color\(0xFFDB2777\)\) \}\s*Spacer\(modifier = Modifier\.width\(16\.dp\)\)\s*Column\(modifier = Modifier\.weight\(1f\)\) \{\s*Text\("Logout"\.tr, fontSize = 16\.sp, fontWeight = FontWeight\.Bold, color = Color\(0xFFDB2777\)\)\s*\}\s*\}', re.MULTILINE)

content = logout_pattern.sub('', content)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)

