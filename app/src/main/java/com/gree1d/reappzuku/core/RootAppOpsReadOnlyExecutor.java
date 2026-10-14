package com.gree1d.reappzuku.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded, non-persistent ROOT-only reader for the pinned AppOps GET diagnostic.
 *
 * This intentionally does NOT replace the general command executor. Standard
 * output and standard error are drained concurrently so a verbose root shell
 * cannot deadlock on the pipe which the caller was not yet reading.
 *
 * The complete operation (including shell startup, stdin writes, drain and
 * exit) has a wall-clock deadline. An overlong response, stderr, failed exit,
 * interrupted thread or timed-out process never becomes AppOps evidence.
 * Neither an AppOps mutation nor a Shizuku fallback exists in this class.
 */
final class RootAppOpsReadOnlyExecutor {
    private RootAppOpsReadOnlyExecutor() {}

    interface Starter {
        Process start() throws IOException;
    }

    static final int MAX_STDOUT_BYTES = 4096;
    static final long DEFAULT_TIMEOUT_MS = 8_000L;

    /**
     * Root readiness is a cached backend observation, NOT proof that a newly
     * launched su process retained euid 0. Verify the effective UID in the
     * exact same shell before AND after the read-only AppOps command. A
     * failure of either UID check returns exit 125; a failed GET returns 126
     * without allowing the postflight test to mask its nonzero exit status.
     * These are bounded observations, not proof of atomic privilege continuity.
     * A missing or broken system id also fails closed; no Shizuku fallback.
     */
    static final String REQUIRE_ROOT_EUID =
            "test \"$(/system/bin/id -u)\" = 0 || exit 125\n";

    static final class Outcome {
        final boolean succeeded;
        final int exitCode;
        final String output;

        private Outcome(boolean succeeded, int exitCode, String output) {
            this.succeeded = succeeded;
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private static Outcome failed() {
        return new Outcome(false, -1, "");
    }

    static Outcome execute(Starter starter, String command) {
        return execute(starter, command, DEFAULT_TIMEOUT_MS);
    }

    static Outcome execute(Starter starter, String command, long timeoutMillis) {
        if (starter == null || !ShellManager.isReadOnlyAppOpsCommand(command)
                || timeoutMillis < 1 || timeoutMillis > 30_000L) {
            return failed();
        }

        AtomicReference<Process> process = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        FutureTask<Outcome> task = new FutureTask<>(
                () -> runProcess(starter, command, process, cancelled));
        Thread worker = daemon("appops-root-observe", task);
        worker.start();
        try {
            return task.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException failure) {
            cancelled.set(true);
            terminate(process.get());
            task.cancel(true);
            return failed();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancelled.set(true);
            terminate(process.get());
            task.cancel(true);
            return failed();
        }
    }

    private static Outcome runProcess(Starter starter, String command,
            AtomicReference<Process> processRef, AtomicBoolean cancelled) {
        Process process = null;
        ExecutorService drains = null;
        try {
            if (cancelled.get()) return failed();
            process = starter.start();
            if (process == null) return failed();
            processRef.set(process);
            if (cancelled.get()) return failed();

            final Process child = process;
            drains = Executors.newFixedThreadPool(2, runnable ->
                    daemon("appops-root-pipe", runnable));
            Future<BoundedRead> stdout = drains.submit(
                    () -> drain(child.getInputStream(), MAX_STDOUT_BYTES));
            Future<BoundedRead> stderr = drains.submit(
                    () -> drain(child.getErrorStream(), 0));

            // stdin writes also run inside the deadline-bound worker.
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write((REQUIRE_ROOT_EUID + command + " || exit 126\n"
                        + REQUIRE_ROOT_EUID + "exit\n")
                        .getBytes(StandardCharsets.US_ASCII));
                stdin.flush();
            }

            int exitCode = process.waitFor();
            BoundedRead out = stdout.get();
            BoundedRead err = stderr.get();
            if (cancelled.get() || exitCode != 0 || out.overflow
                    || out.bytes.length == 0 || err.hadData) {
                return failed();
            }
            return new Outcome(true, 0,
                    new String(out.bytes, StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException | ExecutionException
                | RuntimeException failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return failed();
        } finally {
            if (drains != null) drains.shutdownNow();
            terminate(process);
        }
    }

    private static final class BoundedRead {
        final byte[] bytes;
        final boolean overflow;
        final boolean hadData;

        BoundedRead(byte[] bytes, boolean overflow, boolean hadData) {
            this.bytes = bytes;
            this.overflow = overflow;
            this.hadData = hadData;
        }
    }

    private static BoundedRead drain(InputStream stream, int maxBytes)
            throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        byte[] block = new byte[512];
        boolean overflow = false;
        boolean hadData = false;
        int read;
        while ((read = stream.read(block)) != -1) {
            if (read == 0) continue;
            hadData = true;
            int remaining = maxBytes - captured.size();
            if (read > remaining) overflow = true;
            if (remaining > 0) captured.write(block, 0, Math.min(read, remaining));
            // Even after the cap, drain and DISCARD to avoid pipe deadlock.
            // The deadline terminates infinite or stalled writers.
        }
        return new BoundedRead(captured.toByteArray(), overflow, hadData);
    }

    private static Thread daemon(String name, Runnable runnable) {
        Thread worker = new Thread(runnable, name);
        worker.setDaemon(true);
        return worker;
    }

    private static void terminate(Process process) {
        if (process == null) return;
        try {
            process.destroy();
        } catch (RuntimeException ignored) {
            // Best effort: the waiting caller has already failed closed.
        }
    }
}
