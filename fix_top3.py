with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    lines = f.readlines()

# Find the package line
pkg_index = -1
for i, line in enumerate(lines):
    if line.startswith('package '):
        pkg_index = i
        break

if pkg_index > 0:
    pkg_line = lines.pop(pkg_index)
    lines.insert(0, pkg_line + '\n')

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.writelines(lines)
