import re

with open('app/src/main/java/com/covaimetertaxi/driver/util/PdfReceiptGenerator.kt', 'r') as f:
    content = f.read()

# We need to change getFixedTypeface signature to take Context so we can access assets.
# But `fun generatePdf` already receives Context! We can just create Typefaces inside generatePdf using Context.assets

# Let's replace the whole getFixedTypeface logic.

old_typefaces = """        // Standard Typefaces (loading TrueType fonts directly to prevent OEM custom font theme distortion)
        val typefaceRegular = getFixedTypeface(TypefaceStyle.REGULAR)
        val typefaceMedium = getFixedTypeface(TypefaceStyle.MEDIUM)
        val typefaceBold = getFixedTypeface(TypefaceStyle.BOLD)"""

new_typefaces = """        // Standard Typefaces (loading TrueType fonts from assets to prevent OEM custom font theme distortion)
        val typefaceRegular = try { Typeface.createFromAsset(context.assets, "fonts/Roboto-Regular.ttf") } catch (e: Exception) { Typeface.SANS_SERIF }
        val typefaceMedium = try { Typeface.createFromAsset(context.assets, "fonts/Roboto-Medium.ttf") } catch (e: Exception) { Typeface.defaultFromStyle(Typeface.BOLD) }
        val typefaceBold = try { Typeface.createFromAsset(context.assets, "fonts/Roboto-Bold.ttf") } catch (e: Exception) { Typeface.defaultFromStyle(Typeface.BOLD) }"""

content = content.replace(old_typefaces, new_typefaces)

with open('app/src/main/java/com/covaimetertaxi/driver/util/PdfReceiptGenerator.kt', 'w') as f:
    f.write(content)

