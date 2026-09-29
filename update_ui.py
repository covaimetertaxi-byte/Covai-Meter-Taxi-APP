import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Remove Conclude & New Trip button
pattern_btn = re.compile(r'\s*Spacer\(modifier = Modifier\.height\(8\.dp\)\)\s*// New Trip Button\s*Button\(.*?onClick = \{\s*viewModel\.setNavigation\("FORM"\)\s*\},.*?"CONCLUDE & NEW TRIP".*?\}\s*\}\s*\}\s*\}', re.DOTALL)
content = pattern_btn.sub('\n    }\n}', content)

# Remove Icon for Registration No.
pattern_reg_icon = re.compile(r'\s*Box\(\s*modifier = Modifier\.size\(40\.dp\)\.background\(Slate100, CircleShape\),\s*contentAlignment = Alignment\.Center\s*\)\s*\{\s*Icon\(Icons\.Default\.ConfirmationNumber, contentDescription = null, tint = Slate600\)\s*\}\s*Spacer\(modifier = Modifier\.width\(16\.dp\)\)', re.DOTALL)
content = pattern_reg_icon.sub('', content)

# Remove Icon for Category
pattern_cat_icon = re.compile(r'\s*Box\(\s*modifier = Modifier\.size\(40\.dp\)\.background\(Slate100, CircleShape\),\s*contentAlignment = Alignment\.Center\s*\)\s*\{\s*Icon\(Icons\.Default\.LocalTaxi, contentDescription = null, tint = Slate600\)\s*\}\s*Spacer\(modifier = Modifier\.width\(16\.dp\)\)', re.DOTALL)
content = pattern_cat_icon.sub('', content)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
