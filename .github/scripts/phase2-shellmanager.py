#!/usr/bin/env python3
from pathlib import Path

P = Path("app/src/main/java/com/gree1d/reappzuku/core/ShellManager.java")

def r(old, new):
    text = P.read_text()
    count = text.count(old)
    if count == 0 and new in text:
        return
    if count != 1:
        raise RuntimeError(f"ShellManager anchor count={count}")
    P.write_text(text.replace(old, new, 1))

r("""    private volatile boolean userServiceBinding = false;
    private volatile boolean shizukuPermissionRequestPending = false;
""", """    private volatile boolean userServiceBinding = false;
    private volatile boolean shizukuPermissionRequestPending = false;
    private volatile boolean shizukuBinderEverSeen = false;
    private volatile boolean shizukuBinderLost = false;
""")
r("""                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    // Permission and UserService readiness are separate states.
""", """                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    shizukuBinderLost = false;
                    // Permission and UserService readiness are separate states.
""")
r("""            };

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
""", """            };

    private final Shizuku.OnBinderReceivedListener internalBinderReceivedListener = () -> {
        shizukuBinderEverSeen = true;
        shizukuBinderLost = false;
        AppDebugManager.d(Category.CORE, "ShellManager: internal Shizuku binder received");
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindUserService();
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: binder readiness check failed", e);
        }
    };

    private final Shizuku.OnBinderDeadListener internalBinderDeadListener = () -> {
        shizukuBinderEverSeen = true;
        shizukuBinderLost = true;
        shizukuPermissionRequestPending = false;
        userService = null;
        userServiceBinding = false;
        userServiceReadyLatch.countDown();
        userServiceReadyLatch = new CountDownLatch(1);
        AppDebugManager.w(Category.CORE, "ShellManager: Shizuku backend lost");
    };

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
""")
r("""            userService = IShellService.Stub.asInterface(binder);
            userServiceBinding = false;
""", """            userService = IShellService.Stub.asInterface(binder);
            shizukuBinderEverSeen = true;
            shizukuBinderLost = false;
            userServiceBinding = false;
""")
r("""        public void onServiceDisconnected(ComponentName name) {
            AppDebugManager.w(Category.CORE, "ShellManager: UserService disconnected");
            userService = null;
            userServiceBinding = false;
            userServiceReadyLatch = new CountDownLatch(1);
        }
""", """        public void onServiceDisconnected(ComponentName name) {
            AppDebugManager.w(Category.CORE, "ShellManager: UserService disconnected");
            userService = null;
            userServiceBinding = false;
            userServiceReadyLatch.countDown();
            userServiceReadyLatch = new CountDownLatch(1);
        }
""")
r("""        Shizuku.addRequestPermissionResultListener(internalShizukuPermissionListener);
""", """        Shizuku.addRequestPermissionResultListener(internalShizukuPermissionListener);
        Shizuku.addBinderReceivedListenerSticky(internalBinderReceivedListener);
        Shizuku.addBinderDeadListener(internalBinderDeadListener);
""")
r("""        } catch (Exception e) {
            userServiceBinding = false;
            AppDebugManager.w(Category.CORE, "ShellManager: bindUserService failed", e);
        }
    }

    public void unbindUserService() {
""", """        } catch (Exception e) {
            userServiceBinding = false;
            userServiceReadyLatch.countDown();
            AppDebugManager.w(Category.CORE, "ShellManager: bindUserService failed", e);
        }
    }

    public void unbindUserService() {
""")
r("""    public void checkShellPermissions() {
""", """    public ShellBackendState getBackendState() {
        boolean binderAvailable = false;
        boolean permissionGranted = false;
        try {
            binderAvailable = Shizuku.pingBinder();
            if (binderAvailable) {
                shizukuBinderEverSeen = true;
                shizukuBinderLost = false;
                permissionGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            }
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: backend probe failed", e);
        }
        return ShellBackendState.resolve(
                Boolean.TRUE.equals(hasRoot), binderAvailable,
                shizukuBinderEverSeen || shizukuBinderLost, permissionGranted,
                shizukuPermissionRequestPending, userServiceBinding, userService != null);
    }

    public boolean isAnyShellReady() {
        return getBackendState().isReady();
    }

    @androidx.annotation.WorkerThread
    public ShellBackendState awaitAnyShellReadyBlocking() {
        if (hasRoot == null) hasRoot = checkRootAccessBlocking();
        if (Boolean.TRUE.equals(hasRoot)) return ShellBackendState.ROOT_READY;
        ShellBackendState state = getBackendState();
        if (state == ShellBackendState.SHIZUKU_GRANTED || state == ShellBackendState.SHIZUKU_BINDING) {
            awaitUserService();
            state = getBackendState();
        }
        return state;
    }

    public void prepareShellBackendAsync(Consumer<ShellBackendState> callback) {
        executor.execute(() -> {
            ShellBackendState state = awaitAnyShellReadyBlocking();
            if (callback != null) handler.post(() -> callback.accept(state));
        });
    }

    public void checkShellPermissions() {
""")
r("""            if (!Shizuku.pingBinder()) {
                AppDebugManager.d(Category.CORE,
                        "ShellManager: Shizuku binder unavailable; permission request deferred");
                return;
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
""", """            if (!Shizuku.pingBinder()) {
                AppDebugManager.d(Category.CORE,
                        "ShellManager: Shizuku binder unavailable; permission request deferred");
                return;
            }
            shizukuBinderEverSeen = true;
            shizukuBinderLost = false;

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
""")
print("ShellManager phase 2 patch applied")
