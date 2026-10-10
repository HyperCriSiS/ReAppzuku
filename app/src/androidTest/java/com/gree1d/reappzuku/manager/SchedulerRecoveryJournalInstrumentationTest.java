package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import org.junit.Assume;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Disposable API 24/37 real SharedPreferences verification; no app policies,
 * non-test preferences, Shizuku/root or system clock operations are touched.
 */
@RunWith(AndroidJUnit4.class)
public final class SchedulerRecoveryJournalInstrumentationTest {
    private static final String TEST_FILE = "scheduler_recovery_ci_journal_only";
    private static final String PROCESS_FILE = "scheduler_recovery_ci_process_only";

    @Test public void falseCommitPoisonsEveryAdapterForTheSamePreferenceObject() {
        AtomicReference<String> memoryOnlyJournal = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        // Test-only preferences implementation: commit(false) mutates its
        // process-local value without acknowledging a durable disk write.
        SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "getString":
                            return memoryOnlyJournal.get() == null
                                    ? args[1] : memoryOnlyJournal.get();
                        case "edit":
                            return Proxy.newProxyInstance(
                                    SharedPreferences.Editor.class.getClassLoader(),
                                    new Class<?>[]{SharedPreferences.Editor.class},
                                    (editorProxy, editorMethod, editorArgs) -> {
                                        switch (editorMethod.getName()) {
                                            case "putString":
                                                memoryOnlyJournal.set((String) editorArgs[1]);
                                                return editorProxy;
                                            case "commit":
                                                writes.incrementAndGet();
                                                return false;
                                            case "hashCode":
                                                return System.identityHashCode(editorProxy);
                                            case "equals":
                                                return editorProxy == editorArgs[0];
                                            default:
                                                throw new AssertionError("unexpected editor operation");
                                        }
                                    });
                        default:
                            throw new AssertionError("unexpected preferences operation");
                    }
                });
        SchedulerRecoverySharedPreferencesStore first =
                new SchedulerRecoverySharedPreferencesStore(prefs);
        SchedulerRecoverySharedPreferencesStore second =
                new SchedulerRecoverySharedPreferencesStore(prefs);
        SchedulerRecoveryTransaction.Record record = SchedulerRecoveryTransaction.prepare(
                "com.example.recoveryfake", Collections.singleton(11L),
                new SchedulerRecoveryTransaction.OriginalRestrictions(
                        3, 40, false, false, true), 1);
        assertFalse(first.commit(record));
        assertEquals(1, writes.get());
        assertTrue("failure may already be visible in prefs memory",
                memoryOnlyJournal.get() != null);
        assertTrue(second.isPoisoned());
        assertThrows(IllegalStateException.class, second::snapshot);
        SchedulerRecoverySharedPreferencesStore third =
                new SchedulerRecoverySharedPreferencesStore(prefs);
        assertTrue(third.isPoisoned());
        assertFalse(third.commit(record));
        assertEquals("no new attempt may follow a memory-only false commit",
                1, writes.get());
    }

    @Test public void synchronousReopenAndResolvedPrune() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE);
        String old = prefs.getString(SchedulerRecoverySharedPreferencesStore.KEY, null);
        try {
            prefs.edit().remove(SchedulerRecoverySharedPreferencesStore.KEY).commit();
            SchedulerRecoverySharedPreferencesStore store =
                    new SchedulerRecoverySharedPreferencesStore(prefs);
            SchedulerRecoveryTransaction.Record prepared = SchedulerRecoveryTransaction.prepare(
                    "com.example.schedulerci", Collections.singleton(123L),
                    new SchedulerRecoveryTransaction.OriginalRestrictions(
                            5, 40, true, false, true), 1);
            assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                    SchedulerRecoveryTransaction.beginLift(prepared, store, () -> {
                        assertEquals(SchedulerRecoveryTransaction.Phase.APPLYING,
                                new SchedulerRecoverySharedPreferencesStore(prefs).snapshot()
                                        .get("com.example.schedulerci").phase);
                        return true;
                    }));
            SchedulerRecoverySharedPreferencesStore reopened =
                    new SchedulerRecoverySharedPreferencesStore(
                            context.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE));
            SchedulerRecoveryTransaction.Record active =
                    reopened.snapshot().get("com.example.schedulerci");
            assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE, active.phase);
            SchedulerRecoveryTransaction.Record needsRestore = active.removeOwner(123);
            assertTrue(reopened.commit(needsRestore));
            assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                    SchedulerRecoveryTransaction.restore(needsRestore, true, reopened, () -> true));
            assertEquals(SchedulerRecoveryTransaction.Phase.RESOLVED,
                    new SchedulerRecoverySharedPreferencesStore(prefs).snapshot()
                            .get("com.example.schedulerci").phase);
            assertTrue(reopened.removeResolved("com.example.schedulerci"));
            assertTrue(new SchedulerRecoverySharedPreferencesStore(prefs).snapshot().isEmpty());
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (old == null) editor.remove(SchedulerRecoverySharedPreferencesStore.KEY);
            else editor.putString(SchedulerRecoverySharedPreferencesStore.KEY, old);
            assertTrue("test-owned journal cleanup failed", editor.commit());
        }
    }

    @Test public void badDiskStringCannotBeSilentlyReplaced() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE);
        String old = prefs.getString(SchedulerRecoverySharedPreferencesStore.KEY, null);
        try {
            assertTrue(prefs.edit().putString(
                    SchedulerRecoverySharedPreferencesStore.KEY, "invalid-test-fixture").commit());
            SchedulerRecoverySharedPreferencesStore store =
                    new SchedulerRecoverySharedPreferencesStore(prefs);
            assertThrows(IllegalStateException.class, store::snapshot);
            assertTrue(store.isPoisoned());
            assertFalse(store.commit(SchedulerRecoveryTransaction.prepare(
                    "com.example.schedulerci", Collections.singleton(123L),
                    new SchedulerRecoveryTransaction.OriginalRestrictions(
                            5, 40, true, false, true), 1)));
            assertEquals("invalid-test-fixture",
                    prefs.getString(SchedulerRecoverySharedPreferencesStore.KEY, null));
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (old == null) editor.remove(SchedulerRecoverySharedPreferencesStore.KEY);
            else editor.putString(SchedulerRecoverySharedPreferencesStore.KEY, old);
            assertTrue("test-owned journal cleanup failed", editor.commit());
        }
    }

    @Test public void seedForProcessDeath() {
        Assume.assumeTrue("seed".equals(InstrumentationRegistry.getArguments()
                .getString("ci_scheduler_journal_phase")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(PROCESS_FILE, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().remove(SchedulerRecoverySharedPreferencesStore.KEY).commit());
        SchedulerRecoverySharedPreferencesStore store =
                new SchedulerRecoverySharedPreferencesStore(prefs);
        SchedulerRecoveryTransaction.Record record = SchedulerRecoveryTransaction.prepare(
                "com.example.processprobe", Collections.singleton(741L),
                new SchedulerRecoveryTransaction.OriginalRestrictions(13, 45, true, false, true), 1);
        assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                SchedulerRecoveryTransaction.beginLift(record, store, () -> true));
        assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE,
                new SchedulerRecoverySharedPreferencesStore(prefs).snapshot()
                        .get("com.example.processprobe").phase);
        Bundle b = new Bundle();
        b.putString("SCHEDULER_JOURNAL_STATUS", "SEEDED");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, b);
    }

    @Test public void verifyAfterProcessDeath() {
        Assume.assumeTrue("verify".equals(InstrumentationRegistry.getArguments()
                .getString("ci_scheduler_journal_phase")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(PROCESS_FILE, Context.MODE_PRIVATE);
        SchedulerRecoverySharedPreferencesStore store =
                new SchedulerRecoverySharedPreferencesStore(prefs);
        try {
            SchedulerRecoveryTransaction.Record active =
                    store.snapshot().get("com.example.processprobe");
            assertNotNull("record absent after real process death", active);
            assertEquals(SchedulerRecoveryTransaction.Phase.ACTIVE, active.phase);
            assertEquals(13, active.original.appOpsMask);
            assertEquals(45, active.original.standbyBucket);
            assertTrue(active.original.deviceIdleWhitelisted);
            assertEquals(Collections.singleton(741L), active.owners);
            SchedulerRecoveryTransaction.Record restore = active.removeOwner(741L);
            assertTrue(store.commit(restore));
            assertEquals(SchedulerRecoveryTransaction.Outcome.COMPLETED,
                    SchedulerRecoveryTransaction.restore(restore, true, store, () -> true));
            assertTrue(store.removeResolved("com.example.processprobe"));
            assertTrue(new SchedulerRecoverySharedPreferencesStore(prefs).snapshot().isEmpty());
            Bundle b = new Bundle();
            b.putString("SCHEDULER_JOURNAL_STATUS", "RECOVERED");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, b);
        } finally {
            assertTrue(prefs.edit().remove(SchedulerRecoverySharedPreferencesStore.KEY).commit());
        }
    }

}