import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

pattern = re.compile(r'SwipeToActionButton\(\s*text = "SWIPE TO END TRIP",\s*onSwipeComplete = \{ showEndConfirmationDialog = true \},.*?modifier = Modifier\s*\.weight\(1f\)\s*\.testTag\("end_trip_button"\)\s*\)', re.DOTALL)

replacement = """SwipeToActionButton(
                text = "SWIPE TO END TRIP",
                onSwipeComplete = { showEndConfirmationDialog = true },
                containerColor = Color(0xFFE11D48), // Rose Red
                contentColor = Color.White,
                thumbColor = Color.White,
                thumbIconColor = Color(0xFFE11D48),
                resetKey = showEndConfirmationDialog,
                modifier = Modifier
                    .weight(1f)
                    .testTag("end_trip_button")
            )"""

if pattern.search(content):
    content = pattern.sub(replacement, content)
    with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
        f.write(content)
    print("Replaced successfully!")
else:
    print("Not found.")

