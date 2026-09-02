#!/usr/bin/env python3
from pathlib import Path

p = Path('app/src/main/res/layout/item.xml')
text = p.read_text()
marker = 'android:id="@+id/btn_overflow"'
description = 'android:contentDescription="@string/a11y_app_action"'
start = text.find(marker)
if start < 0:
    raise SystemExit('btn_overflow anchor missing')
end = text.find('/>', start)
if end < 0:
    raise SystemExit('btn_overflow element end missing')
block = text[start:end]
if 'android:contentDescription=' in block:
    print('btn_overflow content description already present')
else:
    anchor = '                android:id="@+id/btn_overflow"\n'
    if text.count(anchor) != 1:
        raise SystemExit('btn_overflow id anchor missing or ambiguous')
    text = text.replace(
        anchor,
        anchor + '                android:contentDescription="@string/a11y_app_action"\n',
        1,
    )
    p.write_text(text)
    print('labeled btn_overflow')
