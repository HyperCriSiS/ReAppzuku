#!/usr/bin/env python3
from pathlib import Path
P = Path("app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java")

def r(old, new):
    text = P.read_text()
    count = text.count(old)
    if count == 0 and new in text:
        return
    if count != 1:
        raise RuntimeError(f"BackgroundAppManager anchor count={count}")
    P.write_text(text.replace(old, new, 1))

r("import com.gree1d.reappzuku.core.ShellManager;\n",
  "import com.gree1d.reappzuku.core.ShellManager;\nimport com.gree1d.reappzuku.core.ShellBackendState;\n")
r("""        return supportsBackgroundRestriction() && shellManager.hasAnyShellPermission();
""", """        return supportsBackgroundRestriction() && shellManager.isAnyShellReady();
""")
r("""            if (!shellManager.hasAnyShellPermission()) {
                if (onFullList != null) {
                    handler.post(() -> {
                        currentAppsList.clear();
                        onFullList.accept(new ArrayList<>());
                    });
                }
                return;
            }
""", """            if (!shellManager.isAnyShellReady()) {
                ShellBackendState state = shellManager.getBackendState();
                AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME
                        + ": main-screen scan deferred; shell backend state=" + state);
                if (onFullList != null) {
                    handler.post(() -> onFullList.accept(new ArrayList<>(currentAppsList)));
                }
                return;
            }
""")
r("""                    } else {
                        AppDebugManager.w(Category.BACKGROUND_RESTRICTIONS, FILE_NAME + ": loadBackgroundAppsForMainScreen failed to get running apps, ps output is null");
                        handler.post(() -> Toast
                                .makeText(context, context.getString(R.string.toast_failed_get_running_apps), Toast.LENGTH_SHORT).show());
                    }
""", """                    } else {
                        ShellBackendState state = shellManager.getBackendState();
                        if (!state.isReady()) {
                            AppDebugManager.d(Category.BACKGROUND_RESTRICTIONS, FILE_NAME
                                    + ": running-app output unavailable because shell backend is not ready, state=" + state);
                        } else {
                            AppDebugManager.w(Category.BACKGROUND_RESTRICTIONS, FILE_NAME
                                    + ": loadBackgroundAppsForMainScreen failed to get running apps while backend was ready");
                            handler.post(() -> Toast.makeText(context,
                                    context.getString(R.string.toast_failed_get_running_apps),
                                    Toast.LENGTH_SHORT).show());
                        }
                    }
""")
r("""    public void updateRunningState(List<AppModel> apps, Runnable onComplete) {
        if (!shellManager.hasAnyShellPermission()) {
""", """    public void updateRunningState(List<AppModel> apps, Runnable onComplete) {
        if (!shellManager.isAnyShellReady()) {
""")
print("BackgroundAppManager phase 2 patch applied")
