import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

pattern = re.compile(r'@Composable\nfun SwipeToActionButton\(.*?\}\n\}\n', re.DOTALL)

new_code = """@Composable
fun SwipeToActionButton(
    text: String,
    onSwipeComplete: () -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    resetKey: Any? = null,
    containerColor: Color = TaxiYellow,
    contentColor: Color = TaxiBlack,
    thumbColor: Color = Color.White,
    thumbIconColor: Color = TaxiBlack
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isSwiped by remember { mutableStateOf(false) }

    LaunchedEffect(resetKey) {
        offsetX = 0f
        isSwiped = false
    }

    val alpha = if (isEnabled) 1f else 0.5f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(containerColor.copy(alpha = alpha), RoundedCornerShape(32.dp))
    ) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val buttonSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
        val maxSwipePx = widthPx - buttonSizePx

        // Background Text
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = contentColor.copy(alpha = alpha),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 1.sp
            )
        }

        val animatedOffsetX by animateFloatAsState(targetValue = offsetX, label = "swipe")

        // Draggable Thumb
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(animatedOffsetX.roundToInt(), 0) }
                .size(64.dp)
                .padding(4.dp)
                .background(thumbColor.copy(alpha = alpha), CircleShape)
                .pointerInput(isEnabled, isSwiped) {
                    if (isEnabled && !isSwiped) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX > maxSwipePx * 0.7f) {
                                    offsetX = maxSwipePx
                                    isSwiped = true
                                    onSwipeComplete()
                                } else {
                                    offsetX = 0f
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (!isSwiped) {
                                offsetX = (offsetX + dragAmount).coerceIn(0f, maxSwipePx)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardDoubleArrowRight,
                contentDescription = "Swipe",
                tint = thumbIconColor.copy(alpha = alpha)
            )
        }
    }
}
"""

if pattern.search(content):
    content = pattern.sub(new_code, content)
    with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
        f.write(content)
    print("Replaced SwipeToActionButton.")
else:
    print("Could not find SwipeToActionButton.")
