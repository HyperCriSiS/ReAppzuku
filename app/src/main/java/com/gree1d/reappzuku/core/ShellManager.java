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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;


public class ShellManager {

    private final Context context;
    private final Handler handler;
    private final ExecutorService executor;

    private volatile Boolean hasRoot = null;

    @SuppressWarnings("deprecation")
    private Shizuku.OnRequestPermissionResultListener shizukuPermissionListener;

    private Shizuku.OnBinderReceivedListener shizukuBinderReceivedListener;
    private Shizuku.OnBinderDeadListener shizukuBinderDeadListener;

    private volatile ShizukuRemoteProcess currentRemoteProcess;

    private static final long SHIZUKU_COMMAND_TIMEOUT_MS = 15_000L;

    private final ExecutorService shizukuWatchdogExecutor = Executors.newCachedThreadPool();

    private volatile IShellService userService;

    private static final String USER_SERVICE_TAG = "ReAppzukuShellUserService";

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            AppDebugManager.d(Category.CORE, "ShellManager: UserService connected");
            userService = IShellService.Stub.asInterface(binder);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            AppDebugManager.w(Category.CORE, "ShellManager: UserService disconnected");
            userService = null;
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

    public void bindUserService() {
        try {
            if (!Shizuku.pingBinder()) {
                AppDebugManager.d(Category.CORE, "ShellManager: bindUserService: Shizuku binder not available yet, will rely on legacy fallback");
                return;
            }
            Shizuku.bindUserService(buildUserServiceArgs(), userServiceConnection);
        } catch (Exception e) {
            AppDebugManager.w(Category.CORE, "ShellManager: bindUserService failed, will rely on legacy fallback", e);
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

    public void destroyCurrentShizukuProcess() {
        ShizukuRemoteProcess remote = currentRemoteProcess;
        if (remote != null) {
            AppDebugManager.w(Category.CORE, "ShellManager: destroyCurrentShizukuProcess: forcing destroy of in-flight remote process");
            closeShizukuStreamsQuietly(remote);
            try {
                remote.destroy();
            } catch (Exception e) {
                AppDebugManager.e(Category.CORE, "ShellManager: destroyCurrentShizukuProcess: destroy failed", e);
            }
        }
    }

    public void shutdownWatchdog() {
        shizukuWatchdogExecutor.shutdownNow();
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

    private <T> T runWithTimeout(ShizukuRemoteProcess remote, String command, String stepLabel, Callable<T> blockingCall) throws Exception {
        Future<T> future = shizukuWatchdogExecutor.submit(blockingCall);
        try {
            return future.get(SHIZUKU_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            AppDebugManager.e(Category.CORE, "ShellManager: runWithTimeout: " + stepLabel + " timed out after "
                    + SHIZUKU_COMMAND_TIMEOUT_MS + "ms, force-destroying remote process: " + command);
            try {
                remote.destroy();
            } catch (Exception destroyEx) {
                AppDebugManager.e(Category.CORE, "ShellManager: runWithTimeout: destroy after timeout failed", destroyEx);
            }
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    private void closeShizukuStreamsQuietly(ShizukuRemoteProcess remote) {
        if (remote == null) {
            return;
        }
        try {
            remote.getOutputStream().close();
        } catch (Exception ignored) {
        }
        try {
            remote.getInputStream().close();
        } catch (Exception ignored) {
        }
        try {
            remote.getErrorStream().close();
        } catch (Exception ignored) {
        }
    }

    private boolean executeShizukuCommand(String command) {
        IShellService service = userService;
        if (service != null) {
            try {
                ShellExecResult result = service.execute(command);
                if (result.exitCode != 0) {
                    AppDebugManager.w(Category.CORE, "ShellManager: Shizuku (UserService) command exited with code " + result.exitCode + ": " + command);
                }
                return result.succeeded;
            } catch (Exception e) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku (UserService) command failed, falling back to legacy newProcess path", e);
               
            }
        }
        return executeShizukuCommandLegacy(command);
    }

    private boolean executeShizukuCommandLegacy(String command) {
        ShizukuRemoteProcess remote = null;
        try {
            remote = Shizuku.newProcess(new String[] { "sh", "-c", command }, null, "/");
            currentRemoteProcess = remote;
            final ShizukuRemoteProcess finalRemote = remote;
            runWithTimeout(remote, command, "read", (Callable<Void>) () -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalRemote.getInputStream()))) {
                    while (reader.readLine() != null) {
                    }
                }
                return null;
            });

            int exitCode = runWithTimeout(remote, command, "waitFor", remote::waitFor);
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command exited with code " + exitCode + ": " + command);
            }
            return exitCode == 0;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command failed", e);
            return false;
        } finally {
            closeShizukuStreamsQuietly(remote);
            if (remote != null) {
                remote.destroy();
            }
            currentRemoteProcess = null;
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

    private boolean executeShizukuCommandWithOutput(String command, Consumer<String> outputProcessor) {
        IShellService service = userService;
        if (service != null) {
            try {
                return executeShizukuCommandWithOutputViaUserService(service, command, outputProcessor);
            } catch (Exception e) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku (UserService) command with output failed, falling back to legacy newProcess path", e);
                
            }
        }
        return executeShizukuCommandWithOutputLegacy(command, outputProcessor);
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
            AppDebugManager.w(Category.CORE, "ShellManager: Shizuku (UserService) command with output exited with code " + exitCode + ": " + command);
        }
        return exitCode == 0;
    }

