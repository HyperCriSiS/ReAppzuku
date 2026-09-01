#!/usr/bin/env python3
from pathlib import Path
p = Path("app/src/main/java/com/gree1d/reappzuku/manager/RamKillShortcutManager.java")
s = p.read_text()
old = '''    private int resolveBackgroundColor() {
        return ContextCompat.getColor(context, R.color.primary);
    }

    private int resolveTextColor() {
        return Color.WHITE;
    }
'''
new = '''    private int resolveBackgroundColor() {
        int nightMode = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ? Color.BLACK : Color.WHITE;
    }

    private int resolveTextColor() {
        int nightMode = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES ? Color.WHITE : Color.BLACK;
    }
'''
if new in s:
    print("Shortcut color fix already applied")
elif old in s:
    p.write_text(s.replace(old, new, 1))
    print("Shortcut color regression fixed")
else:
    raise SystemExit("Expected shortcut color block not found")
