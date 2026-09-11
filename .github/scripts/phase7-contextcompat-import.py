#!/usr/bin/env python3
from pathlib import Path

p = Path('app/src/main/java/com/gree1d/reappzuku/manager/AdditionalScenariosManager.java')
text = p.read_text()
if 'import androidx.core.content.ContextCompat;' not in text:
    anchor = 'import android.net.wifi.WifiManager;\n'
    if text.count(anchor) != 1:
        raise SystemExit('WifiManager import anchor missing or ambiguous')
    text = text.replace(anchor, anchor + 'import androidx.core.content.ContextCompat;\n', 1)
    p.write_text(text)
    print('added ContextCompat import')
else:
    print('ContextCompat import already present')
