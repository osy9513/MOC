import os
import re

base_dir = r"d:\ai\MocPlugin\src\main\java\me\user\moc\ability\impl"
files = [os.path.join(base_dir, f) for f in os.listdir(base_dir) if f.endswith(".java")]

print("=== Suspect (No Action check) ===")
for fpath in files:
    with open(fpath, "r", encoding="utf-8") as f:
        content = f.read()
    if "PlayerInteractEvent" in content and "checkCooldown(" in content:
        if "Action.RIGHT_CLICK" not in content and "contains(\"RIGHT\")" not in content and "isSneaking" not in content:
            print(os.path.basename(fpath))

print("=== Suspect (No item==null check) ===")
for fpath in files:
    with open(fpath, "r", encoding="utf-8") as f:
        content = f.read()
    if "PlayerInteractEvent" in content and "checkCooldown(" in content:
        c = content.replace(" ", "").replace("\n", "")
        if "getItem()==null" not in c and "item==null" not in c:
            print(os.path.basename(fpath))
