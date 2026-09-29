with open('app/src/main/java/com/covaimetertaxi/driver/service/TaxiMeterService.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "serviceLifecycleOwner?.onDestroy()" in line:
        skip = True
        continue
    
    if skip and line.strip() == "}":
        pass
    elif skip and "overlayView = null" in line:
        pass
    elif skip and line.strip() == "}":
        pass
    elif skip and "    }" in line:
        skip = False
    else:
        if skip:
            if "@SuppressLint" in line or "private fun acquireWakeLock" in line:
                skip = False
                new_lines.append(line)
        else:
            new_lines.append(line)

with open('app/src/main/java/com/covaimetertaxi/driver/service/TaxiMeterService.kt', 'w') as f:
    f.writelines(new_lines)
