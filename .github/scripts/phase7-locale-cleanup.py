#!/usr/bin/env python3
from pathlib import Path
import re

LOCALES = [
    'app/src/main/res/values-es/strings.xml',
    'app/src/main/res/values-ru/strings.xml',
    'app/src/main/res/values-uk/strings.xml',
    'app/src/main/res/values-zh-rCN/strings.xml',
]
UNTRANSLATABLE = [
    'app_name',
    'settings_shell_shizuku_ok',
    'settings_shell_root_ok',
    'settings_theme_amoled_short',
]

for path in LOCALES:
    p = Path(path)
    text = p.read_text()
    original = text
    for name in UNTRANSLATABLE:
        text = re.sub(
            r'^\s*<string name="' + re.escape(name) + r'"[^>]*>.*?</string>\s*\n',
            '', text, flags=re.M)
    if text != original:
        p.write_text(text)
        print('removed local copies of non-translatable resources:', path)
    else:
        print('locale cleanup already applied:', path)

p = Path('app/src/main/res/values-es/strings.xml')
text = p.read_text()
if '<item quantity="many">%1$d apps cerradas · %2$s liberados</item>' not in text:
    text = text.replace(
        '<item quantity="other">%1$d apps cerradas · %2$s liberados</item>',
        '<item quantity="many">%1$d apps cerradas · %2$s liberados</item>\n        <item quantity="other">%1$d apps cerradas · %2$s liberados</item>', 1)
if '<item quantity="many">%d apps cerradas</item>' not in text:
    text = text.replace(
        '<item quantity="other">%d apps cerradas</item>',
        '<item quantity="many">%d apps cerradas</item>\n        <item quantity="other">%d apps cerradas</item>', 1)
p.write_text(text)
print('Spanish plural coverage ready')
