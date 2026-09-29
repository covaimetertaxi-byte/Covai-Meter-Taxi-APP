import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

vars_orig = """    val sVehicleNum by viewModel.vehicleNumber.collectAsState()
    val sDriverSelfiePath by viewModel.driverSelfiePath.collectAsState()

    val isProfileSetupNeeded = sDriverName.trim().isEmpty() || sVehicleNum.trim().isEmpty()"""

vars_new = """    val sVehicleNum by viewModel.vehicleNumber.collectAsState()
    val sDriverSelfiePath by viewModel.driverSelfiePath.collectAsState()
    val hasAcceptedTerms by viewModel.hasAcceptedTerms.collectAsState()

    val isProfileSetupNeeded = sDriverName.trim().isEmpty() || sVehicleNum.trim().isEmpty()"""

content = content.replace(vars_orig, vars_new)

checks_orig = """            onReCheck = {
                checkPermissionsAndGps()
                if (hasFineState && hasNotificationState && hasBackgroundState && gpsEnabledState && batteryOptimizationIgnoredState) {
                    Toast.makeText(context, "All setup verified successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Some requirements are still missing.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    } else if (isProfileSetupNeeded) {"""

checks_new = """            onReCheck = {
                checkPermissionsAndGps()
                if (hasFineState && hasNotificationState && hasBackgroundState && gpsEnabledState && batteryOptimizationIgnoredState) {
                    Toast.makeText(context, "All setup verified successfully!".tr, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Some requirements are still missing.".tr, Toast.LENGTH_SHORT).show()
                }
            }
        )
    } else if (!hasAcceptedTerms) {
        MandatoryTermsScreen(viewModel = viewModel)
    } else if (isProfileSetupNeeded) {"""

content = content.replace(checks_orig, checks_new)

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
