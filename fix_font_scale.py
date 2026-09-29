with open('app/src/main/java/com/covaimetertaxi/driver/ui/theme/Theme.kt', 'r') as f:
    content = f.read()

new_theme = """
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
                androidx.compose.ui.platform.LocalDensity provides customDensity
            ) {
                content()
            }
        }
    )
}
"""

content = content.replace('''
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = YellowLightColorScheme,
        typography = Typography,
        content = content
    )
}
'''.strip(), new_theme.strip())

with open('app/src/main/java/com/covaimetertaxi/driver/ui/theme/Theme.kt', 'w') as f:
    f.write(content)
