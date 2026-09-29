import re

with open('app/src/main/java/com/covaimetertaxi/driver/service/TaxiMeterService.kt', 'r') as f:
    content = f.read()

pattern = re.compile(r'var calculatedFare = if \(state\.isPackageMeter\) \{.*?\} else \{', re.DOTALL)

replacement = """var calculatedFare = if (state.isPackageMeter) {
            val totalMinutes = state.durationSeconds.toDouble() / 60.0
            // Auto step-up hours (e.g. 1-60 mins = 1 hr, 61-120 = 2 hrs, etc.)
            val packageHours = maxOf(1, kotlin.math.ceil(totalMinutes / 60.0).toInt())
            
            val hourlyRate = if (state.packagePerHourRate > 0.0) state.packagePerHourRate else state.packageBaseFare
            
            dynamicIncludedKm = packageHours * 10.0
            dynamicIncludedMinutes = packageHours * 60
            dynamicPackageBaseFare = packageHours * hourlyRate
            dynamicName = "$packageHours Hour / ${dynamicIncludedKm.toInt()} KM"

            val packageFare = dynamicPackageBaseFare
            val extraKm = maxOf(0.0, state.distanceKm - dynamicIncludedKm)
            val extraKmFare = extraKm * state.extraKmRate
            
            val waitingMinutes = state.waitingSeconds.toDouble() / 60.0
            val waitingFare = if (state.packageWaitingChargePerMin > 0.0) waitingMinutes * state.packageWaitingChargePerMin else 0.0
            
            packageFare + extraKmFare + waitingFare
        } else {"""

content = pattern.sub(replacement, content)

with open('app/src/main/java/com/covaimetertaxi/driver/service/TaxiMeterService.kt', 'w') as f:
    f.write(content)

print("Calculations updated.")
