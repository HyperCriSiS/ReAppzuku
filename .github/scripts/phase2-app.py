#!/usr/bin/env python3
from pathlib import Path
p = Path("app/src/main/java/com/gree1d/reappzuku/core/App.java")
old = '''        // Handles both an already-running Shizuku instance and a later
        // Shizuku start/restart while ReAppzuku is open.
        Shizuku.addBinderReceivedListenerSticky(shellManager::bindUserService);
'''
new = '''        // ShellManager owns the application-lifetime Binder/permission/UserService
        // state machine and reacts to Shizuku restarts centrally.
'''
text = p.read_text()
if old in text:
    p.write_text(text.replace(old, new, 1))
elif new not in text:
    raise RuntimeError("App readiness anchor not found")
print("App phase 2 patch applied")
