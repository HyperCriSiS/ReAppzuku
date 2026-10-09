package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.SharedPreferences;

import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Disposable API24/API37 safety regression. All data is synthetic: no real
 * Room row, preferences, setup queue or user notification is modified.
 */
public final class Api24Api37PresetCleanupRecoveryInstrumentationTest {
    private static final String TARGET = "com.reappzuku.ci.presetcleanup";

    @Test
    public void failedPendingRemovalCannotReapplyPresetOrSkipDurableRetry() {
        AtomicReference<Set<String>> memory = new AtomicReference<>(
                new HashSet<>(Collections.singleton(TARGET)));
        AtomicReference<Set<String>> disk = new AtomicReference<>(
                new HashSet<>(Collections.singleton(TARGET)));
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger cancels = new AtomicInteger();
        SharedPreferences fake = preferences(memory, disk, writes);

        AppPolicy applied = new AppPolicy(TARGET);
        applied.source = AppPolicy.SOURCE_EXPLICIT;
        applied.presetId = 18L;
        applied.strategy = AppPolicy.STRATEGY_PROTECTED;
        applied.createdAt = 100L;
        assertTrue(NewAppSetupCoordinator.isCompletedExplicitSetup(applied, true));

        try {
            NewAppSetupCoordinator.clearPendingBeforeCancel(
                    () -> NewAppSetupStore.removePending(fake, TARGET),
                    cancels::incrementAndGet);
            fail("First synthetic disk commit must fail");
        } catch (IllegalStateException expected) {
            assertEquals(1, writes.get());
            assertEquals(0, cancels.get());
            assertFalse(memory.get().contains(TARGET));
            assertTrue(disk.get().contains(TARGET));
        }

        // The user may change the saved explicit policy before the retry.
        applied.customized = true;
        applied.strategy = AppPolicy.STRATEGY_SMART;
        assertTrue(NewAppSetupCoordinator.isCompletedExplicitSetup(applied, false));
        NewAppSetupCoordinator.clearPendingBeforeCancel(
                () -> NewAppSetupStore.removePending(fake, TARGET),
                cancels::incrementAndGet);
        assertEquals(2, writes.get());
        assertEquals(1, cancels.get());
        assertFalse(disk.get().contains(TARGET));
        assertEquals(AppPolicy.STRATEGY_SMART, applied.strategy);
        assertEquals(Long.valueOf(18L), applied.presetId);
    }

    @SuppressWarnings("unchecked")
    private static SharedPreferences preferences(AtomicReference<Set<String>> memory,
            AtomicReference<Set<String>> disk, AtomicInteger writes) {
        return (SharedPreferences) Proxy.newProxyInstance(
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
                                        memory.set(new HashSet<>(staged.get()));
                                        if (writes.incrementAndGet() > 1) {
                                            disk.set(new HashSet<>(staged.get()));
                                            return true;
                                        }
                                        return false;
                                    }
                                    throw new AssertionError("Unexpected editor call: "
                                            + editMethod.getName());
                                });
                    }
                    throw new AssertionError("Unexpected preferences call: "
                            + method.getName());
                });
    }
}
