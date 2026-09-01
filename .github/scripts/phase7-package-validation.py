#!/usr/bin/env python3
from pathlib import Path


def replace_once(path, old, new, marker):
    p = Path(path)
    text = p.read_text()
    if marker in text:
        print(f"already applied: {path}")
        return
    if old not in text:
        raise SystemExit(f"anchor missing: {path}: {old[:80]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"anchor not unique: {path}: {text.count(old)}")
    p.write_text(text.replace(old, new, 1))
    print(f"patched: {path}")

# Backup import: all string sets restored here are package-name sets.
replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java",
    '''            for (int i = 0; i < array.length(); i++) {\n                set.add(array.getString(i));\n            }''',
    '''            for (int i = 0; i < array.length(); i++) {\n                String packageName = array.getString(i);\n                if (!PackageNameValidator.isValid(packageName)) {\n                    throw new IllegalArgumentException("Invalid package name in backup: " + key);\n                }\n                set.add(packageName);\n            }''',
    'Invalid package name in backup:'
)

replace_once(
    "app/src/main/java/com/gree1d/reappzuku/core/BackupManager.java",
    '''        while (keys.hasNext()) {\n            String pkg = keys.next();\n            editor.putInt(KEY_MANUAL_OPS_PREFIX + pkg, masks.getInt(pkg));''',
    '''        while (keys.hasNext()) {\n            String pkg = keys.next();\n            if (!PackageNameValidator.isValid(pkg)) {\n                throw new IllegalArgumentException("Invalid package name in manual ops backup");\n            }\n            editor.putInt(KEY_MANUAL_OPS_PREFIX + pkg, masks.getInt(pkg));''',
    'Invalid package name in manual ops backup'
)

# Preset JSON is also importable independently of the full backup.
p = Path("app/src/main/java/com/gree1d/reappzuku/utils/PresetModel.java")
text = p.read_text()
if 'PackageNameValidator.requireValid' not in text:
    if 'import org.json.JSONObject;' not in text:
        raise SystemExit('PresetModel import anchor missing')
    text = text.replace('import org.json.JSONObject;', 'import org.json.JSONObject;\n\nimport com.gree1d.reappzuku.core.PackageNameValidator;', 1)
    text = text.replace('model.appLaunchTriggerPackages.add(launchPkgs.getString(i));',
                        'model.appLaunchTriggerPackages.add(PackageNameValidator.requireValid(launchPkgs.getString(i)));')
    text = text.replace('model.whitelistedApps.add(whitelist.getString(i));',
                        'model.whitelistedApps.add(PackageNameValidator.requireValid(whitelist.getString(i)));')
    text = text.replace('model.blacklistedApps.add(blacklist.getString(i));',
                        'model.blacklistedApps.add(PackageNameValidator.requireValid(blacklist.getString(i)));')
    p.write_text(text)
    print('patched: PresetModel.java')
else:
    print('already applied: PresetModel.java')

# AutoKill is a high-value privileged boundary.
p = Path("app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java")
text = p.read_text()
if 'PackageNameValidator.requireValid(packageName)' not in text:
    if 'import com.gree1d.reappzuku.core.' not in text:
        # manager has many imports; insert directly after package declaration.
        text = text.replace('package com.gree1d.reappzuku.manager;\n',
                            'package com.gree1d.reappzuku.manager;\n\nimport com.gree1d.reappzuku.core.PackageNameValidator;\n', 1)
    else:
        text = text.replace('package com.gree1d.reappzuku.manager;\n',
                            'package com.gree1d.reappzuku.manager;\n\nimport com.gree1d.reappzuku.core.PackageNameValidator;\n', 1)
    text = text.replace('''        if (packageName == null || packageName.isEmpty()) {\n            if (onComplete != null) {''',
                        '''        if (!PackageNameValidator.isValid(packageName)) {\n            if (onComplete != null) {''', 1)
    text = text.replace('''    private String buildKillCommand(String packageName) {\n        String cmd =''',
                        '''    private String buildKillCommand(String packageName) {\n        PackageNameValidator.requireValid(packageName);\n        String cmd =''', 1)
    text = text.replace('''    public void killPackageSync(String packageName, String source) {\n        if (packageName == null || packageName.isEmpty()) return;''',
                        '''    public void killPackageSync(String packageName, String source) {\n        if (!PackageNameValidator.isValid(packageName)) return;''', 1)
    p.write_text(text)
    print('patched: AutoKillManager.java')
else:
    print('already applied: AutoKillManager.java')

# Restriction sets are the main path from restored preferences into appops/bucket/deviceidle shell calls.
p = Path("app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java")
text = p.read_text()
marker = 'PackageNameValidator.isValid(packageName) && !packageName.equals(context.getPackageName())'
if marker not in text:
    text = text.replace('package com.gree1d.reappzuku.manager;\n',
                        'package com.gree1d.reappzuku.manager;\n\nimport com.gree1d.reappzuku.core.PackageNameValidator;\n', 1)
    old = '''            if (packageName != null && !packageName.isEmpty() && !packageName.equals(context.getPackageName())) {\n                desiredPackages.add(packageName);\n            }'''
    new = '''            if (PackageNameValidator.isValid(packageName) && !packageName.equals(context.getPackageName())) {\n                desiredPackages.add(packageName);\n            }'''
    if old not in text:
        raise SystemExit('BackgroundAppManager sanitizer anchor missing')
    text = text.replace(old, new, 1)
    p.write_text(text)
    print('patched: BackgroundAppManager.java')
else:
    print('already applied: BackgroundAppManager.java')
