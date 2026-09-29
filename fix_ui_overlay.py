import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Delete isOverlayEnabledVal val
content = re.sub(r'\s*val isOverlayEnabledVal by viewModel\.isOverlayEnabled\.collectAsState\(\)', '', content)

# Delete the overlay setting UI block (from Spacer to the end of the Card)
pattern = re.compile(r'\s*Spacer\(modifier = Modifier\.height\(16\.dp\)\)\s*// Floating overlay controls styled\s*Card\(.*?\{.*?Row\(.*?\{.*?Row\(.*?\{.*?Box.*?\{.*?Icon.*?\}\s*Spacer.*?\s*Column \{.*?Text\("Draggable Floating Widget".*?Text\("Draw overlay.*?\}.*?\}\s*Switch\(.*?\)\s*\}\s*\}', re.DOTALL)
content = pattern.sub('', content)

# There is a query overlay setting status check, let's look at that too
content = re.sub(r'\s*// Query overlay setting status check\s*val canDraw = remember \{ mutableStateOf\(Settings\.canDrawOverlays\(context\)\) \}', '', content)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
