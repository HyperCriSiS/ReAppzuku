package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

/**
 * Disposable API24/API37 test-owned v2 preferences; no production recovery
 * records, Shizuku/root, app policy or OS restriction is ever touched.
 */
@RunWith(AndroidJUnit4.class)
public final class SchedulerRecoveryEvidenceV2InstrumentationTest {
    private static final String TEST_FILE = "scheduler_recovery_v2_ci_only";
    private static final String CORRUPT_FILE = "scheduler_recovery_v2_ci_corrupt_only";

    private static SchedulerRecoveryEvidenceV2 record() {
        return SchedulerRecoveryEvidenceV2.rehydrate(
                "com.example.recoveryv2", 10921, 0, false, 40,
                SchedulerRestrictionObservation.WhitelistKind.USER,
                Collections.singletonMap("WAKE_LOCK",
                        SchedulerRestrictionObservation.AppOpMode.DEFAULT),
                Collections.singleton(911L), 1781000000000L, 1,
                SchedulerRecoveryEvidenceV2.Phase.OBSERVED);
    }

    @Test public void synchronousReopenKeepsExactModesAndReviewMarker() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE);
        String previous = prefs.getString(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY, null);
        try {
            assertTrue(prefs.edit().remove(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY).commit());
            SchedulerRecoveryEvidenceV2 row = record();
            SchedulerRecoveryEvidenceSharedPreferencesStoreV2 first =
                    new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(prefs);
            assertTrue(first.commitCaptured(row));
            SchedulerRecoveryEvidenceSharedPreferencesStoreV2 reopened =
                    new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(
                            context.getSharedPreferences(TEST_FILE, Context.MODE_PRIVATE));
            Map<String, SchedulerRecoveryEvidenceV2> snapshot = reopened.snapshot();
            assertEquals(1, snapshot.size());
            assertEquals(10921, snapshot.get(row.packageName).uid);
            assertEquals(SchedulerRestrictionObservation.AppOpMode.DEFAULT,
                    snapshot.get(row.packageName).appOps.get("WAKE_LOCK"));
            assertEquals(SchedulerRestrictionObservation.WhitelistKind.USER,
                    snapshot.get(row.packageName).whitelistKind);
            assertTrue(reopened.requireManualReview(row.requireManualReview()));
            assertEquals(SchedulerRecoveryEvidenceV2.Phase.REVIEW_REQUIRED,
                    new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(prefs)
                            .snapshot().get(row.packageName).phase);
            assertFalse(first.commitCaptured(row));
        } finally {
            SharedPreferences.Editor cleanup = prefs.edit();
            if (previous == null) {
                cleanup.remove(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY);
            } else {
                cleanup.putString(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY, previous);
            }
            assertTrue("test-owned v2 journal cleanup failed", cleanup.commit());
        }
    }

    @Test public void corruptedDiskEvidencePoisonsAllSameProcessAdapters() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(CORRUPT_FILE, Context.MODE_PRIVATE);
        String previous = prefs.getString(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY, null);
        try {
            assertTrue(prefs.edit().putString(
                    SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY,
                    "invalid-ci-evidence").commit());
            SchedulerRecoveryEvidenceSharedPreferencesStoreV2 first =
                    new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(prefs);
            assertThrows(IllegalStateException.class, first::snapshot);
            SchedulerRecoveryEvidenceSharedPreferencesStoreV2 second =
                    new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(prefs);
            assertTrue(second.isPoisoned());
            assertFalse(second.commitCaptured(record()));
            assertEquals("invalid-ci-evidence",
                    prefs.getString(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY, null));
        } finally {
            SharedPreferences.Editor cleanup = prefs.edit();
            if (previous == null) {
                cleanup.remove(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY);
            } else {
                cleanup.putString(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY, previous);
            }
            assertTrue("test-owned corrupt fixture cleanup failed", cleanup.commit());
        }
    }
    private static final String PROCESS_FILE = "scheduler_recovery_v2_ci_process_death_only";

    @Test public void seedForRealProcessDeath() {
        org.junit.Assume.assumeTrue("seed".equals(
                InstrumentationRegistry.getArguments().getString("ci_recovery_v2_phase")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(PROCESS_FILE, Context.MODE_PRIVATE);
        assertTrue(prefs.edit().remove(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY).commit());
        SchedulerRecoveryEvidenceV2 row = record();
        assertTrue(new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(prefs).commitCaptured(row));
        assertEquals(row.uid, new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(prefs)
                .snapshot().get(row.packageName).uid);
        android.os.Bundle status = new android.os.Bundle();
        status.putString("RECOVERY_V2_STATUS", "SEEDED");
        InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
    }

    @Test public void verifyAfterRealProcessDeath() {
        org.junit.Assume.assumeTrue("verify".equals(
                InstrumentationRegistry.getArguments().getString("ci_recovery_v2_phase")));
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(PROCESS_FILE, Context.MODE_PRIVATE);
        try {
            SchedulerRecoveryEvidenceSharedPreferencesStoreV2 store =
                    new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(prefs);
            SchedulerRecoveryEvidenceV2 original = record();
            SchedulerRecoveryEvidenceV2 loaded = store.snapshot().get(original.packageName);
            assertNotNull("RZJ2 record missing after app PID death", loaded);
            assertEquals(original.uid, loaded.uid);
            assertEquals(original.enabledSetting, loaded.enabledSetting);
            assertEquals(original.suspended, loaded.suspended);
            assertEquals(original.standbyBucket, loaded.standbyBucket);
            assertEquals(original.whitelistKind, loaded.whitelistKind);
            assertEquals(original.appOps, loaded.appOps);
            assertEquals(original.owners, loaded.owners);
            assertEquals(original.captureTimestampMillis, loaded.captureTimestampMillis);
            assertEquals(original.revision, loaded.revision);
            assertEquals(SchedulerRecoveryEvidenceV2.Phase.OBSERVED, loaded.phase);
            assertTrue(store.requireManualReview(loaded.requireManualReview()));
            assertEquals(SchedulerRecoveryEvidenceV2.Phase.REVIEW_REQUIRED,
                    new SchedulerRecoveryEvidenceSharedPreferencesStoreV2(prefs)
                            .snapshot().get(original.packageName).phase);
            android.os.Bundle status = new android.os.Bundle();
            status.putString("RECOVERY_V2_STATUS", "VERIFIED");
            InstrumentationRegistry.getInstrumentation().sendStatus(0, status);
        } finally {
            assertTrue("CI-only RZJ2 process fixture cleanup failed", prefs.edit()
                    .remove(SchedulerRecoveryEvidenceSharedPreferencesStoreV2.KEY).commit());
        }
    }

}
