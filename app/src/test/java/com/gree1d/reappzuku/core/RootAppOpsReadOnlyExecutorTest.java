package com.gree1d.reappzuku.core;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Pure-JVM fault fixtures; no real Root or AppOps privilege is requested. */
public final class RootAppOpsReadOnlyExecutorTest {
    private static final String GET =
            "cmd appops get --user 0 com.example.app WAKE_LOCK";

    private static byte[] data(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static class FakeProcess extends Process {
        final InputStream stdout;
        final InputStream stderr;
        final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        final int exitCode;
        final CountDownLatch released = new CountDownLatch(1);
        final boolean blocks;
        volatile boolean destroyed;

        FakeProcess(byte[] out, byte[] err, int exit, boolean blocks) {
            this.stdout = new ByteArrayInputStream(out);
            this.stderr = new ByteArrayInputStream(err);
            this.exitCode = exit;
            this.blocks = blocks;
        }

        @Override public OutputStream getOutputStream() { return stdin; }
        @Override public InputStream getInputStream() { return stdout; }
        @Override public InputStream getErrorStream() { return stderr; }
        @Override public int waitFor() throws InterruptedException {
            if (blocks) released.await();
            return exitCode;
        }
        @Override public int exitValue() {
            if (blocks && !destroyed) throw new IllegalThreadStateException();
            return exitCode;
        }
        @Override public void destroy() {
            destroyed = true;
            released.countDown();
        }
    }

    private static RootAppOpsReadOnlyExecutor.Outcome read(FakeProcess process) {
        return RootAppOpsReadOnlyExecutor.execute(() -> process, GET, 2_000L);
    }

    @Test public void exactReadOnlyGetSucceedsWithNoStderr() {
        FakeProcess p = new FakeProcess(
                data("No operations.\nDefault mode: allow\n"), new byte[0], 0, false);
        RootAppOpsReadOnlyExecutor.Outcome result = read(p);
        assertTrue(result.succeeded);
        assertEquals(0, result.exitCode);
        assertEquals("No operations.\nDefault mode: allow\n", result.output);
        assertEquals(RootAppOpsReadOnlyExecutor.REQUIRE_ROOT_EUID + GET + "\nexit\n",
                new String(p.stdin.toByteArray(), StandardCharsets.US_ASCII));
        assertTrue("Completed process should be released", p.destroyed);
    }

    @Test public void revokedSuExit125NeverBecomesDiagnosticEvidence() {
        FakeProcess p = new FakeProcess(new byte[0], new byte[0], 125, false);
        RootAppOpsReadOnlyExecutor.Outcome result = read(p);
        assertFalse("A failed effective-UID preflight must not become evidence",
                result.succeeded);
        assertEquals(-1, result.exitCode);
        assertEquals("", result.output);
        assertEquals(RootAppOpsReadOnlyExecutor.REQUIRE_ROOT_EUID + GET + "\nexit\n",
                new String(p.stdin.toByteArray(), StandardCharsets.US_ASCII));
        assertTrue(p.destroyed);
    }

    @Test public void failingExitCannotBecomeSuccessfulEvidence() {
        FakeProcess p = new FakeProcess(data("WAKE_LOCK: allow\n"), new byte[0], 1, false);
        RootAppOpsReadOnlyExecutor.Outcome result = read(p);
        assertFalse(result.succeeded);
        assertEquals(-1, result.exitCode);
        assertEquals("", result.output);
    }

    @Test public void evenOneStderrWarningFailsClosed() {
        FakeProcess p = new FakeProcess(
                data("No operations.\nDefault mode: allow\n"),
                data("warning\n"), 0, false);
        RootAppOpsReadOnlyExecutor.Outcome result = read(p);
        assertFalse(result.succeeded);
        assertEquals("", result.output);
    }

    @Test public void simultaneousLargeStdoutAndStderrNeverDeadlockOrEscapeBounds() {
        byte[] out = new byte[65_536];
        byte[] err = new byte[65_536];
        Arrays.fill(out, (byte) 'A');
        Arrays.fill(err, (byte) 'B');
        FakeProcess p = new FakeProcess(out, err, 0, false);
        RootAppOpsReadOnlyExecutor.Outcome result = read(p);
        assertFalse(result.succeeded);
        assertEquals("", result.output);
        assertTrue(p.destroyed);
    }

    @Test public void outputLargerThanFourKiBFailsEvenIfExitZero() {
        byte[] out = new byte[RootAppOpsReadOnlyExecutor.MAX_STDOUT_BYTES + 1];
        Arrays.fill(out, (byte) 'x');
        assertFalse(read(new FakeProcess(out, new byte[0], 0, false)).succeeded);
        assertFalse(read(new FakeProcess(new byte[0], new byte[0], 0, false)).succeeded);
    }

    @Test public void stalledChildIsDestroyedWithinDeadline() {
        FakeProcess p = new FakeProcess(
                data("No operations.\n"), new byte[0], 0, true);
        long start = System.nanoTime();
        RootAppOpsReadOnlyExecutor.Outcome result =
                RootAppOpsReadOnlyExecutor.execute(() -> p, GET, 80L);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertFalse(result.succeeded);
        assertTrue("The stalled child must be destroyed", p.destroyed);
        assertTrue("Caller must not hang on the child", elapsedMs < 1_500L);
    }

    @Test public void unavailableSuAndInvalidCommandsCannotProduceEvidence() {
        RootAppOpsReadOnlyExecutor.Outcome unavailable =
                RootAppOpsReadOnlyExecutor.execute(() -> {
                    throw new IOException("permission denied");
                }, GET, 250L);
        assertFalse(unavailable.succeeded);

        AtomicInteger launches = new AtomicInteger();
        RootAppOpsReadOnlyExecutor.Starter spawner = () -> {
            launches.incrementAndGet();
            return new FakeProcess(data("allow\n"), new byte[0], 0, false);
        };
        for (String invalid : new String[]{
                "cmd appops set --user 0 com.example.app WAKE_LOCK ignore",
                "cmd appops reset com.example.app",
                "cmd appops get --user 0 com.example.app WAKE_LOCK;id",
                "cmd appops get --user 0 com.example.app WAKE_LOCK\nid"
        }) {
            assertFalse(RootAppOpsReadOnlyExecutor.execute(
                    spawner, invalid, 200L).succeeded);
        }
        assertEquals("Invalid commands must not spawn su", 0, launches.get());
    }

    @Test public void interruptedCallerFailsAndDestroysProcess() throws Exception {
        FakeProcess p = new FakeProcess(
                data("No operations.\n"), new byte[0], 0, true);
        final RootAppOpsReadOnlyExecutor.Outcome[] answer = new RootAppOpsReadOnlyExecutor.Outcome[1];
        final boolean[] interruptRestored = {false};
        Thread caller = new Thread(() -> {
            answer[0] = RootAppOpsReadOnlyExecutor.execute(() -> p, GET, 2_000L);
            interruptRestored[0] = Thread.currentThread().isInterrupted();
        });
        caller.start();
        Thread.sleep(50L);
        caller.interrupt();
        caller.join(1_000L);
        assertFalse(caller.isAlive());
        assertNotNull(answer[0]);
        assertFalse(answer[0].succeeded);
        assertTrue(interruptRestored[0]);
        assertTrue(p.destroyed);
    }
}
