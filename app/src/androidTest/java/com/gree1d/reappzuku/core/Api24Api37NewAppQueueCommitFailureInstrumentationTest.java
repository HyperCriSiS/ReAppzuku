package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fail-closed pending queue tests with synthetic SharedPreferences, never real
 * app state. The fake deliberately models Android's commit(false) behavior:
 * memory may reflect an edit before the disk write has failed.
 */
public final class Api24Api37NewAppQueueCommitFailureInstrumentationTest {
    private static final String TARGET = "com.reappzuku.test.queuecommit";
    private static final String OTHER = "com.reappzuku.test.other";

    @Test
    public void failedAddCanBeRetriedEvenWhenMemoryAlreadyContainsPackage() {
        Fixture f = fixture(false);
        expectCommitFailure(() -> NewAppSetupStore.addPending(f.prefs, TARGET));
        assertEquals(1, f.commits.get());
        assertTrue("Android may apply failed disk edits in memory",
                f.inMemory.get().contains(TARGET));
        assertFalse("A failed commit is not durable", f.onDisk.get().contains(TARGET));

        f.successfulCommit.set(true);
        NewAppSetupStore.addPending(f.prefs, TARGET);
        assertEquals("Do not skip a necessary retry due to in-memory membership",
                2, f.commits.get());
        assertTrue(f.onDisk.get().contains(TARGET));

        NewAppSetupStore.addPending(f.prefs, "invalid package name");
        assertEquals(2, f.commits.get());
    }

    @Test
    public void failedRemovalIsRetriedEvenWhenMemoryNoLongerContainsPackage() {
        Fixture f = fixture(false, TARGET, OTHER);
        expectCommitFailure(() -> NewAppSetupStore.removePending(f.prefs, TARGET));
        assertEquals(1, f.commits.get());
        assertFalse(f.inMemory.get().contains(TARGET));
        assertTrue("Failed removal must not be treated as durable", f.onDisk.get().contains(TARGET));

        f.successfulCommit.set(true);
        NewAppSetupStore.removePending(f.prefs, TARGET);
        assertEquals(2, f.commits.get());
        assertFalse(f.onDisk.get().contains(TARGET));
        assertTrue(f.onDisk.get().contains(OTHER));
    }

    @Test
    public void evenIdempotentUpdatesAreDurabilityChecked() {
        Fixture f = fixture(true, OTHER);
        NewAppSetupStore.addPending(f.prefs, TARGET);
        NewAppSetupStore.addPending(f.prefs, TARGET);
        assertEquals(2, f.commits.get());
        NewAppSetupStore.removePending(f.prefs, TARGET);
        NewAppSetupStore.removePending(f.prefs, TARGET);
        assertEquals(4, f.commits.get());
        assertEquals(new HashSet<>(Arrays.asList(OTHER)), f.onDisk.get());
    }

    private static void expectCommitFailure(Runnable operation) {
        try {
            operation.run();
            fail("A failed pending-queue disk write must propagate");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("pending queue"));
        }
    }

    private static Fixture fixture(boolean commitsSucceed, String... existing) {
        AtomicReference<Set<String>> memory =
                new AtomicReference<>(new HashSet<>(Arrays.asList(existing)));
        AtomicReference<Set<String>> disk =
                new AtomicReference<>(new HashSet<>(Arrays.asList(existing)));
        AtomicInteger commits = new AtomicInteger();
        AtomicBoolean succeed = new AtomicBoolean(commitsSucceed);

        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.class},
                (proxy, method, args) -> {
                    if ("getStringSet".equals(method.getName())) {
                        return new HashSet<>(memory.get());
                    }
                    if ("edit".equals(method.getName())) {
                        AtomicReference<Set<String>> staged = new AtomicReference<>();
                        return Proxy.newProxyInstance(
                                SharedPreferences.Editor.class.getClassLoader(),
                                new Class<?>[] {SharedPreferences.Editor.class},
                                (editor, editMethod, editArgs) -> {
                                    if ("putStringSet".equals(editMethod.getName())) {
                                        staged.set(new HashSet<>((Set<String>) editArgs[1]));
                                        return editor;
                                    }
                                    if ("commit".equals(editMethod.getName())) {
                                        commits.incrementAndGet();
                                        memory.set(new HashSet<>(staged.get()));
                                        if (succeed.get()) {
                                            disk.set(new HashSet<>(staged.get()));
                                        }
                                        return succeed.get();
                                    }
                                    throw new AssertionError("Unexpected editor API: "
                                            + editMethod.getName());
                                });
                    }
                    throw new AssertionError("Unexpected preference API: "
                            + method.getName());
                });
        return new Fixture(prefs, memory, disk, commits, succeed);
    }

    private static final class Fixture {
        final SharedPreferences prefs;
        final AtomicReference<Set<String>> inMemory;
        final AtomicReference<Set<String>> onDisk;
        final AtomicInteger commits;
        final AtomicBoolean successfulCommit;

        Fixture(SharedPreferences prefs, AtomicReference<Set<String>> inMemory,
                AtomicReference<Set<String>> onDisk, AtomicInteger commits,
                AtomicBoolean successfulCommit) {
            this.prefs = prefs;
            this.inMemory = inMemory;
            this.onDisk = onDisk;
            this.commits = commits;
            this.successfulCommit = successfulCommit;
        }
    }
}
