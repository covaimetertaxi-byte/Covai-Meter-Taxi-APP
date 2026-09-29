import re

with open('app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt', 'r') as f:
    content = f.read()

# Overwrite old 25.0 default if it exists
fix_snippet = """        if (_packagePerKmRate.value <= 0.0 || _packagePerKmRate.value == 25.0) {
            _packagePerKmRate.value = 20.0
            prefs.edit().putFloat("pkg_per_km_rate", 20.0f).apply()
        }
        if (_packageExtraKmRate.value <= 0.0 || _packageExtraKmRate.value == 25.0) {
            _packageExtraKmRate.value = 20.0
            prefs.edit().putFloat("pkg_extra_km_rate", 20.0f).apply()
        }"""

content = re.sub(r'if \(_packagePerKmRate\.value <= 0\.0\) \{.*?prefs\.edit\(\)\.putFloat\("pkg_extra_km_rate", 20\.0f\)\.apply\(\)\s*\}', fix_snippet, content, flags=re.DOTALL)

with open('app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt', 'w') as f:
    f.write(content)
