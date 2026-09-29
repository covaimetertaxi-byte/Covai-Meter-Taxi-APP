import re

with open('app/src/main/java/com/covaimetertaxi/driver/service/TaxiMeterService.kt', 'r') as f:
    content = f.read()

# Delete showFloatingWidget
pattern1 = re.compile(r'\s*@SuppressLint\("ClickableViewAccessibility"\)\s*private fun showFloatingWidget\(\) \{.*?(?=private fun )', re.DOTALL)
content = pattern1.sub('\n\n    ', content)

# Delete removeFloatingWidget (which I broke into just private fun { overlayView?.let ... })
pattern2 = re.compile(r'\s*private fun \{.*?\n\s*\}\n', re.DOTALL)
content = pattern2.sub('\n', content)

# Remove the bottom FloatingMeterContent composable
pattern3 = re.compile(r'@Composable\nfun FloatingMeterContent.*$', re.DOTALL)
content = pattern3.sub('', content)

# Clean up ViewModel where it references toggleOverlay or isOverlayEnabled
# ... (I'll do ViewModel separately)

with open('app/src/main/java/com/covaimetertaxi/driver/service/TaxiMeterService.kt', 'w') as f:
    f.write(content)
