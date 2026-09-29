import re

# Read TamilDictionary keys from LanguageManager.kt
keys = []
with open('app/src/main/java/com/covaimetertaxi/driver/util/LanguageManager.kt', 'r') as f:
    in_dict = False
    for line in f:
        if "val TamilDictionary = mapOf(" in line:
            in_dict = True
            continue
        if in_dict:
            if ")" in line and "to" not in line:
                break
            # match "KEY" to "VALUE"
            m = re.search(r'"([^"]+)"\s*to\s*".+"', line)
            if m:
                keys.append(m.group(1))

print(f"Loaded {len(keys)} keys")

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'r') as f:
    content = f.read()

# For each key, replace occurrences of "key" with "key".tr inside the file.
# We have to be careful not to replace it if it's already .tr or inside other strings.
# But it's mostly inside Compose Text("key") or label = "key"
for key in keys:
    # Escape key for regex
    escaped_key = re.escape(key)
    # Match Text("key") -> Text("key".tr)
    # Match label = "key" -> label = "key".tr
    # Match value = "key" -> value = "key".tr
    # We will use a regex that looks for exactly "key" and appends .tr if it isn't followed by .tr
    
    # Text("key")
    content = re.sub(r'Text\(\s*"' + escaped_key + r'"\s*(?!\.tr)', r'Text("' + key + r'".tr', content)
    
    # label = "key"
    content = re.sub(r'label\s*=\s*"' + escaped_key + r'"\s*(?!\.tr)', r'label = "' + key + r'".tr', content)
    
    # title = { Text("key") } is handled by the first rule

with open('app/src/main/java/com/covaimetertaxi/driver/MainActivity.kt', 'w') as f:
    f.write(content)

