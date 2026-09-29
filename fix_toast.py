with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace('Toast.makeText(context, "All setup verified successfully!".tr, Toast.LENGTH_SHORT).show()', 'Toast.makeText(context, "All setup verified successfully!", Toast.LENGTH_SHORT).show()')
content = content.replace('Toast.makeText(context, "Some requirements are still missing.".tr, Toast.LENGTH_SHORT).show()', 'Toast.makeText(context, "Some requirements are still missing.", Toast.LENGTH_SHORT).show()')

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
