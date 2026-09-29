with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace('.padding(horizontal = 8.dp, bottom = 8.dp)', '.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)')

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
