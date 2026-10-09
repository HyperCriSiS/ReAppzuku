package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic fault injection at each step of the Ask/preset-missing path.
 * No real package, preferences, notification or database is touched.
 */
public class NewAppSetupCommitOrderTest {
    @Test
    public void failedPendingCommitCannotCreateOrphanExplicitPolicy() {
        AtomicBoolean explicitPolicyWritten = new AtomicBoolean();
        AtomicBoolean notificationSent = new AtomicBoolean();
        try {
            NewAppSetupCoordinator.queueBeforePolicy(
                    () -> { throw new IllegalStateException("simulated queue commit(false)"); },
                    () -> explicitPolicyWritten.set(true),
                    () -> notificationSent.set(true));
            fail("A failed queue commit must abort the decision");
        } catch (IllegalStateException expected) {
            assertFalse(explicitPolicyWritten.get());
            assertFalse(notificationSent.get());
        }
    }

    @Test
    public void failedPolicyWriteLeavesPendingMarkerForLaterRetry() {
        AtomicBoolean durablePending = new AtomicBoolean();
        AtomicBoolean explicitPolicyWritten = new AtomicBoolean();
        AtomicInteger notices = new AtomicInteger();
        try {
            NewAppSetupCoordinator.queueBeforePolicy(
                    () -> durablePending.set(true),
                    () -> { throw new IllegalStateException("simulated Room failure"); },
                    notices::incrementAndGet);
            fail("Failed policy persist must stop notifications");
        } catch (IllegalStateException expected) {
            assertTrue("A committed pending marker makes the next retry eligible",
                    durablePending.get());
            assertFalse(explicitPolicyWritten.get());
            assertEquals(0, notices.get());
        }
        NewAppSetupCoordinator.queueBeforePolicy(
                () -> assertTrue("The pending entry must survive until replay", durablePending.get()),
                () -> explicitPolicyWritten.set(true),
                notices::incrementAndGet);
        assertTrue(explicitPolicyWritten.get());
        assertEquals(1, notices.get());
    }

    @Test
    public void notificationFailureLeavesBothDurableRecoveryInputs() {
        AtomicBoolean pending = new AtomicBoolean();
        AtomicBoolean policy = new AtomicBoolean();
        try {
            NewAppSetupCoordinator.queueBeforePolicy(
                    () -> pending.set(true), () -> policy.set(true),
                    () -> { throw new IllegalStateException("temporary notifier fault"); });
            fail("Failed notifier must be visible for retry");
        } catch (IllegalStateException expected) {
            assertTrue(pending.get());
            assertTrue(policy.get());
        }
    }
}
