import re

with open('app/src/main/java/com/covaimetertaxi/driver/ui/theme/Type.kt', 'r') as f:
    content = f.read()

imports = """import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.covaimetertaxi.driver.R

val RobotoFontFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)
"""

content = content.replace("import androidx.compose.ui.text.font.FontFamily", imports)

content = content.replace("fontFamily = FontFamily.Default,", "fontFamily = RobotoFontFamily,")

with open('app/src/main/java/com/covaimetertaxi/driver/ui/theme/Type.kt', 'w') as f:
    f.write(content)

