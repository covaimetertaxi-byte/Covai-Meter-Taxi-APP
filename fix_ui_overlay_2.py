import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Remove overlayRequestLauncher
pattern = re.compile(r'\s*// Toggle overlay launcher\s*val overlayRequestLauncher = rememberLauncherForActivityResult\(\s*contract = ActivityResultContracts\.StartActivityForResult\(\)\s*\) \{\s*canDraw\.value = Settings\.canDrawOverlays\(context\)\s*Toast\.makeText\(context, "Overlay configuration verified!", Toast\.LENGTH_SHORT\)\.show\(\)\s*\}', re.DOTALL)
content = pattern.sub('', content)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
