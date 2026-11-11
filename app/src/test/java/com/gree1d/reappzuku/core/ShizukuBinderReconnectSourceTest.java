package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * After the Shizuku server restarts, a running main process is not handed the
 * new Binder (issue 170). It must ask the provider again, a bounded number of
 * times, and stop as soon as the Binder arrives.
 */
public class ShizukuBinderReconnectSourceTest {

    @Test
    public void lostBinderIsRequestedAgainBoundedAndStopsOnArrival() throws Exception {
        String shell = read("app/src/main/java/com/gree1d/reappzuku/core/ShellManager.java");
        int dead = shell.indexOf("internalBinderDeadListener = () -> {");
        String deadBody = shell.substring(dead, shell.indexOf("};", dead));
        assertTrue(deadBody.contains("ShellManager.this.handler.post(this::requestBinderAgain);"));

        int received = shell.indexOf("internalBinderReceivedListener = () -> {");
        String receivedBody = shell.substring(received, shell.indexOf("};", received));
        assertTrue(receivedBody.contains("ShellManager.this.handler.removeCallbacks(binderRequest);"));

        int request = shell.indexOf("private final Runnable binderRequest");
        String requestBody = shell.substring(request, shell.indexOf("};", request));
        assertTrue(requestBody.contains("if (!shizukuBinderLost || binderRequestsLeft <= 0) return;"));
        // Off the main thread, through the bridge, without a new receiver per call.
        assertTrue(requestBody.contains("executor.execute(() -> {"));
        assertTrue(requestBody.contains("shizuku.requestBinderFromProvider(context);"));
        // Each request leaves a receiver in the library: the rounds per process are capped.
        assertTrue(shell.contains("if (binderRequestRounds >= BINDER_REQUEST_ROUNDS) return;"));
        assertTrue(ShellManager.BINDER_REQUEST_ROUNDS * ShellManager.BINDER_REQUEST_ATTEMPTS < 100);

        // Two minutes of retries, then only on the next user action.
        assertEquals(120_000L, ShellManager.BINDER_REQUEST_INTERVAL_MS * ShellManager.BINDER_REQUEST_ATTEMPTS);
        assertTrue(shell.contains("if (shizukuBinderLost && binderRequestsLeft <= 0) {"));
    }

    @Test
    public void serviceAndActivityKeepSeparateBinderListeners() throws Exception {
        String shell = read("app/src/main/java/com/gree1d/reappzuku/core/ShellManager.java");
        assertTrue(shell.contains("public synchronized void setShizukuBinderListeners(Object owner,"));
        assertTrue(shell.contains("public synchronized void removeShizukuBinderListeners(Object owner) {"));
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/ui/MainActivity.java")
                .contains("shellManager.removeShizukuBinderListeners(this);"));
        assertTrue(read("app/src/main/java/com/gree1d/reappzuku/service/ShappkyService.java")
                .contains("shellManager.removeShizukuBinderListeners(this);"));
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(
                Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative)).replace("\r\n", "\n");
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
