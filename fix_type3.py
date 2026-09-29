with open('app/src/main/java/com/covaimetertaxi/driver/ui/theme/Type.kt', 'r') as f:
    content = f.read()

content = content.replace("import androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.unit.sp", "import androidx.compose.ui.unit.sp")

with open('app/src/main/java/com/covaimetertaxi/driver/ui/theme/Type.kt', 'w') as f:
    f.write(content)
