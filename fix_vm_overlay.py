import re

with open('app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt', 'r') as f:
    content = f.read()

# Delete _isOverlayEnabled and isOverlayEnabled
content = re.sub(r'\s*private val _isOverlayEnabled = MutableStateFlow\(prefs\.getBoolean\("is_overlay_enabled", false\)\)\s*val isOverlayEnabled = _isOverlayEnabled\.asStateFlow\(\)', '', content)

# Delete usage in startTaxiTrip
content = re.sub(r'\s*_isOverlayEnabled\.value = false\s*prefs\.edit\(\)\.putBoolean\("is_overlay_enabled", false\)\.apply\(\)', '', content)

# Delete toggleSystemOverlay function
content = re.sub(r'\s*fun toggleSystemOverlay\(context: Context, enable: Boolean\) \{.*?\n    \}', '', content, flags=re.DOTALL)

with open('app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt', 'w') as f:
    f.write(content)
