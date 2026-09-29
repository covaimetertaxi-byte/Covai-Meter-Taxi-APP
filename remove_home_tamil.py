import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    lines = f.readlines()

# Find DriverDetailsScreen bounds
start_idx = -1
end_idx = -1
for i, line in enumerate(lines):
    if "fun DriverDetailsScreen(" in line:
        start_idx = i
    if "fun LiveDisplayScreen(" in line:
        end_idx = i
        break

if start_idx != -1 and end_idx != -1:
    for i in range(start_idx, end_idx):
        lines[i] = lines[i].replace('.tr', '')

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.writelines(lines)
