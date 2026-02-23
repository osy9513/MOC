import os
import re

impl_dir = r"d:\ai\MocPlugin\src\main\java\me\user\moc\ability\impl"

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    lines = content.split('\n')
    modified = False
    
    i = 0
    while i < len(lines):
        line = lines[i]
        
        if "hasAbility(" in line and "getCode(" in line and "return" in line:
            m = re.search(r'hasAbility\(\s*([a-zA-Z0-9_]+)\s*,', line)
            if m:
                player_var = m.group(1)
                
                # Check if it already has isSilenced nearby
                has_silence_check = False
                for j in range(max(0, i - 15), min(i + 15, len(lines))):
                    if "isSilenced" in lines[j] or "silencedPlayers.contains" in lines[j]:
                        # Make sure it's checking the same variable
                        if player_var in lines[j] or "Player" in lines[j]:
                            has_silence_check = True
                            break
                        
                if not has_silence_check:
                    indent = len(line) - len(line.lstrip())
                    indent_str = " " * indent
                    silence_code = f"{indent_str}if (isSilenced({player_var})) return;"
                    
                    lines.insert(i + 1, silence_code)
                    modified = True
                    i += 1 
        i += 1
                    
    if modified:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines))
        return True
    return False

count = 0
for file in os.listdir(impl_dir):
    if file.endswith('.java'):
        if process_file(os.path.join(impl_dir, file)):
            count += 1
            print(f"Modified {file}")
            
print(f"Total files modified: {count}")
