from pathlib import Path

main = Path("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java")
text = main.read_text()
old = '''                if (sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false)) {\n                    // Explicit user-requested exit from the main screen. Remove the\n                    // task from Recents while leaving Android in control of process\n                    // reclamation and any separately requested background features.\n                    finishAndRemoveTask();\n                    return;\n                }\n'''
new = '''                if (sharedPreferences.getBoolean(KEY_EXIT_ON_BACK, false)) {\n                    // This is deliberately stronger than Android's normal Back action:\n                    // release the Shizuku user service, remove the task, then terminate\n                    // only ReAppzuku's main process. The isolated :shizuku provider\n                    // process remains available for the next on-demand launch.\n                    shellManager.unbindUserService();\n                    finishAndRemoveTask();\n                    android.os.Process.killProcess(android.os.Process.myPid());\n                    return;\n                }\n'''
if new not in text:
    if old not in text:
        raise RuntimeError("Expected exit-on-Back block not found")
    main.write_text(text.replace(old, new, 1))
    print("upgraded Back action to full main-process exit")
else:
    print("full-exit Back action already present")

strings = Path("app/src/main/res/values/strings.xml")
text = strings.read_text()
text2 = text.replace(
    '<string name="settings_exit_on_back_title">Exit app with Back button</string>',
    '<string name="settings_exit_on_back_title">Fully exit app with Back button</string>'
).replace(
    '<string name="settings_exit_on_back_subtitle">On the main screen, Back closes ReAppzuku and removes it from Recents</string>',
    '<string name="settings_exit_on_back_subtitle">On the main screen, Back stops the ReAppzuku main process; background features in that process stop too</string>'
)
if text2 != text:
    strings.write_text(text2)
    print("updated full-exit setting text")
