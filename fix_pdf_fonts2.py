import re

with open('app/src/main/java/com/covaimetertaxi/driver/util/PdfReceiptGenerator.kt', 'r') as f:
    content = f.read()

new_typefaces = """        // Standard Typefaces (loading TrueType fonts from resources to prevent OEM custom font theme distortion)
        val typefaceRegular = androidx.core.content.res.ResourcesCompat.getFont(context, com.covaimetertaxi.driver.R.font.roboto_regular) ?: Typeface.SANS_SERIF
        val typefaceMedium = androidx.core.content.res.ResourcesCompat.getFont(context, com.covaimetertaxi.driver.R.font.roboto_medium) ?: Typeface.defaultFromStyle(Typeface.BOLD)
        val typefaceBold = androidx.core.content.res.ResourcesCompat.getFont(context, com.covaimetertaxi.driver.R.font.roboto_bold) ?: Typeface.defaultFromStyle(Typeface.BOLD)"""

content = re.sub(r'        // Standard Typefaces \(loading TrueType fonts from assets.*?(?=        // Paint definitions)', new_typefaces + '\n\n', content, flags=re.DOTALL)

with open('app/src/main/java/com/covaimetertaxi/driver/util/PdfReceiptGenerator.kt', 'w') as f:
    f.write(content)
