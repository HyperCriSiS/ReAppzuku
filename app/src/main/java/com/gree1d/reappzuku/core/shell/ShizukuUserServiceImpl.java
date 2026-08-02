package com.gree1d.reappzuku.core.shell;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Callable;
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
 *
 * Output reading and process wait run as two INDEPENDENT tasks with a shared
 * deadline, not sequentially. This matters because BufferedReader#readLine() is
 * blocking I/O that does not respond to Thread#interrupt(): if a command leaves
 * a grandchild process holding stdout/stderr open (a real pattern on some
 * HyperOS/MIUI builds, e.g. commands that spawn detached children), waiting for
 * read-EOF before ever checking process exit status can stall the whole call
 * even though the process itself has already finished. Waiting on process exit
 * and read-completion independently, then forcibly destroying the process the
 * moment either times out, bounds worst-case latency and guarantees the pipes
 * get closed so any stuck reader unblocks instead of leaking forever.
 *
 * The executor is a bounded fixed pool rather than a cached pool. Multiple
 * callers (watchdog, auto-kill, UI) can invoke execute()/executeWithCallback()
 * concurrently; a cached pool would let a pile-up of stuck readers grow the
 * thread count without limit. A fixed pool makes exhaustion visible as queueing
 * delay instead of unbounded thread/fd growth that can eventually take down
 * this whole UserService process for every caller at once.
 */
public class ShizukuUserServiceImpl extends IShellService.Stub {

    private static final long COMMAND_TIMEOUT_MS = 15_000L;
    private static final long READ_DRAIN_GRACE_MS = 2_000L;
    private static final int WATCHDOG_POOL_SIZE = 8;

    private final ExecutorService watchdog = Executors.newFixedThreadPool(WATCHDOG_POOL_SIZE);

    public ShizukuUserServiceImpl() {
    }

    @Override
    public ShellExecResult execute(String command) {
        Process process = null;
        StringBuilder output = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec(new String[] { "sh", "-c", command });
            final Process finalProcess = process;

            Future<Void> readFuture = watchdog.submit(() -> {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                    readBothStreamsInto(readerInput, errorReader, output);
                }
                return null;
            });
            Future<Integer> waitFuture = watchdog.submit((Callable<Integer>) finalProcess::waitFor);

            int exitCode = awaitProcessExit(finalProcess, waitFuture, readFuture, command);
            awaitReadDrain(finalProcess, readFuture, command);

            return new ShellExecResult(exitCode == 0, exitCode, output.toString());
        } catch (Exception e) {
            return new ShellExecResult(false, -1, "UserService execute failed: " + e.getMessage());
        } finally {
            closeStreamsQuietly(process);
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    @Override
    public void executeWithCallback(String command, IShellCallback callback) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[] { "sh", "-c", command });
            final Process finalProcess = process;

            Future<Void> readFuture = watchdog.submit(() -> {
                try (BufferedReader readerInput = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()));
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                    String line;
                    while ((line = readerInput.readLine()) != null) {
                        final String finalLine = line;
                        safeCallback(callback, cb -> cb.onLine(finalLine, false));
                    }
                    while ((line = errorReader.readLine()) != null) {
                        final String finalLine = line;
                        safeCallback(callback, cb -> cb.onLine(finalLine, true));
                    }
                }
                return null;
            });
            Future<Integer> waitFuture = watchdog.submit((Callable<Integer>) finalProcess::waitFor);

            int exitCode = awaitProcessExit(finalProcess, waitFuture, readFuture, command);
            awaitReadDrain(finalProcess, readFuture, command);

            safeCallback(callback, cb -> cb.onComplete(exitCode));
        } catch (Exception e) {
            safeCallback(callback, cb -> cb.onError("UserService executeWithCallback failed: " + e.getMessage()));
        } finally {
            closeStreamsQuietly(process);
            if (process != null) {
                process.destroyForcibly();
            }
        }
    }

    @Override
    public void destroy() {
        watchdog.shutdownNow();
        System.exit(0);
    }

    private int awaitProcessExit(Process process, Future<Integer> waitFuture, Future<Void> readFuture, String command) throws Exception {
        try {
            return waitFuture.get(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            process.destroyForcibly();
            waitFuture.cancel(true);
            readFuture.cancel(true);
            throw new TimeoutException("Process did not exit within " + COMMAND_TIMEOUT_MS + "ms: " + command);
        }
    }

    private void awaitReadDrain(Process process, Future<Void> readFuture, String command) {
        try {
            readFuture.get(READ_DRAIN_GRACE_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            process.destroyForcibly();
            readFuture.cancel(true);
        } catch (Exception ignored) {
        }
    }

    private void readBothStreamsInto(BufferedReader readerInput, BufferedReader errorReader, StringBuilder output) throws Exception {
        String line;
        while ((line = readerInput.readLine()) != null) {
            output.append(line).append("\n");
        }
        while ((line = errorReader.readLine()) != null) {
            output.append("ERROR: ").append(line).append("\n");
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
