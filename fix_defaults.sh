awk '
/init \{/ {
    print $0
    print "        if (_baseFare.value <= 0.0) {"
    print "            _baseFare.value = 80.0"
    print "            prefs.edit().putFloat(\"base_fare\", 80.0f).apply()"
    print "        }"
    print "        if (_perKmFare.value <= 0.0) {"
    print "            _perKmFare.value = 28.0"
    print "            prefs.edit().putFloat(\"per_km_fare\", 28.0f).apply()"
    print "        }"
    print "        if (_packageBaseFare.value <= 0.0) {"
    print "            _packageBaseFare.value = 350.0"
    print "            prefs.edit().putFloat(\"pkg_base_fare\", 350.0f).apply()"
    print "        }"
    print "        if (_packagePerHourRate.value <= 0.0) {"
    print "            _packagePerHourRate.value = 350.0"
    print "            prefs.edit().putFloat(\"pkg_per_hour_rate\", 350.0f).apply()"
    print "        }"
    print "        if (_packagePerKmRate.value <= 0.0) {"
    print "            _packagePerKmRate.value = 25.0"
    print "            prefs.edit().putFloat(\"pkg_per_km_rate\", 25.0f).apply()"
    print "        }"
    print "        if (_packageExtraKmRate.value <= 0.0) {"
    print "            _packageExtraKmRate.value = 25.0"
    print "            prefs.edit().putFloat(\"pkg_extra_km_rate\", 25.0f).apply()"
    print "        }"
    next
}
{ print }
' app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt > temp_vm.kt
mv temp_vm.kt app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt
