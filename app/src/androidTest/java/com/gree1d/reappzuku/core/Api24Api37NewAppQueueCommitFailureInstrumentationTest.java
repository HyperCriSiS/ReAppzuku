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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Inject SharedPreferences.Editor.commit(false) without touching application data.
 * The test-only SharedPreferences is a narrow dynamic proxy; every unexpected
 * API call throws to prevent silently accepting a fake implementation.
 */
public final class Api24Api37NewAppQueueCommitFailureInstrumentationTest {
    private static final String TARGET = "com.reappzuku.test.queuecommit";
    private static final String OTHER = "com.reappzuku.test.other";

    @Test
    public void failedAddCommitThrowsWithoutWritingAnyRealAppState() {
        Fixture f = fixture(false);
        expectCommitFailure(() -> NewAppSetupStore.addPending(f.prefs, TARGET));
        assertEquals(1, f.commits.get());
        assertFalse(f.values.get().contains(TARGET));
        // Malformed names remain rejected without attempting storage.
        NewAppSetupStore.addPending(f.prefs, "invalid package name");
        assertEquals(1, f.commits.get());
    }

    @Test
    public void failedRemoveCommitCannotBeMistakenForCompletedCleanup() {
        Fixture f = fixture(false, TARGET, OTHER);
        expectCommitFailure(() -> NewAppSetupStore.removePending(f.prefs, TARGET));
        assertEquals(1, f.commits.get());
        assertTrue(f.values.get().contains(TARGET));
        assertTrue(f.values.get().contains(OTHER));
        // Removing something absent has no write and therefore no false failure.
        NewAppSetupStore.removePending(f.prefs, "com.reappzuku.test.absent");
        assertEquals(1, f.commits.get());
    }

    @Test
    public void successfulQueueUpdatesRemainIdempotentAndPreserveOtherItems() {
        Fixture f = fixture(true, OTHER);
        NewAppSetupStore.addPending(f.prefs, TARGET);
        assertTrue(f.values.get().contains(TARGET));
        assertTrue(f.values.get().contains(OTHER));
        NewAppSetupStore.addPending(f.prefs, TARGET);
        assertEquals("Repeated queueing should not rewrite a durable entry",
                1, f.commits.get());
        NewAppSetupStore.removePending(f.prefs, TARGET);
        assertFalse(f.values.get().contains(TARGET));
        assertTrue(f.values.get().contains(OTHER));
        assertEquals(2, f.commits.get());
    }

    private static void expectCommitFailure(Runnable operation) {
        try {
            operation.run();
            fail("A failed pending-queue commit must propagate");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("pending queue"));
        }
    }

    private static Fixture fixture(boolean commitsSucceed, String... existing) {
        AtomicReference<Set<String>> values =
                new AtomicReference<>(new HashSet<>(Arrays.asList(existing)));
        AtomicInteger commits = new AtomicInteger();
        SharedPreferences fake = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.class},
                (proxy, method, args) -> {
                    if ("getStringSet".equals(method.getName())) {
                        return new HashSet<>(values.get());
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
                                        if (commitsSucceed) values.set(staged.get());
                                        return commitsSucceed;
                                    }
                                    throw new AssertionError("Unexpected editor method: "
                                            + editMethod.getName());
                                });
                    }
                    throw new AssertionError("Unexpected preferences method: "
                            + method.getName());
                });
        return new Fixture(fake, values, commits);
    }

    private static final class Fixture {
        final SharedPreferences prefs;
        final AtomicReference<Set<String>> values;
        final AtomicInteger commits;

        Fixture(SharedPreferences prefs, AtomicReference<Set<String>> values,
                AtomicInteger commits) {
            this.prefs = prefs;
            this.values = values;
            this.commits = commits;
        }
    }
}
