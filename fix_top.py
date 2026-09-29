import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Fix the broken package declaration
content = content.replace('import androidx.compose.material.icons.filled.Languageimport androidx.compose.material.icons.filled.SwapHorizimport com.covaimetertaxi.driver.util.trimport com.covaimetertaxi.driver.util.LanguageManagerpackage com.covaimetertaxi.driver', 
'''package com.covaimetertaxi.driver

import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SwapHoriz
import com.covaimetertaxi.driver.util.tr
import com.covaimetertaxi.driver.util.LanguageManager''')

# Fix line 2271
# It might be `Text( text = "ACTIONS".tr, ... )` which isn't valid if the regex added .tr after the parameter name
content = re.sub(r'(\s*Text\(\s*text\s*=\s*"[^"]+)".tr,', r'\1.tr",', content)
# Wait, let's just see what the line looks like first.

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)

