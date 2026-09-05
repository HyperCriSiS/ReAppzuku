package com.gree1d.reappzuku.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;
import com.gree1d.reappzuku.core.shell.IShellCallback;
import com.gree1d.reappzuku.core.shell.IShellService;
import com.gree1d.reappzuku.core.shell.ProcessMemoryInfo;
import com.gree1d.reappzuku.core.shell.ShellExecResult;
import com.gree1d.reappzuku.core.shell.ShizukuUserServiceImpl;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import rikka.shizuku.Shizuku;

public class ShellManager {

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;
    private ShizukuBridge shizuku;

    private volatile Boolean hasRoot = null;

    private Shizuku.OnBinderReceivedListener shizukuBinderReceivedListener;
    private Shizuku.OnBinderDeadListener shizukuBinderDeadListener;

    private static final long SHIZUKU_COMMAND_TIMEOUT_MS = 15_000L;
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 0;

    private volatile IShellService userService;
    private volatile boolean userServiceBinding = false;
    private volatile boolean shizukuPermissionRequestPending = false;
    private volatile boolean shizukuBinderEverSeen = false;
    private volatile boolean shizukuBinderLost = false;

    private static final String USER_SERVICE_TAG = "ReAppzukuShellUserService";

    private static final long USER_SERVICE_BIND_WAIT_MS = 8_000L;

    private volatile CountDownLatch userServiceReadyLatch = new CountDownLatch(1);

