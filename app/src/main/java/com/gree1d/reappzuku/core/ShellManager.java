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
import com.gree1d.reappzuku.core.shell.ShellExecResult;
import com.gree1d.reappzuku.core.shell.ShizukuUserServiceImpl;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
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

    private volatile Boolean hasRoot = null;

    @SuppressWarnings("deprecation")
    private Shizuku.OnRequestPermissionResultListener shizukuPermissionListener;

    private Shizuku.OnBinderReceivedListener shizukuBinderReceivedListener;
    private Shizuku.OnBinderDeadListener shizukuBinderDeadListener;

    private static final long SHIZUKU_COMMAND_TIMEOUT_MS = 15_000L;

    private volatile IShellService userService;

    private static final String USER_SERVICE_TAG = "ReAppzukuShellUserService";

    private static final long USER_SERVICE_BIND_WAIT_MS = 3_000L;

    private volatile CountDownLatch userServiceReadyLatch = new CountDownLatch(1);

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            AppDebugManager.d(Category.CORE, "ShellManager: UserService connected");
            userService = IShellService.Stub.asInterface(binder);
            userServiceReadyLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            AppDebugManager.w(Category.CORE, "ShellManager: UserService disconnected");
            userService = null;
            userServiceReadyLatch = new CountDownLatch(1);
        }
    };

    public ShellManager(Context context, Handler handler, ExecutorService executor) {
        this.context = context.getApplicationContext();
        this.handler = handler;
        this.executor = executor;
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
        try {
            userServiceReadyLatch.await(USER_SERVICE_BIND_WAIT_MS, TimeUnit.MILLISECONDS);
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
            if (!Shizuku.pingBinder()) {
                AppDebugManager.d(Category.CORE, "ShellManager: bindUserService: Shizuku binder not available yet");
                return;
            }
            if (userServiceReadyLatch.getCount() == 0) {
                userServiceReadyLatch = new CountDownLatch(1);
            }
            Shizuku.bindUserService(buildUserServiceArgs(), userServiceConnection);
        } catch (Exception e) {
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
            Shizuku.unbindUserService(buildUserServiceArgs(), userServiceConnection, true);
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: unbindUserService failed", e);
        } finally {
            userService = null;
        }
    }

    public void setShizukuPermissionListener(Shizuku.OnRequestPermissionResultListener listener) {
        this.shizukuPermissionListener = listener;
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
    }

    public void removeShizukuPermissionListener() {
        if (shizukuPermissionListener != null) {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
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

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener);
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener);
    }

    public void removeShizukuBinderListeners() {
        if (shizukuBinderReceivedListener != null) {
            Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener);
            shizukuBinderReceivedListener = null;
        }
        if (shizukuBinderDeadListener != null) {
            Shizuku.removeBinderDeadListener(shizukuBinderDeadListener);
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
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: Error checking Shizuku permission", e);
            return false;
        }
    }

    public void checkShellPermissions() {
        if (hasRoot != null && hasRoot) {
            AppDebugManager.d(Category.CORE, "ShellManager: Root access available, skipping Shizuku permission request");
            return;
        }
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(0);
                }
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