    private boolean executeShizukuCommandWithOutputLegacy(String command, Consumer<String> outputProcessor) {
        ShizukuRemoteProcess remote = null;
        try {
            remote = Shizuku.newProcess(new String[] { "sh", "-c", command }, null, "/");
            currentRemoteProcess = remote;
            final ShizukuRemoteProcess finalRemote = remote;
            runWithTimeout(remote, command, "read", (Callable<Void>) () -> {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(finalRemote.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(finalRemote.getErrorStream()))) {
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
                return null;
            });
            int exitCode = runWithTimeout(remote, command, "waitFor", remote::waitFor);
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command with output exited with code " + exitCode + ": " + command);
            }
            return exitCode == 0;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command with output failed", e);
            return false;
        } finally {
            closeShizukuStreamsQuietly(remote);
            if (remote != null) {
                remote.destroy();
            }
            currentRemoteProcess = null;
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

    private String executeShizukuCommandAndGetFullOutput(String command) {
        IShellService service = userService;
        if (service != null) {
            try {
                return service.execute(command).output;
            } catch (Exception e) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku (UserService) get output failed, falling back to legacy newProcess path", e);
                // fall through to legacy path below
            }
        }
        return executeShizukuCommandAndGetFullOutputLegacy(command);
    }

    private String executeShizukuCommandAndGetFullOutputLegacy(String command) {
        ShizukuRemoteProcess remote = null;
        StringBuilder output = new StringBuilder();
        try {
            remote = Shizuku.newProcess(new String[] { "sh", "-c", command }, null, "/");
            currentRemoteProcess = remote;
            final ShizukuRemoteProcess finalRemote = remote;
            final StringBuilder finalOutput = output;
            runWithTimeout(remote, command, "read", (Callable<Void>) () -> {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(finalRemote.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(finalRemote.getErrorStream()))) {
                    String line;
                    while ((line = readerInput.readLine()) != null) {
                        finalOutput.append(line).append("\n");
                    }
                    while ((line = errorReader.readLine()) != null) {
                        finalOutput.append("ERROR: ").append(line).append("\n");
                    }
                }
                return null;
            });
            runWithTimeout(remote, command, "waitFor", remote::waitFor);
            return output.toString();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command get output failed", e);
            return null;
        } finally {
            closeShizukuStreamsQuietly(remote);
            if (remote != null) {
                remote.destroy();
            }
            currentRemoteProcess = null;
        }
    }


    private ShellResult executeShizukuCommandForResult(String command) {
        IShellService service = userService;
        if (service != null) {
            try {
                ShellExecResult result = service.execute(command);
                if (result.exitCode != 0) {
                    AppDebugManager.w(Category.CORE, "ShellManager: Shizuku (UserService) command exited with code " + result.exitCode + ": " + command);
                }
                return new ShellResult(result.succeeded, result.exitCode, result.output);
            } catch (Exception e) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku (UserService) command failed, falling back to legacy newProcess path", e);
                // fall through to legacy path below
            }
        }
        return executeShizukuCommandForResultLegacy(command);
    }

    private ShellResult executeShizukuCommandForResultLegacy(String command) {
        ShizukuRemoteProcess remote = null;
        StringBuilder output = new StringBuilder();
        try {
            remote = Shizuku.newProcess(new String[] { "sh", "-c", command }, null, "/");
            currentRemoteProcess = remote;
            final ShizukuRemoteProcess finalRemote = remote;
            final StringBuilder finalOutput = output;
            runWithTimeout(remote, command, "read", (Callable<Void>) () -> {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(finalRemote.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(finalRemote.getErrorStream()))) {
                    String line;
                    while ((line = readerInput.readLine()) != null) {
                        finalOutput.append(line).append("\n");
                    }
                    while ((line = errorReader.readLine()) != null) {
                        finalOutput.append("ERROR: ").append(line).append("\n");
                    }
                }
                return null;
            });
            int exitCode = runWithTimeout(remote, command, "waitFor", remote::waitFor);
            if (exitCode != 0) {
                AppDebugManager.w(Category.CORE, "ShellManager: Shizuku command exited with code " + exitCode + ": " + command);
            }
            return new ShellResult(exitCode == 0, exitCode, output.toString());
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            AppDebugManager.e(Category.CORE, "ShellManager: Shizuku command failed", e);
            return new ShellResult(false, -1, e.getMessage());
        } finally {
            closeShizukuStreamsQuietly(remote);
            if (remote != null) {
                remote.destroy();
            }
            currentRemoteProcess = null;
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