    private final Shizuku.OnRequestPermissionResultListener internalShizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST_CODE) {
                    return;
                }
                shizukuPermissionRequestPending = false;
                AppDebugManager.d(Category.CORE,
                        "ShellManager: internal Shizuku permission result=" + grantResult);
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    shizukuBinderLost = false;
                    // Permission and UserService readiness are separate states.
                    // A first-launch grant must explicitly trigger the UserService bind,
                    // because the Binder may have arrived before permission existed.
                    bindUserService();
                }
            };

    private final Shizuku.OnBinderReceivedListener internalBinderReceivedListener = () -> {
        shizukuBinderEverSeen = true;
        shizukuBinderLost = false;
        AppDebugManager.d(Category.CORE, "ShellManager: internal Shizuku binder received");
        try {
            if (shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindUserService();
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
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            AppDebugManager.d(Category.CORE, "ShellManager: UserService connected");
            userService = IShellService.Stub.asInterface(binder);
            shizukuBinderEverSeen = true;
            shizukuBinderLost = false;
            userServiceBinding = false;
            userServiceReadyLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            AppDebugManager.w(Category.CORE, "ShellManager: UserService disconnected");
            userService = null;
            userServiceBinding = false;
            userServiceReadyLatch.countDown();
            userServiceReadyLatch = new CountDownLatch(1);
        }
    };

    public ShellManager(Context context, Handler handler, ExecutorService executor) {
        this(context, handler, executor, new RealShizukuBridge());
    }

    ShellManager(Context context, Handler handler, ExecutorService executor, ShizukuBridge shizuku) {
        if (shizuku == null) throw new IllegalArgumentException("shizuku == null");
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.executor = executor;
        this.shizuku = shizuku;

        // Keep an application-lifetime listener. Activity listeners can be removed
        // while the system permission dialog is in the foreground, so binding the
        // UserService must not depend on an Activity still being started.
        shizuku.addRequestPermissionResultListener(internalShizukuPermissionListener);
        shizuku.addBinderReceivedListenerSticky(internalBinderReceivedListener);
        shizuku.addBinderDeadListener(internalBinderDeadListener);
    }

    private Shizuku.UserServiceArgs buildUserServiceArgs() {
        return new Shizuku.UserServiceArgs(
                new ComponentName(context.getPackageName(), ShizukuUserServiceImpl.class.getName()))
                .daemon(false)
                .processNameSuffix("shell_service")
                .debuggable(false)
                .version(1)
                .tag(USER_SERVICE_TAG);
    }

    private IShellService awaitUserService() {
        IShellService service = userService;
        if (service != null) {
            return service;
        }

        // A granted Shizuku permission does not imply that the UserService is
        // already connected. This is especially important on the first launch:
        // the Shizuku Binder commonly arrives before the user grants this app.
        if (!hasShizukuPermission()) {
            return null;
        }

        bindUserService();
        try {
            boolean connected = userServiceReadyLatch.await(USER_SERVICE_BIND_WAIT_MS, TimeUnit.MILLISECONDS);
            if (!connected && userService == null) {
                // Allow a later caller to retry a bind that never completed.
                userServiceBinding = false;
                AppDebugManager.w(Category.CORE,
                        "ShellManager: timed out waiting for Shizuku UserService bind");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return userService;
    }


    public void bindUserService() {
        if (userService != null) {
            AppDebugManager.d(Category.CORE, "ShellManager: bindUserService: already bound, skipping");
            return;
        }
        try {
            if (!shizuku.pingBinder()) {
                AppDebugManager.d(Category.CORE, "ShellManager: bindUserService: Shizuku binder not available yet");
                return;
            }
            if (shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                AppDebugManager.d(Category.CORE,
                        "ShellManager: bindUserService: waiting for Shizuku permission");
                return;
            }
            synchronized (this) {
                if (userService != null || userServiceBinding) {
                    return;
                }
                if (userServiceReadyLatch.getCount() == 0) {
                    userServiceReadyLatch = new CountDownLatch(1);
                }
                userServiceBinding = true;
            }
            shizuku.bindUserService(buildUserServiceArgs(), userServiceConnection);
        } catch (Exception e) {
            userServiceBinding = false;
            userServiceReadyLatch.countDown();
            AppDebugManager.w(Category.CORE, "ShellManager: bindUserService failed", e);
        }
    }

    public void unbindUserService() {
        IShellService service = userService;
        if (service != null) {
            try {
                service.destroy();
            } catch (Exception e) {
                AppDebugManager.w(Category.CORE, "ShellManager: unbindUserService: remote destroy() failed", e);
            }
        }
        try {
            shizuku.unbindUserService(buildUserServiceArgs(), userServiceConnection, true);
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: unbindUserService failed", e);
        } finally {
            userService = null;
            userServiceBinding = false;
            userServiceReadyLatch = new CountDownLatch(1);
        }
    }

    @SuppressWarnings("deprecation")
    public void setShizukuPermissionListener(Shizuku.OnRequestPermissionResultListener listener) {
        shizuku.addRequestPermissionResultListener(listener);
    }

    @SuppressWarnings("deprecation")
    public void removeShizukuPermissionListener(Shizuku.OnRequestPermissionResultListener listener) {
        if (listener != null) {
            shizuku.removeRequestPermissionResultListener(listener);
        }
    }

    public void setShizukuBinderListeners(Runnable onReceived, Runnable onDead) {
        removeShizukuBinderListeners();

        shizukuBinderReceivedListener = () -> {
            AppDebugManager.d(Category.CORE, "ShellManager: Shizuku binder received");
            if (onReceived != null) {
                handler.post(onReceived);
            }
        };
        shizukuBinderDeadListener = () -> {
            AppDebugManager.w(Category.CORE, "ShellManager: Shizuku binder died");
            if (onDead != null) {
                handler.post(onDead);
            }
        };

        shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener);
        shizuku.addBinderDeadListener(shizukuBinderDeadListener);
    }

    public void removeShizukuBinderListeners() {
        if (shizukuBinderReceivedListener != null) {
            shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener);
            shizukuBinderReceivedListener = null;
        }
        if (shizukuBinderDeadListener != null) {
            shizuku.removeBinderDeadListener(shizukuBinderDeadListener);
            shizukuBinderDeadListener = null;
        }
    }

    public boolean hasRootOnlyMode() {
        return hasRootAccess() && !hasShizukuPermission();
    }

    public boolean hasRootAccess() {
        if (hasRoot == null) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                hasRoot = checkRootAccessBlocking();
            } else {
                AppDebugManager.w(Category.CORE, "ShellManager: hasRootAccess: called on main thread before root check completed, returning false");
                return false;
            }
        }
        return hasRoot;
    }

    public boolean hasShizukuPermission() {
        try {
            return shizuku.pingBinder() && shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: Error checking Shizuku permission", e);
            return false;
        }
    }

    public ShellBackendState getBackendState() {
        boolean binderAvailable = false;
        boolean permissionGranted = false;
        try {
            binderAvailable = shizuku.pingBinder();
            if (binderAvailable) {
                shizukuBinderEverSeen = true;
                shizukuBinderLost = false;
                permissionGranted = shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
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
        if (hasRoot != null && hasRoot) {
            AppDebugManager.d(Category.CORE, "ShellManager: Root access available, skipping Shizuku permission request");
            return;
        }
        try {
            if (!shizuku.pingBinder()) {
                AppDebugManager.d(Category.CORE,
                        "ShellManager: Shizuku binder unavailable; permission request deferred");
                return;
            }
            shizukuBinderEverSeen = true;
            shizukuBinderLost = false;

            if (shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                // Permission may have just been granted while the Activity listener
                // was stopped by the system dialog. Ensure service readiness here.
                bindUserService();
                return;
            }

            synchronized (this) {
                if (shizukuPermissionRequestPending) {
                    AppDebugManager.d(Category.CORE,
                            "ShellManager: Shizuku permission request already pending");
                    return;
                }
                shizukuPermissionRequestPending = true;
            }
            try {
                shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                shizukuPermissionRequestPending = false;
                throw e;
            }
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: Error checking shell permissions", e);
        }
    }

    public boolean hasAnyShellPermission() {
        if (hasShizukuPermission()) {
            AppDebugManager.d(Category.CORE, "ShellManager: hasAnyShellPermission: true (Shizuku)");
            return true;
        }
        boolean result = hasRoot != null && hasRoot;
        AppDebugManager.d(Category.CORE, "ShellManager: hasAnyShellPermission: " + result + " (root)");
        return result;
    }

    public boolean resolveAnyShellPermission() {
        if (hasShizukuPermission()) {
            AppDebugManager.d(Category.CORE, "ShellManager: resolveAnyShellPermission: true (Shizuku)");
            return true;
        }
        boolean result = hasRootAccess();
        AppDebugManager.d(Category.CORE, "ShellManager: resolveAnyShellPermission: " + result + " (root)");
        return result;
    }

    public boolean resolveAnyShellPermissionBlocking() {
        if (hasRoot == null) {
            hasRoot = checkRootAccessBlocking();
        }
        if (hasRoot) {
            AppDebugManager.d(Category.CORE, "ShellManager: resolveAnyShellPermissionBlocking: true (root)");
            return true;
        }
        boolean shizuku = hasShizukuPermission();
        AppDebugManager.d(Category.CORE, "ShellManager: resolveAnyShellPermissionBlocking: " + shizuku + " (Shizuku)");
        return shizuku;
    }

    public void runShellCommand(String command, Runnable onSuccess) {
        runShellCommand(command, onSuccess, null);
    }

    public void runShellCommand(String command, Runnable onSuccess, Runnable onFailure) {
        executor.execute(() -> {
            boolean succeeded = runShellCommandBlocking(command);

            if (succeeded) {
                if (onSuccess != null) {
                    handler.post(onSuccess);
                }
            } else if (onFailure != null) {
                handler.post(onFailure);
            }
        });
    }

    public boolean runShellCommandBlocking(String command) {
        return runShellCommandForResult(command).succeeded();
    }

    public ShellResult runShellCommandForResult(String command) {
        ShellResult rootResult = null;
        if (hasRootAccess()) {
            rootResult = executeRootCommandForResult(command);
            if (rootResult.succeeded()) {
                return rootResult;
            }
        }
        if (hasShizukuPermission()) {
            ShellResult shizukuResult = executeShizukuCommandForResult(command);
            if (shizukuResult.succeeded() || rootResult == null) {
                return shizukuResult;
            }
        }
        if (rootResult != null) {
            return rootResult;
        }
        AppDebugManager.w(Category.CORE, "ShellManager: runShellCommandForResult: no Root or Shizuku permission available, command=" + command);
        return new ShellResult(false, -1, "No Root or Shizuku permission available");
    }

    public void runShellCommandWithOutput(String command, Consumer<String> outputProcessor) {
        executor.execute(() -> {
            boolean executed = false;
            if (hasRootAccess()) {
                executed = executeRootCommand(command, outputProcessor);
            }
            if (!executed && hasShizukuPermission()) {
                executed = executeShizukuCommandWithOutput(command, outputProcessor);
            }
        });
    }

    public String runShellCommandAndGetFullOutput(String command) {
        if (hasRootAccess()) {
            return executeRootCommandAndGetFullOutput(command);
        } else if (hasShizukuPermission()) {
            return executeShizukuCommandAndGetFullOutput(command);
        }
        AppDebugManager.w(Category.CORE, "ShellManager: runShellCommandAndGetFullOutput: no Root or Shizuku permission available, command=" + command);
        return null;
    }

    // Reads PSS for the given pids via the UserService's ActivityManager.getProcessMemoryInfo(),
    // instead of shelling out to "dumpsys meminfo" and parsing a text dump of every process
    // in the system. Shizuku-only: the UserService binding is how this call reaches shell/root
    // identity, so root-only mode (su without Shizuku) returns an empty list rather than
    // falling back to a su-spawned equivalent.
    @androidx.annotation.WorkerThread
    public java.util.List<ProcessMemoryInfo> getProcessMemoryInfo(int[] pids) {
        if (pids == null || pids.length == 0) {
            return Collections.emptyList();
        }
        if (!hasShizukuPermission()) {
            AppDebugManager.w(Category.CORE, "ShellManager: getProcessMemoryInfo: Shizuku permission not available");
            return Collections.emptyList();
        }
        IShellService service = awaitUserService();
        if (service == null) {
            AppDebugManager.w(Category.CORE, "ShellManager: getProcessMemoryInfo: UserService not bound");
            return Collections.emptyList();
        }
        try {
            ProcessMemoryInfo[] result = service.getProcessMemoryInfo(pids);
            return result == null ? Collections.emptyList() : java.util.Arrays.asList(result);
        } catch (Exception e) {
            AppDebugManager.e(Category.CORE, "ShellManager: getProcessMemoryInfo failed", e);
            return Collections.emptyList();
        }
    }

    @androidx.annotation.WorkerThread
    @androidx.annotation.Nullable
    public String runCommandAndGetOutput(String command) {
        if (hasRootAccess()) {
            return executeRootCommandAndGetFullOutput(command);
        } else if (hasShizukuPermission()) {
            return executeShizukuCommandAndGetFullOutput(command);
        }
        AppDebugManager.w(Category.CORE, "ShellManager: runCommandAndGetOutput: no Root or Shizuku permission available, command=" + command);
        return null;
    }

    private boolean checkRootAccessBlocking() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id -u\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            process.waitFor();

            return "0".equals(output != null ? output.trim() : "");
        } catch (IOException | InterruptedException e) {
            AppDebugManager.d(Category.CORE, "ShellManager: Root not available: " + e.getMessage());
            return false;
        } finally {
            try {
                if (os != null)
                    os.close();
                if (process != null)
                    process.destroy();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean executeRootCommand(String command, Consumer<String> outputProcessor) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            if (outputProcessor != null) {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(
                                new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = readerInput.readLine()) != null) {
                        final String finalLine = line;
                        handler.post(() -> outputProcessor.accept(finalLine));
                    }
                    while ((line = errorReader.readLine()) != null) {
                        final String finalLine = line;
                        handler.post(() -> outputProcessor.accept("ERROR: " + finalLine));
                    }
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Root command exited with code " + exitCode + ": " + command);
            }
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Root command failed", e);
            return false;
        } finally {
            try {
                if (os != null)
                    os.close();
                if (process != null)
                    process.destroy();
            } catch (IOException ignored) {
            }
        }
    }

    private ShellResult executeRootCommandForResult(String command) {
        Process process = null;
        DataOutputStream os = null;
        StringBuilder output = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = readerInput.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Root command exited with code " + exitCode + ": " + command);
            }
            return new ShellResult(exitCode == 0, exitCode, output.toString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Root command failed", e);
            return new ShellResult(false, -1, e.getMessage());
        } finally {
            try {
                if (os != null)
                    os.close();
                if (process != null)
                    process.destroy();
            } catch (IOException ignored) {
            }
        }
    }

    private String executeRootCommandAndGetFullOutput(String command) {
        Process process = null;
        DataOutputStream os = null;
        StringBuilder output = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();

            try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = readerInput.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }
            process.waitFor();
            return output.toString();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Root command get output failed", e);
            return null;
        } finally {
            try {
                if (os != null)
                    os.close();
                if (process != null)
                    process.destroy();
            } catch (IOException ignored) {
            }
        }
    }

    private boolean executeShizukuCommand(String command) {
        IShellService service = awaitUserService();
        if (service == null) {
            AppDebugManager.w(Category.CORE, "ShellManager: executeShizukuCommand: UserService not bound, command=" + command);
            return false;
        }
        try {
            ShellExecResult result = service.execute(command);
            if (result.exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command exited with code " + result.exitCode + ": " + command);
            }
            return result.succeeded;
        } catch (Exception e) {
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command failed", e);
            return false;
        }
    }

    private boolean executeShizukuCommandWithOutput(String command, Consumer<String> outputProcessor) {
        IShellService service = awaitUserService();
        if (service == null) {
            AppDebugManager.w(Category.CORE, "ShellManager: executeShizukuCommandWithOutput: UserService not bound, command=" + command);
            return false;
        }
        try {
            return executeShizukuCommandWithOutputViaUserService(service, command, outputProcessor);
        } catch (Exception e) {
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command with output failed", e);
            return false;
        }
    }

    private boolean executeShizukuCommandWithOutputViaUserService(IShellService service, String command, Consumer<String> outputProcessor) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final int[] exitCodeHolder = { -1 };
        final boolean[] errorHolder = { false };

        IShellCallback callback = new IShellCallback.Stub() {
            @Override
            public void onLine(String line, boolean isError) {
                handler.post(() -> outputProcessor.accept(isError ? "ERROR: " + line : line));
            }

            @Override
            public void onComplete(int exitCode) {
                exitCodeHolder[0] = exitCode;
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                AppDebugManager.w(Category.CORE, "ShellManager: UserService executeWithCallback reported error: " + message);
                errorHolder[0] = true;
                latch.countDown();
            }
        };

        service.executeWithCallback(command, callback);

        boolean completed = latch.await(SHIZUKU_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!completed) {
            throw new TimeoutException("UserService executeWithCallback timed out after " + SHIZUKU_COMMAND_TIMEOUT_MS + "ms: " + command);
        }
        if (errorHolder[0]) {
            throw new IOException("UserService executeWithCallback reported an error for: " + command);
        }
        int exitCode = exitCodeHolder[0];
        if (exitCode != 0) {
            AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command with output exited with code " + exitCode + ": " + command);
        }
        return exitCode == 0;
    }

    private String executeShizukuCommandAndGetFullOutput(String command) {
        IShellService service = awaitUserService();
        if (service == null) {
            AppDebugManager.w(Category.CORE, "ShellManager: executeShizukuCommandAndGetFullOutput: UserService not bound, command=" + command);
            return null;
        }
        try {
            return service.execute(command).output;
        } catch (Exception e) {
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command get output failed", e);
            return null;
        }
    }

    private ShellResult executeShizukuCommandForResult(String command) {
        IShellService service = awaitUserService();
        if (service == null) {
            AppDebugManager.w(Category.CORE, "ShellManager: executeShizukuCommandForResult: UserService not bound, command=" + command);
            return new ShellResult(false, -1, "Shizuku UserService not bound");
        }
        try {
            ShellExecResult result = service.execute(command);
            if (result.exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command exited with code " + result.exitCode + ": " + command);
            }
            return new ShellResult(result.succeeded, result.exitCode, result.output);
        } catch (Exception e) {
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command failed", e);
            return new ShellResult(false, -1, e.getMessage());
        }
    }

    public static final class ShellResult {
        private final boolean succeeded;
        private final int exitCode;
        private final String output;

        private ShellResult(boolean succeeded, int exitCode, String output) {
            this.succeeded = succeeded;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output.trim();
        }

        public boolean succeeded() {
            return succeeded;
        }

        public int exitCode() {
            return exitCode;
        }

        public String output() {
            return output;
        }
    }
}
