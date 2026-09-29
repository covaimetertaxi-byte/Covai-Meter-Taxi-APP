import re

with open('app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt', 'r') as f:
    content = f.read()

# Replace _packagePerKmRate.value = 25.0 -> 20.0
content = re.sub(r'_packagePerKmRate\.value = 25\.0', '_packagePerKmRate.value = 20.0', content)

# Replace _packageExtraKmRate.value = 25.0 -> 20.0
content = re.sub(r'_packageExtraKmRate\.value = 25\.0', '_packageExtraKmRate.value = 20.0', content)

# Replace "pkg_per_km_rate", 25.0f -> 20.0f
content = re.sub(r'"pkg_per_km_rate",\s*25\.0f', '"pkg_per_km_rate", 20.0f', content)

# Replace "pkg_extra_km_rate", 25.0f -> 20.0f
content = re.sub(r'"pkg_extra_km_rate",\s*25\.0f', '"pkg_extra_km_rate", 20.0f', content)

with open('app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt', 'w') as f:
    f.write(content)
