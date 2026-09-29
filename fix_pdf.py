import re

with open('app/src/main/java/com/covaimetertaxi/driver/util/PdfReceiptGenerator.kt', 'r') as f:
    content = f.read()

# Replace duplicate declarations
content = re.sub(r'\s*// Invoice title and No vertical positioning depends on whether we have a logo\s*val invoiceTitleY = if \(hasLogo\) currentY \+ 115f else currentY \+ 30f\s*val invoiceNoY = if \(hasLogo\) currentY \+ 133f else currentY \+ 48f', '', content)

with open('app/src/main/java/com/covaimetertaxi/driver/util/PdfReceiptGenerator.kt', 'w') as f:
    f.write(content)
