from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PATH = Path("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java")
TARGET = ROOT / PATH


def restore_if_truncated():
    text = TARGET.read_text(encoding="utf-8") if TARGET.exists() else ""
    if len(text) > 1000 and "__PLACEHOLDER__" not in text:
        return text

    # Recover the nearest intact version from git history. This also repairs a
    # connector-side accidental truncation without embedding a 58 kB source file
    # in this maintenance script.
    for depth in range(1, 10):
        rev = "HEAD" + "^" * depth
        try:
            candidate = subprocess.check_output(
                ["git", "show", f"{rev}:{PATH.as_posix()}"],
                cwd=ROOT,
                text=True,
                stderr=subprocess.DEVNULL,
            )
        except subprocess.CalledProcessError:
            continue
        if len(candidate) > 1000 and "__PLACEHOLDER__" not in candidate:
            TARGET.write_text(candidate, encoding="utf-8")
            print(f"Recovered MainActivity.java from {rev}")
            return candidate
    raise RuntimeError("Could not recover an intact MainActivity.java from recent history")


def replace_once(text, old, new, marker):
    if marker in text:
        return text
    if old not in text:
        raise RuntimeError(f"Expected MainActivity patch anchor not found: {old[:100]!r}")
    return text.replace(old, new, 1)


text = restore_if_truncated()

text = replace_once(
    text,
    '''    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = (requestCode, grantResult) -> {\n        AppDebugManager.d(Category.CORE, "MainActivity: Shizuku permission result=" + grantResult);\n        if (grantResult == PackageManager.PERMISSION_GRANTED) {\n            loadBackgroundApps();\n        }\n    };''',
    '''    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener = (requestCode, grantResult) -> {\n        AppDebugManager.d(Category.CORE, "MainActivity: Shizuku permission result=" + grantResult);\n        if (grantResult == PackageManager.PERMISSION_GRANTED) {\n            // The first app scan must not start until the asynchronous Shizuku\n            // permission dialog has actually been accepted.\n            loadBackgroundApps();\n        } else {\n            // Keep the list untouched when permission is denied. In particular,\n            // do not leave pull-to-refresh spinning after a manual retry.\n            if (binding != null) {\n                binding.swiperefreshlayout1.setRefreshing(false);\n            }\n        }\n    };''',
    "Keep the list untouched when permission is denied",
)

# Second-stage fix: permission grant and Shizuku UserService readiness are
# separate. On first launch the Binder can arrive before permission is granted,
# so the original bind attempt is skipped/failed and must be restarted after the
# user accepts the permission dialog.
if "Permission and UserService connection are separate." not in text:
    old_granted = '''
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // The first app scan must not start until the asynchronous Shizuku
            // permission dialog has actually been accepted.
            loadBackgroundApps();
'''
    new_granted = '''
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // Permission and UserService connection are separate. Explicitly
            // start the bind here; ShellManager also self-heals this centrally
            // when this Activity listener was stopped by the permission dialog.
            shellManager.bindUserService();
            loadBackgroundApps();
'''
    if old_granted in text:
        text = text.replace(old_granted, new_granted, 1)

text = replace_once(
    text,
    '''        executor.execute(() -> {\n            shellManager.resolveAnyShellPermissionBlocking();\n            handler.post(() -> {\n                if (binding == null || isFinishing() || isDestroyed()) return;\n                shellManager.checkShellPermissions();\n                loadBackgroundApps();\n            });\n        });''',
    '''        executor.execute(() -> {\n            boolean hasShellPermission = shellManager.resolveAnyShellPermissionBlocking();\n            handler.post(() -> {\n                if (binding == null || isFinishing() || isDestroyed()) return;\n\n                if (hasShellPermission || shellManager.hasAnyShellPermission()) {\n                    loadBackgroundApps();\n                    return;\n                }\n\n                // Shizuku permission is asynchronous. Request it and stop here;\n                // shizukuPermissionListener continues initialization only after\n                // the user has answered the permission dialog.\n                AppDebugManager.d(Category.CORE,\n                        "MainActivity: waiting for Shizuku permission before first app scan");\n                shellManager.checkShellPermissions();\n            });\n        });''',
    "waiting for Shizuku permission before first app scan",
)

text = replace_once(
    text,
    '''    private void loadBackgroundApps() {\n        if (loadInFlight) {''',
    '''    private void loadBackgroundApps() {\n        // Central guard for every entry point (initial load, swipe-to-refresh,\n        // restriction changes, permission callback). Never start a shell-backed\n        // scan while the Shizuku permission dialog is still pending.\n        if (!shellManager.hasAnyShellPermission()) {\n            AppDebugManager.d(Category.MAIN_PAGE,\n                    "MainActivity: app scan deferred until shell permission is granted");\n            if (binding != null) {\n                binding.swiperefreshlayout1.setRefreshing(false);\n            }\n            shellManager.checkShellPermissions();\n            return;\n        }\n\n        if (loadInFlight) {''',
    "app scan deferred until shell permission is granted",
)

TARGET.write_text(text, encoding="utf-8")
print("Applied Shizuku permission race fix")
