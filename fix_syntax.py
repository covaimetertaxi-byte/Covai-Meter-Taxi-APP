with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if i == 1510: # line 1511 is index 1510
        skip = True
        new_lines.append("            }\n")
    if i == 1524: # line 1525 is Spacer(modifier = Modifier.weight(1.0f))
        skip = False
        
    if not skip:
        new_lines.append(line)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.writelines(new_lines)
