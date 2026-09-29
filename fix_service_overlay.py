import re

with open('app/src/main/java/com/covaimetertaxi/driver/service/TaxiMeterService.kt', 'r') as f:
    content = f.read()

# 1. Remove isOverlayActive from LiveTripState
content = re.sub(r'\s*// Overlay enabled state\s*val isOverlayActive: Boolean = false,', '', content)
content = re.sub(r'\s*putBoolean\("isOverlayActive", state\.isOverlayActive\)', '', content)
content = re.sub(r'\s*isOverlayActive = prefs\.getBoolean\("isOverlayActive", false\),', '', content)

# 2. Remove isOverlayActive from startTrip arguments and usage
content = re.sub(r'\s*isOverlayActive: Boolean,', '', content)
content = re.sub(r'\s*isOverlayActive = isOverlayActive,', '', content)

# 3. Remove toggleOverlay function entirely
content = re.sub(r'\s*fun toggleOverlay\(context: Context, enable: Boolean\) \{.*?\n        \}', '', content, flags=re.DOTALL)

# 4. Remove overlay view variables
content = re.sub(r'\s*private var windowManager: WindowManager\? = null\s*private var overlayView: ComposeView\? = null\s*private var serviceLifecycleOwner: ServiceLifecycleOwner\? = null', '', content)

# 5. Remove calls to showFloatingWidget() and removeFloatingWidget()
content = re.sub(r'\s*if \(_tripState\.value\.isOverlayActive\) \{\s*showFloatingWidget\(\)\s*\}', '', content)
content = re.sub(r'\s*removeFloatingWidget\(\)', '', content)

# 6. Remove SHOW_OVERLAY and HIDE_OVERLAY branches from onStartCommand
content = re.sub(r'\s*"SHOW_OVERLAY" -> \{\s*showFloatingWidget\(\)\s*\}\s*"HIDE_OVERLAY" -> \{.*?\}', '', content, flags=re.DOTALL)

# 7. Remove the showFloatingWidget() and removeFloatingWidget() functions
content = re.sub(r'\s*@SuppressLint\("ClickableViewAccessibility"\)\s*private fun showFloatingWidget\(\) \{.*?\n    \}\n\s*private fun removeFloatingWidget\(\) \{.*?\n    \}', '', content, flags=re.DOTALL)

# 8. Remove the FloatingMeterContent composable at the bottom of the file
content = re.sub(r'\s*@Composable\s*fun FloatingMeterContent.*?\}\n\}\n', '\n', content, flags=re.DOTALL)

with open('app/src/main/java/com/covaimetertaxi/driver/service/TaxiMeterService.kt', 'w') as f:
    f.write(content)

print("Service overlay logic removed.")
