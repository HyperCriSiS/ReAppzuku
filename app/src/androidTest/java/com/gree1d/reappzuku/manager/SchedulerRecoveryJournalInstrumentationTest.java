package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Disposable API 24/37 real SharedPreferences verification; no app policies,
 * non-test preferences, Shizuku/root or system clock operations are touched.
 */
@RunWith(AndroidJUnit4.class)
public final class SchedulerRecoveryJournalInstrumentationTest {
    private static final String TEST_FILE = "scheduler_recovery_ci_journal_only";

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
}
