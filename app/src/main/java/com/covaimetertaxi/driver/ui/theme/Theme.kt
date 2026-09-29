package com.covaimetertaxi.driver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val YellowLightColorScheme = lightColorScheme(
    primary = TaxiYellow,
    onPrimary = TaxiBlack,
    primaryContainer = TaxiYellowLight,
    onPrimaryContainer = TaxiYellowDark,
    secondary = TaxiBlack,
    onSecondary = TaxiWhite,
    background = TaxiBackground,
    onBackground = TaxiBlack,
    surface = TaxiSurface,
    onSurface = TaxiBlack,
    surfaceVariant = TaxiLine,
    onSurfaceVariant = TaxiGray
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = androidx.compose.ui.unit.Density(currentDensity.density, 1f)

    MaterialTheme(
        colorScheme = YellowLightColorScheme,
        typography = Typography,
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides customDensity,
                androidx.compose.material3.LocalTextStyle provides androidx.compose.ui.text.TextStyle(
                    fontFamily = RobotoFontFamily
                )
            ) {
                content()
            }
        }
    )
}
