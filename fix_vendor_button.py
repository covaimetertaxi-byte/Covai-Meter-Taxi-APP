import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

pattern = re.compile(r'Box\(\s*modifier = Modifier\s*\.size\(36\.dp\)\s*\.background\(TaxiYellow, RoundedCornerShape\(10\.dp\)\),\s*contentAlignment = Alignment\.Center\s*\)\s*\{\s*Icon\(\s*imageVector = Icons\.Default\.Business,\s*contentDescription = "Vendor",\s*tint = TaxiBlack,\s*modifier = Modifier\.size\(20\.dp\)\s*\)\s*\}', re.DOTALL)
content = pattern.sub('', content)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
