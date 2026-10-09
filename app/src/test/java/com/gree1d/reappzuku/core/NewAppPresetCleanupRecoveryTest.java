package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class NewAppPresetCleanupRecoveryTest {
    private static final String PACKAGE = "com.example.recovery";

    @Test
    public void persistedPresetMustBeCleanedUpNotAppliedAgain() {
        PolicyPreset preset = new PolicyPreset("test");
        preset.id = 42L;
        preset.strategy = AppPolicy.STRATEGY_PROTECTED;
        AppPolicy persisted = AppPolicyEditorModel.fromPreset(PACKAGE, preset, 100L);

        // Room was committed; the subsequent SharedPreferences removal failed.
        assertTrue(NewAppSetupCoordinator.isCompletedExplicitSetup(persisted, true));
        AtomicInteger removals = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        try {
            NewAppSetupCoordinator.clearPendingBeforeCancel(() -> {
                removals.incrementAndGet();
                throw new IllegalStateException("simulated queue commit(false)");
            }, cancellations::incrementAndGet);
            fail("Failed cleanup must propagate into the Worker retry path");
        } catch (IllegalStateException expected) {
            assertEquals(1, removals.get());
            assertEquals(0, cancellations.get());
        }

        // User changes an already applied preset before a later retry.
        persisted.customized = true;
        persisted.strategy = AppPolicy.STRATEGY_SMART;
        persisted.updatedAt = 200L;
        assertTrue("Pending marker may not cause a second preset write",
                NewAppSetupCoordinator.isCompletedExplicitSetup(persisted, true));
        NewAppSetupCoordinator.clearPendingBeforeCancel(
                removals::incrementAndGet, cancellations::incrementAndGet);
        assertEquals(2, removals.get());
        assertEquals(1, cancellations.get());
        assertEquals(AppPolicy.STRATEGY_SMART, persisted.strategy);
        assertEquals(200L, persisted.updatedAt);
    }

    @Test
    public void falseCommitCanLookRemovedInMemoryAndStillNeedsDurableCleanup() {
        AppPolicy completed = new AppPolicy(PACKAGE);
        completed.source = AppPolicy.SOURCE_EXPLICIT;
        completed.presetId = 8L;
        assertTrue(NewAppSetupCoordinator.isCompletedExplicitSetup(completed, false));
        AtomicInteger writes = new AtomicInteger();
        NewAppSetupCoordinator.clearPendingBeforeCancel(writes::incrementAndGet, () -> {});
        assertEquals("No pending marker in memory must not skip the disk repair", 1, writes.get());
    }

    @Test
    public void temporaryUnmanagedPlaceholderStillReplaysAskOrMissingPreset() {
        AppPolicy placeholder = AppPolicyEditorModel.defaultPolicy(PACKAGE, 100L);
        placeholder.strategy = AppPolicy.STRATEGY_UNMANAGED;
        placeholder.presetId = null;
        placeholder.customized = true;
        assertFalse(NewAppSetupCoordinator.isCompletedExplicitSetup(placeholder, true));
        assertTrue(NewAppSetupCoordinator.isCompletedExplicitSetup(placeholder, false));
        placeholder.protectWidgets = false;
        assertTrue("User-edited explicit settings may not be overwritten",
                NewAppSetupCoordinator.isCompletedExplicitSetup(placeholder, true));
    }

    @Test
    public void migratedOrAbsentPolicyCannotSuppressPendingSetup() {
        assertFalse(NewAppSetupCoordinator.isCompletedExplicitSetup(null, false));
        assertFalse(NewAppSetupCoordinator.isCompletedExplicitSetup(null, true));
        AppPolicy legacy = new AppPolicy(PACKAGE);
        legacy.source = AppPolicy.SOURCE_LEGACY_MIGRATED;
        assertFalse(NewAppSetupCoordinator.isCompletedExplicitSetup(legacy, false));
        assertFalse(NewAppSetupCoordinator.isCompletedExplicitSetup(legacy, true));
    }
}
