from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "app/src/main/java/com/gree1d/reappzuku/core/ShellManager.java"
MARKER = "ShellManager: internal Shizuku permission result="

if MARKER in TARGET.read_text(encoding="utf-8"):
    print("Shizuku service-readiness fix already applied")
    raise SystemExit(0)

PATCH = r'''
--- a/app/src/main/java/com/gree1d/reappzuku/core/ShellManager.java
+++ b/app/src/main/java/com/gree1d/reappzuku/core/ShellManager.java
@@ -41,20 +41,40 @@
     private Shizuku.OnBinderDeadListener shizukuBinderDeadListener;
 
     private static final long SHIZUKU_COMMAND_TIMEOUT_MS = 15_000L;
+    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 0;
 
     private volatile IShellService userService;
+    private volatile boolean userServiceBinding = false;
+    private volatile boolean shizukuPermissionRequestPending = false;
 
     private static final String USER_SERVICE_TAG = "ReAppzukuShellUserService";
 
-    private static final long USER_SERVICE_BIND_WAIT_MS = 3_000L;
+    private static final long USER_SERVICE_BIND_WAIT_MS = 8_000L;
 
     private volatile CountDownLatch userServiceReadyLatch = new CountDownLatch(1);
 
+    private final Shizuku.OnRequestPermissionResultListener internalShizukuPermissionListener =
+            (requestCode, grantResult) -> {
+                if (requestCode != SHIZUKU_PERMISSION_REQUEST_CODE) {
+                    return;
+                }
+                shizukuPermissionRequestPending = false;
+                AppDebugManager.d(Category.CORE,
+                        "ShellManager: internal Shizuku permission result=" + grantResult);
+                if (grantResult == PackageManager.PERMISSION_GRANTED) {
+                    // Permission and UserService readiness are separate states.
+                    // A first-launch grant must explicitly trigger the UserService bind,
+                    // because the Binder may have arrived before permission existed.
+                    bindUserService();
+                }
+            };
+
     private final ServiceConnection userServiceConnection = new ServiceConnection() {
         @Override
         public void onServiceConnected(ComponentName name, IBinder binder) {
             AppDebugManager.d(Category.CORE, "ShellManager: UserService connected");
             userService = IShellService.Stub.asInterface(binder);
+            userServiceBinding = false;
             userServiceReadyLatch.countDown();
         }
 
@@ -62,6 +82,7 @@
         public void onServiceDisconnected(ComponentName name) {
             AppDebugManager.w(Category.CORE, "ShellManager: UserService disconnected");
             userService = null;
+            userServiceBinding = false;
             userServiceReadyLatch = new CountDownLatch(1);
         }
     };
@@ -70,6 +91,11 @@
         this.context = context.getApplicationContext();
         this.handler = handler;
         this.executor = executor;
+
+        // Keep an application-lifetime listener. Activity listeners can be removed
+        // while the system permission dialog is in the foreground, so binding the
+        // UserService must not depend on an Activity still being started.
+        Shizuku.addRequestPermissionResultListener(internalShizukuPermissionListener);
     }
 
     private Shizuku.UserServiceArgs buildUserServiceArgs() {
@@ -87,8 +113,23 @@
         if (service != null) {
             return service;
         }
+
+        // A granted Shizuku permission does not imply that the UserService is
+        // already connected. This is especially important on the first launch:
+        // the Shizuku Binder commonly arrives before the user grants this app.
+        if (!hasShizukuPermission()) {
+            return null;
+        }
+
+        bindUserService();
         try {
-            userServiceReadyLatch.await(USER_SERVICE_BIND_WAIT_MS, TimeUnit.MILLISECONDS);
+            boolean connected = userServiceReadyLatch.await(USER_SERVICE_BIND_WAIT_MS, TimeUnit.MILLISECONDS);
+            if (!connected && userService == null) {
+                // Allow a later caller to retry a bind that never completed.
+                userServiceBinding = false;
+                AppDebugManager.w(Category.CORE,
+                        "ShellManager: timed out waiting for Shizuku UserService bind");
+            }
         } catch (InterruptedException e) {
             Thread.currentThread().interrupt();
         }
@@ -106,11 +147,23 @@
                 AppDebugManager.d(Category.CORE, "ShellManager: bindUserService: Shizuku binder not available yet");
                 return;
             }
-            if (userServiceReadyLatch.getCount() == 0) {
-                userServiceReadyLatch = new CountDownLatch(1);
+            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
+                AppDebugManager.d(Category.CORE,
+                        "ShellManager: bindUserService: waiting for Shizuku permission");
+                return;
+            }
+            synchronized (this) {
+                if (userService != null || userServiceBinding) {
+                    return;
+                }
+                if (userServiceReadyLatch.getCount() == 0) {
+                    userServiceReadyLatch = new CountDownLatch(1);
+                }
+                userServiceBinding = true;
             }
             Shizuku.bindUserService(buildUserServiceArgs(), userServiceConnection);
         } catch (Exception e) {
+            userServiceBinding = false;
             AppDebugManager.w(Category.CORE, "ShellManager: bindUserService failed", e);
         }
     }
@@ -130,6 +183,8 @@
             AppDebugManager.w(Category.CORE, "ShellManager: unbindUserService failed", e);
         } finally {
             userService = null;
+            userServiceBinding = false;
+            userServiceReadyLatch = new CountDownLatch(1);
         }
     }
 
@@ -207,10 +262,32 @@
             return;
         }
         try {
-            if (Shizuku.pingBinder()) {
-                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
-                    Shizuku.requestPermission(0);
+            if (!Shizuku.pingBinder()) {
+                AppDebugManager.d(Category.CORE,
+                        "ShellManager: Shizuku binder unavailable; permission request deferred");
+                return;
+            }
+
+            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
+                // Permission may have just been granted while the Activity listener
+                // was stopped by the system dialog. Ensure service readiness here.
+                bindUserService();
+                return;
+            }
+
+            synchronized (this) {
+                if (shizukuPermissionRequestPending) {
+                    AppDebugManager.d(Category.CORE,
+                            "ShellManager: Shizuku permission request already pending");
+                    return;
                 }
+                shizukuPermissionRequestPending = true;
+            }
+            try {
+                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
+            } catch (Exception e) {
+                shizukuPermissionRequestPending = false;
+                throw e;
             }
         } catch (Exception e) {
             AppDebugManager.w(Category.CORE, "ShellManager: Error checking shell permissions", e);
'''

result = subprocess.run(
    ["git", "apply", "--whitespace=nowarn", "-"],
    cwd=ROOT,
    input=PATCH,
    text=True,
    capture_output=True,
)
if result.returncode != 0:
    raise RuntimeError("Failed to apply Shizuku service-readiness patch: " + result.stderr)

print("Applied Shizuku permission/UserService readiness fix")
