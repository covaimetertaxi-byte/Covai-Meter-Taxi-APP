import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Replace "modifier = Modifier,\n                    modifier = Modifier" with "modifier = Modifier"
content = re.sub(r'modifier = Modifier,\s*modifier = Modifier', 'modifier = Modifier', content)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
