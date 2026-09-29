import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# I will insert the language switch section right before the "ACTIONS" text in SettingsScreen.
# It looks like:
#         Text(
#            text = "ACTIONS",

language_ui = """        Text(
            text = "App Preferences".tr,
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
        }

"""

content = re.sub(r'(\s*Text\(\s*text\s*=\s*"ACTIONS",)', language_ui + r'\1', content)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
