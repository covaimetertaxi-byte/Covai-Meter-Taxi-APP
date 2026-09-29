import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# Find the start of the if (state.isPackageMeter) block
pattern = re.compile(r'if \(state\.isPackageMeter\) \{.*?} else \{(\s*// The Digital Meter Panel in Geometric Balance.*?\s*)\}\s*Spacer\(modifier = Modifier\.height\(16\.dp\)\)', re.DOTALL)

match = pattern.search(content)
if match:
    else_block_content = match.group(1)
    
    # We want to replace the whole if/else with just the else block
    replacement = f"{else_block_content}\n        Spacer(modifier = Modifier.height(16.dp))"
    
    content = content[:match.start()] + replacement + content[match.end():]
    
    with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
        f.write(content)
    print("Successfully updated meter UI to be identical for both modes.")
else:
    print("Could not find the if(state.isPackageMeter) block.")

