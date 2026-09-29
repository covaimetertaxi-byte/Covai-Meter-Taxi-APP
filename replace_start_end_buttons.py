import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Replace Start Trip button
start_button_pattern = re.compile(r'// Modern, stylish Start Trip Action Button with a beautiful active gradient\s+Button\(.*?\n\s+\)\s+\{.*?\}\s+\}', re.DOTALL)
start_button_replacement = """// Modern Swipe to Start Action
            SwipeToActionButton(
                text = "SWIPE TO START TRIP",
                onSwipeComplete = { onStart(customerMobile, selectedMode == "PACKAGE") },
                isEnabled = isOtpCorrect,
                containerColor = Color(0xFFE60000), // Vibrant Red
                contentColor = Color.White,
                thumbColor = Color.White,
                thumbIconColor = Color(0xFFE60000),
                modifier = Modifier
                    .shadow(if (isOtpCorrect) 6.dp else 0.dp, shape = RoundedCornerShape(32.dp))
                    .testTag("start_trip_button")
            )"""

if start_button_pattern.search(content):
    content = start_button_pattern.sub(start_button_replacement, content)
    print("Replaced Start Trip button.")
else:
    print("Could not find Start Trip button.")

# Replace End Trip button
end_button_pattern = re.compile(r'// End Trip button \(solid red, round dot inside next to text\)\s+Button\(\s+onClick = \{ showEndConfirmationDialog = true \},.*?\{.*?\}\s+\}', re.DOTALL)
end_button_replacement = """// Swipe to End Trip Action
            SwipeToActionButton(
                text = "SWIPE TO END TRIP",
                onSwipeComplete = { showEndConfirmationDialog = true },
                containerColor = Color(0xFFE11D48), // Rose Red
                contentColor = Color.White,
                thumbColor = Color.White,
                thumbIconColor = Color(0xFFE11D48),
                modifier = Modifier
                    .weight(1f)
                    .testTag("end_trip_button")
            )"""

if end_button_pattern.search(content):
    content = end_button_pattern.sub(end_button_replacement, content)
    print("Replaced End Trip button.")
else:
    print("Could not find End Trip button.")

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)

