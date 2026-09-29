import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Make the outer Column in DriverDetailsScreen scrollable.
orig_col = """        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {"""

new_col = """        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {"""

content = content.replace(orig_col, new_col)

# Also fix the inner content to be SpaceBetween using a Spacer with weight if it was meant to fill height, but since it's scrollable, it's better to just have the button at the bottom after a spacer. 
# Wait, actually Arrangement.spacedBy(16.dp) is good enough if it's scrollable. The user just scrolls down to the button.

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)
