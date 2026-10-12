package com.gree1d.reappzuku.core;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
import java.util.concurrent.atomic.AtomicLong;
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
    // Monotonic invalidation of read-only AppOps observations across
    // Shizuku binder / UserService reconnections, even READY -> READY.
    private final AtomicLong appOpsShizukuEpoch = new AtomicLong();
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

                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    shizukuBinderLost = false;
                    // Permission and UserService readiness are separate states.
                    // A first-launch grant must explicitly trigger the UserService bind,
                    // because the Binder may have arrived before permission existed.
                    bindUserService();
                }
            };

    private final Shizuku.OnBinderReceivedListener internalBinderReceivedListener = () -> {
        appOpsShizukuEpoch.incrementAndGet();
        shizukuBinderEverSeen = true;
        shizukuBinderLost = false;

        try {
            if (shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindUserService();
        } catch (Exception e) {

        }
    };

    private final Shizuku.OnBinderDeadListener internalBinderDeadListener = () -> {
        appOpsShizukuEpoch.incrementAndGet();
        shizukuBinderEverSeen = true;
        shizukuBinderLost = true;
        shizukuPermissionRequestPending = false;
        userService = null;
        userServiceBinding = false;
        userServiceReadyLatch.countDown();
        userServiceReadyLatch = new CountDownLatch(1);

    };

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            appOpsShizukuEpoch.incrementAndGet();
            userService = IShellService.Stub.asInterface(binder);
            shizukuBinderEverSeen = true;
            shizukuBinderLost = false;
            userServiceBinding = false;
            userServiceReadyLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            appOpsShizukuEpoch.incrementAndGet();
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

            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return userService;
    }


    public void bindUserService() {
        if (userService != null) {

            return;
        }
        try {
            if (!shizuku.pingBinder()) {

                return;
            }
            if (shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {

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

        }
    }

    public void unbindUserService() {
        appOpsShizukuEpoch.incrementAndGet();
        IShellService service = userService;
        if (service != null) {
            try {
                service.destroy();
            } catch (Exception e) {

            }
        }
        try {
            shizuku.unbindUserService(buildUserServiceArgs(), userServiceConnection, true);
        } catch (Exception e) {

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

            if (onReceived != null) {
                handler.post(onReceived);
            }
        };
        shizukuBinderDeadListener = () -> {

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

                return false;
            }
        }
        return hasRoot;
    }

    public boolean hasShizukuPermission() {
        try {
            return shizuku.pingBinder() && shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {

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

            return;
        }
        try {
            if (!shizuku.pingBinder()) {

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

        }
    }

    public boolean hasAnyShellPermission() {
        if (hasShizukuPermission()) {

            return true;
        }
        boolean result = hasRoot != null && hasRoot;

        return result;
    }

    public boolean resolveAnyShellPermission() {
        if (hasShizukuPermission()) {

            return true;
        }
        boolean result = hasRootAccess();

        return result;
    }

    public boolean resolveAnyShellPermissionBlocking() {
        if (hasRoot == null) {
            hasRoot = checkRootAccessBlocking();
        }
        if (hasRoot) {

            return true;
        }
        boolean shizuku = hasShizukuPermission();

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

        return new ShellResult(false, -1, "No Root or Shizuku permission available");
    }


    /**
     * An unforgeable per-ShellManager read-only AppOps session pin.
     *
     * Root mode is one ready-backend choice, NOT a persistent su session;
     * every Root command still needs a successful checked exit. Shizuku pins
     * the exact already-bound UserService AND a monotonic lifecycle epoch.
     */
    public static final class AppOpsReadOnlySession {
        private final ShellBackendState backend;
        private final IShellService shizukuService;
        private final long shizukuEpoch;

        private AppOpsReadOnlySession(ShellBackendState backend,
                IShellService shizukuService, long shizukuEpoch) {
            this.backend = backend;
            this.shizukuService = shizukuService;
            this.shizukuEpoch = shizukuEpoch;
        }

        public ShellBackendState backend() { return backend; }
    }

    /** Captures an existing ready connection only; NEVER binds or requests permission. */
    @androidx.annotation.WorkerThread
    public AppOpsReadOnlySession captureReadOnlyAppOpsSession() {
        ShellBackendState backend = getBackendState();
        if (backend == ShellBackendState.ROOT_READY) {
            return getBackendState() == backend
                    ? new AppOpsReadOnlySession(backend, null, -1L) : null;
        }
        if (backend != ShellBackendState.SHIZUKU_READY) return null;
        long epoch = appOpsShizukuEpoch.get();
        IShellService service = userService;
        if (service == null || service.asBinder() == null
                || !service.asBinder().isBinderAlive()
                || getBackendState() != backend
                || appOpsShizukuEpoch.get() != epoch || userService != service) {
            return null;
        }
        return new AppOpsReadOnlySession(backend, service, epoch);
    }

    /**
     * READY alone is insufficient: a fast disconnect/reconnect may return the
     * same enum while the Shizuku execution authority has been replaced.
     */
    public boolean isReadOnlyAppOpsSessionCurrent(AppOpsReadOnlySession pin) {
        if (pin == null || getBackendState() != pin.backend) return false;
        if (pin.backend == ShellBackendState.ROOT_READY) return hasRootAccess();
        if (pin.backend != ShellBackendState.SHIZUKU_READY) return false;
        IShellService current = userService;
        return current != null && current == pin.shizukuService
                && appOpsShizukuEpoch.get() == pin.shizukuEpoch
                && current.asBinder() != null && current.asBinder().isBinderAlive()
                && hasShizukuPermission();
    }

    /**
     * Fail-closed read-only executor, NEVER using the generic Root -> Shizuku
     * fallback. Execute against the exact pre-pinned UserService; do not call
     * awaitUserService() because it can bind a replacement under the same READY
     * state. Every result is rejected if the session is no longer current.
     */
    @androidx.annotation.WorkerThread
    public ShellResult runPinnedReadOnlyAppOpsGet(
            String command, AppOpsReadOnlySession pin) {
        if (!isReadOnlyAppOpsCommand(command)
                || !isReadOnlyAppOpsSessionCurrent(pin)) {
            return new ShellResult(false, -1, "Pinned read-only AppOps session unavailable");
        }
        ShellResult result;
        if (pin.backend == ShellBackendState.ROOT_READY) {
            result = executeRootCommandForResult(command);
        } else {
            try {
                ShellExecResult execution = pin.shizukuService.execute(command);
                result = execution == null
                        ? new ShellResult(false, -1, "Pinned Shizuku result unavailable")
                        : new ShellResult(execution.succeeded, execution.exitCode, execution.output);
            } catch (Exception error) {
                result = new ShellResult(false, -1, "Pinned Shizuku read failed");
            }
        }
        if (!isReadOnlyAppOpsSessionCurrent(pin)) {
            return new ShellResult(false, -1, "Pinned read-only AppOps session changed");
        }
        return result;
    }

    /**
     * Strictly a single AppOps GET command, with no redirection, shell
     * expansion, separators, additional flags, or arbitrary shell payload.
     * This method is deliberately more restrictive than the normal executor.
     */
    public static boolean isReadOnlyAppOpsCommand(String command) {
        if (command == null || command.length() > 340) return false;
        String[] parts = command.split(" ", -1);
        if (parts.length != 7
                || !"cmd".equals(parts[0]) || !"appops".equals(parts[1])
                || !"get".equals(parts[2]) || !"--user".equals(parts[3])
                || !parts[4].matches("0|[1-9][0-9]{0,4}")
                || !parts[6].matches("[A-Z][A-Z0-9_]{0,63}")) {
            return false;
        }
        if (com.gree1d.reappzuku.core.PackageNameValidator.isValid(parts[5])) {
            return true;
        }
        if (!parts[5].matches("[1-9][0-9]{0,9}")) return false;
        try {
            return Long.parseLong(parts[5]) <= Integer.MAX_VALUE;
        } catch (NumberFormatException invalid) {
            return false;
        }
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

            return Collections.emptyList();
        }
        IShellService service = awaitUserService();
        if (service == null) {

            return Collections.emptyList();
        }
        try {
            ProcessMemoryInfo[] result = service.getProcessMemoryInfo(pids);
            return result == null ? Collections.emptyList() : java.util.Arrays.asList(result);
        } catch (Exception e) {

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

        return null;
    }

    private Process startRootShell() throws IOException {
    IOException lastFailure = null;
    try { return Runtime.getRuntime().exec("/system/bin/su"); } catch (IOException e) { lastFailure = e; }
    try { return Runtime.getRuntime().exec("/debug_ramdisk/su"); } catch (IOException e) { lastFailure = e; }
    try { return Runtime.getRuntime().exec("/sbin/su"); } catch (IOException e) { lastFailure = e; }
    try { return Runtime.getRuntime().exec("/system/xbin/su"); } catch (IOException e) { lastFailure = e; }
    try { return Runtime.getRuntime().exec("/system/sbin/su"); } catch (IOException e) { lastFailure = e; }
    try { return Runtime.getRuntime().exec("/su/bin/su"); } catch (IOException e) { lastFailure = e; }
    try { return Runtime.getRuntime().exec("/su/xbin/su"); } catch (IOException e) { lastFailure = e; }
    try { return Runtime.getRuntime().exec("/magisk/.core/bin/su"); } catch (IOException e) { lastFailure = e; }
    throw lastFailure != null ? lastFailure : new IOException("No supported root shell path is available");
}

    private boolean checkRootAccessBlocking() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = startRootShell();
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
            process = startRootShell();
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
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

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
            process = startRootShell();
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
            return new ShellResult(exitCode == 0, exitCode, output.toString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

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
            process = startRootShell();
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

            return false;
        }
        try {
            ShellExecResult result = service.execute(command);
            return result.succeeded;
        } catch (Exception e) {

            return false;
        }
    }

    private boolean executeShizukuCommandWithOutput(String command, Consumer<String> outputProcessor) {
        IShellService service = awaitUserService();
        if (service == null) {

            return false;
        }
        try {
            return executeShizukuCommandWithOutputViaUserService(service, command, outputProcessor);
        } catch (Exception e) {

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
        return exitCode == 0;
    }

    private String executeShizukuCommandAndGetFullOutput(String command) {
        IShellService service = awaitUserService();
        if (service == null) {

            return null;
        }
        try {
            return service.execute(command).output;
        } catch (Exception e) {

            return null;
        }
    }

    private ShellResult executeShizukuCommandForResult(String command) {
        IShellService service = awaitUserService();
        if (service == null) {

            return new ShellResult(false, -1, "Shizuku UserService not bound");
        }
        try {
            ShellExecResult result = service.execute(command);
            return new ShellResult(result.succeeded, result.exitCode, result.output);
        } catch (Exception e) {

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
