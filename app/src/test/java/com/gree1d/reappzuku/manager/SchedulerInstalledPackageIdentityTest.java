package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public final class SchedulerInstalledPackageIdentityTest {
    private static final String PACKAGE = "com.example.identity";
    private static final String SIGNER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_SIGNER =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static SchedulerInstalledPackageIdentity.Snapshot row(int uid, String digest,
            long version, long first, long update, boolean single) {
        return new SchedulerInstalledPackageIdentity.Snapshot(
                PACKAGE, uid, 0, digest, version, first, update, single);
    }

    @Test public void doubleReadStableCapturesEveryIdentityDimension() {
        AtomicInteger reads = new AtomicInteger();
        SchedulerInstalledPackageIdentity.Snapshot expected =
                row(10421, SIGNER, 8, 1000, 2000, true);
        SchedulerInstalledPackageIdentity.Result result =
                SchedulerInstalledPackageIdentity.captureStable(PACKAGE, pkg -> {
                    reads.incrementAndGet();
                    return expected;
                });
        assertTrue(result.matched());
        assertSame(expected, result.snapshot);
        assertEquals(2, reads.get());
        assertEquals(SIGNER, result.snapshot.currentSignerSha256);
        assertEquals(10421, result.snapshot.uid);
    }

    @Test public void reportsChangeIfSignerOrUidOrVersionDriftsBetweenTwoReads() {
        SchedulerInstalledPackageIdentity.Snapshot original =
                row(10421, SIGNER, 8, 1000, 2000, true);
        SchedulerInstalledPackageIdentity.Snapshot[] variations = {
                row(10421, OTHER_SIGNER, 8, 1000, 2000, true),
                row(10422, SIGNER, 8, 1000, 2000, true),
                row(10421, SIGNER, 9, 1000, 2001, true),
                row(10421, SIGNER, 8, 3000, 3000, true)
        };
        for (SchedulerInstalledPackageIdentity.Snapshot changed : variations) {
            AtomicInteger count = new AtomicInteger();
            SchedulerInstalledPackageIdentity.Result r =
                    SchedulerInstalledPackageIdentity.captureStable(PACKAGE,
                            pkg -> count.getAndIncrement() == 0 ? original : changed);
            assertEquals(SchedulerInstalledPackageIdentity.Status.CHANGED, r.status);
            assertNull(r.snapshot);
        }
    }

    @Test public void staleCapturedIdentityCannotReenterAsCurrent() {
        SchedulerInstalledPackageIdentity.Snapshot previous =
                row(10421, SIGNER, 8, 1000, 2000, true);
        assertEquals(SchedulerInstalledPackageIdentity.Status.CHANGED,
                SchedulerInstalledPackageIdentity.checkAgain(previous,
                        pkg -> row(10421, OTHER_SIGNER, 8, 1000, 2000, true)).status);
        assertEquals(SchedulerInstalledPackageIdentity.Status.MATCHING_READS,
                SchedulerInstalledPackageIdentity.checkAgain(previous, pkg -> previous).status);
    }

    @Test public void rejectsInvalidSignerUnknownVisibilityAndImpossibleInstallData() {
        SchedulerInstalledPackageIdentity.Snapshot[] invalid = {
                row(0, SIGNER, 8, 1000, 2000, true),
                row(10421, "sha256:unverified", 8, 1000, 2000, true),
                row(10421, SIGNER, 8, 1000, 2000, false),
                row(10421, SIGNER, 8, 2000, 1000, true),
                row(10421, SIGNER, -1, 1000, 2000, true),
                new SchedulerInstalledPackageIdentity.Snapshot("com.other.pkg",
                        10421, 0, SIGNER, 8, 1000, 2000, true)
        };
        for (SchedulerInstalledPackageIdentity.Snapshot item : invalid) {
            SchedulerInstalledPackageIdentity.Result result =
                    SchedulerInstalledPackageIdentity.captureStable(PACKAGE, pkg -> item);
            assertEquals(SchedulerInstalledPackageIdentity.Status.UNREADABLE, result.status);
            assertNull(result.snapshot);
        }
    }

    @Test public void packageValidationAndReadExceptionFailClosed() {
        AtomicInteger count = new AtomicInteger();
        assertEquals(SchedulerInstalledPackageIdentity.Status.INVALID_INPUT,
                SchedulerInstalledPackageIdentity.captureStable("com.example;kill",
                        p -> { count.incrementAndGet(); return null; }).status);
        assertEquals(0, count.get());
        assertEquals(SchedulerInstalledPackageIdentity.Status.UNREADABLE,
                SchedulerInstalledPackageIdentity.captureStable(PACKAGE,
                        p -> { throw new SecurityException("not visible"); }).status);
        assertEquals(SchedulerInstalledPackageIdentity.Status.UNREADABLE,
                SchedulerInstalledPackageIdentity.captureStable(PACKAGE,
                        p -> null).status);
    }
}
