with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    lines = f.readlines()

for i in range(1495, 1530):
    if i < len(lines):
        print(f"{i+1}: {lines[i].rstrip()}")
