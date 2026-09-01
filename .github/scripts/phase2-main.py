#!/usr/bin/env python3
from pathlib import Path
P = Path("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java")

def r(old, new):
    text = P.read_text()
    count = text.count(old)
    if count == 0 and new in text:
        return
    if count != 1:
        raise RuntimeError(f"MainActivity anchor count={count}")
    P.write_text(text.replace(old, new, 1))

r("import com.gree1d.reappzuku.core.ShellManager;\n",
  "import com.gree1d.reappzuku.core.ShellManager;\nimport com.gree1d.reappzuku.core.ShellBackendState;\n")
r("""    private MenuItem selectAllMenuItem;
    private volatile boolean loadInFlight = false;
""", """    private MenuItem selectAllMenuItem;
    private volatile boolean loadInFlight = false;
    private volatile boolean shellPreparationInFlight = false;
""")
r("""        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // Permission and UserService connection are separate. Explicitly
            // start the bind here; ShellManager also self-heals this centrally
            // when this Activity listener was stopped by the permission dialog.
            shellManager.bindUserService();
            loadBackgroundApps();
""", """        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // Permission is not readiness. Wait for the UserService connection
            // before any shell-backed app scan starts.
            prepareShellAndLoadApps();
""")
r("""        executor.execute(() -> {
            boolean hasShellPermission = shellManager.resolveAnyShellPermissionBlocking();
            handler.post(() -> {
                if (binding == null || isFinishing() || isDestroyed()) return;

                if (hasShellPermission || shellManager.hasAnyShellPermission()) {
                    loadBackgroundApps();
                    return;
                }

                // Shizuku permission is asynchronous. Request it and stop here;
                // shizukuPermissionListener continues initialization only after
                // the user has answered the permission dialog.
                AppDebugManager.d(Category.CORE,
                        "MainActivity: waiting for Shizuku permission before first app scan");
                shellManager.checkShellPermissions();
            });
        });
""", """        prepareShellAndLoadApps();
""")
r("""    private void loadBackgroundApps() {
        // Central guard for every entry point (initial load, swipe-to-refresh,
        // restriction changes, permission callback). Never start a shell-backed
        // scan while the Shizuku permission dialog is still pending.
        if (!shellManager.hasAnyShellPermission()) {
            AppDebugManager.d(Category.MAIN_PAGE,
                    "MainActivity: app scan deferred until shell permission is granted");
            if (binding != null) {
                binding.swiperefreshlayout1.setRefreshing(false);
            }
            shellManager.checkShellPermissions();
            return;
        }
""", """    private void prepareShellAndLoadApps() {
        if (shellPreparationInFlight) {
            AppDebugManager.d(Category.CORE, "MainActivity: shell preparation already in flight");
            return;
        }
        shellPreparationInFlight = true;
        shellManager.prepareShellBackendAsync(state -> {
            shellPreparationInFlight = false;
            if (binding == null || isFinishing() || isDestroyed()) return;
            AppDebugManager.d(Category.CORE, "MainActivity: shell backend state=" + state);
            if (state.isReady()) {
                loadBackgroundApps();
                return;
            }
            binding.swiperefreshlayout1.setRefreshing(false);
            if (state.needsPermissionRequest()) {
                AppDebugManager.d(Category.CORE,
                        "MainActivity: waiting for Shizuku permission before app scan");
                shellManager.checkShellPermissions();
                return;
            }
            if (state.isWaiting()) {
                handler.postDelayed(this::prepareShellAndLoadApps, 500L);
            } else {
                AppDebugManager.w(Category.CORE,
                        "MainActivity: shell backend unavailable; app scan deferred, state=" + state);
            }
        });
    }

    private void loadBackgroundApps() {
        // Permission is necessary but not sufficient for Shizuku. Every scan is
        // gated on an actually executable backend.
        if (!shellManager.isAnyShellReady()) {
            ShellBackendState state = shellManager.getBackendState();
            AppDebugManager.d(Category.MAIN_PAGE,
                    "MainActivity: app scan deferred until shell backend is ready, state=" + state);
            if (binding != null) binding.swiperefreshlayout1.setRefreshing(false);
            prepareShellAndLoadApps();
            return;
        }
""")
print("MainActivity phase 2 patch applied")
