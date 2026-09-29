import re

with open('app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt', 'r') as f:
    content = f.read()

func = """    fun acceptTermsAndConditions() {
        _hasAcceptedTerms.value = true
        prefs.edit().putBoolean("has_accepted_terms", true).apply()
    }

    fun saveDriverDetails("""

content = content.replace('    fun saveDriverDetails(', func)

with open('app/src/main/java/com/covaimetertaxi/driver/ui/TaxiMeterViewModel.kt', 'w') as f:
    f.write(content)
