import re

with open('app/src/main/java/com/covaimetertaxi/driver/util/PdfReceiptGenerator.kt', 'r') as f:
    content = f.read()

# Remove logo loading and drawing
content = re.sub(r'// 2\. Draw Header.*?// Invoice title', '// 2. Draw Header\n        val invoiceTitleY = currentY + 30f\n        val invoiceNoY = currentY + 48f\n\n        // Invoice title', content, flags=re.DOTALL)

# Remove Company Details Right Aligned
content = re.sub(r'// Company Details Right Aligned.*?canvas\.drawText\("www\.covaimetertaxi\.in", rightLimitX, currentY \+ 73f, companyDetailsPaint\)', '', content, flags=re.DOTALL)

# Move Date up
content = re.sub(r'canvas\.drawText\("Date: \$formattedDate", rightLimitX, currentY \+ 98f, companyDetailsPaint\)', 'canvas.drawText("Date: $formattedDate", rightLimitX, currentY + 48f, companyDetailsPaint)', content)

# Adjust divider
content = re.sub(r'val dividerY = if \(hasLogo\) currentY \+ 155f else currentY \+ 110f', 'val dividerY = currentY + 75f', content)

# Fix recycling logic at the bottom
content = re.sub(r'logoBitmap\?\.recycle\(\)', '', content)

# Remove Company section from page 2
content = re.sub(r'// Section: Company Details.*?// Page 2 Footer', '// Page 2 Footer', content, flags=re.DOTALL)

# Clean up page 2 header
content = re.sub(r'canvas2\.drawText\("covaimetertaxi Trusted & Verified Service", marginX, secTitleY, verificationTitlePaint\)', 'canvas2.drawText("Trusted & Verified Service", marginX, secTitleY, verificationTitlePaint)', content)
content = re.sub(r'canvas2\.drawText\("Covai Meter Taxi - WE CARE FOR YOUR SAFETY", 595f / 2f, footerY2 \+ 22f, footerTextBoldPaint\)', 'canvas2.drawText("WE CARE FOR YOUR SAFETY", 595f / 2f, footerY2 + 22f, footerTextBoldPaint)', content)
content = re.sub(r'trip\.vehicleModel\.ifEmpty \{ "Covai Meter Taxi" \}', 'trip.vehicleModel.ifEmpty { "Meter Taxi" }', content)


with open('app/src/main/java/com/covaimetertaxi/driver/util/PdfReceiptGenerator.kt', 'w') as f:
    f.write(content)
