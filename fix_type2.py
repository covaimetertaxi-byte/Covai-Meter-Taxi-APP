import re

with open('app/src/main/java/com/covaimetertaxi/driver/ui/theme/Type.kt', 'r') as f:
    content = f.read()

# Let's replace the whole Typography initialization to use RobotoFontFamily for everything
typography_override = """
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
"""

content = re.sub(r'// Set of Material typography styles to start with.*', typography_override, content, flags=re.DOTALL)

with open('app/src/main/java/com/covaimetertaxi/driver/ui/theme/Type.kt', 'w') as f:
    f.write(content)

