package com.covaimetertaxi.driver.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.covaimetertaxi.driver.R

val RobotoFontFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

// Set of Material typography styles to start with
private val defaultTypography = Typography()

val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = RobotoFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = RobotoFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = RobotoFontFamily),

    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = RobotoFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = RobotoFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = RobotoFontFamily),

    titleLarge = defaultTypography.titleLarge.copy(fontFamily = RobotoFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = RobotoFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = RobotoFontFamily),

    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = RobotoFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = RobotoFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = RobotoFontFamily),

    labelLarge = defaultTypography.labelLarge.copy(fontFamily = RobotoFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = RobotoFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = RobotoFontFamily)
)
