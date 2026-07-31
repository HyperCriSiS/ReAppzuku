package com.gree1d.reappzuku.core.shell;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs inside the long-lived Shizuku UserService process (root or shell identity,
 * depending on how the user granted permission). This class is instantiated by
 * Shizuku itself via reflection, so it MUST have a public no-argument constructor
 * (Shizuku 12.1.0 does not support the Context-parameter constructor added in v13).
 *
 * Every command runs as a plain "sh -c <command>" child process of THIS process,
 * which already has the elevated identity — there is no per-command binder/AIDL
 * proxy involved beyond the single, already-established UserService connection.
 */
public class ShizukuUserServiceImpl extends IShellService.Stub {

    private static final long COMMAND_TIMEOUT_MS = 15_000L;

    private final ExecutorService watchdog = Executors.newCachedThreadPool();

    public ShizukuUserServiceImpl() {
    }

    @Override
    public ShellExecResult execute(String command) {
        Process process = null;
        DataOutputStream os = null;
        StringBuilder output = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec(new String[] { "sh", "-c", command });
            final Process finalProcess = process;

            runWithTimeout(() -> {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                    String line;
                    while ((line = readerInput.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    while ((line = errorReader.readLine()) != null) {
                        output.append("ERROR: ").append(line).append("\n");
                    }
                }
                return null;
            }, "execute-read");

            int exitCode = runWithTimeout(finalProcess::waitFor, "execute-waitFor");
            return new ShellExecResult(exitCode == 0, exitCode, output.toString());
        } catch (Exception e) {
            return new ShellExecResult(false, -1, "UserService execute failed: " + e.getMessage());
        } finally {
            closeStreamsQuietly(process);
            if (process != null) {
                process.destroy();
            }
        }
    }

    @Override
    public void executeWithCallback(String command, IShellCallback callback) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] { "sh", "-c", command });
            final Process finalProcess = process;

            runWithTimeout(() -> {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                    String line;
                    while ((line = readerInput.readLine()) != null) {
                        safeCallback(callback, cb -> cb.onLine(line, false));
                    }
                    while ((line = errorReader.readLine()) != null) {
                        safeCallback(callback, cb -> cb.onLine(line, true));
                    }
                }
                return null;
            }, "executeWithCallback-read");

            int exitCode = runWithTimeout(finalProcess::waitFor, "executeWithCallback-waitFor");
            safeCallback(callback, cb -> cb.onComplete(exitCode));
        } catch (Exception e) {
            safeCallback(callback, cb -> cb.onError("UserService executeWithCallback failed: " + e.getMessage()));
        } finally {
            closeStreamsQuietly(process);
            if (process != null) {
                process.destroy();
            }
        }
    }

    @Override
    public void destroy() {
        watchdog.shutdownNow();
        System.exit(0);
    }

    private <T> T runWithTimeout(Callable<T> blockingCall, String stepLabel) throws Exception {
        Future<T> future = watchdog.submit(blockingCall);
        try {
            return future.get(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
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

    private void closeStreamsQuietly(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.getOutputStream().close();
        } catch (Exception ignored) {
        }
        try {
            process.getInputStream().close();
        } catch (Exception ignored) {
        }
        try {
            process.getErrorStream().close();
        } catch (Exception ignored) {
        }
    }

    private interface CallbackAction {
        void run(IShellCallback callback) throws Exception;
    }

    private void safeCallback(IShellCallback callback, CallbackAction action) {
        if (callback == null) {
            return;
        }
        try {
            action.run(callback);
        } catch (Exception ignored) {
        }
    }
}
