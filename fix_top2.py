import re

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.startswith('import ') and 'package com.covaimetertaxi.driver' in line:
        pass # this line is completely messed up. Wait, let's just write a clean top 7 lines
        
# Actually, let's just do it directly.
