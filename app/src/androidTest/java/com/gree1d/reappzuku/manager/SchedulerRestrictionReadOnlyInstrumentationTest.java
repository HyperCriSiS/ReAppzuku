package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.App;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Read-only installed-package sanity check on disposable Android runtimes.
 * Does not request Shizuku, write preferences or change package restrictions.
 */
@RunWith(AndroidJUnit4.class)
public final class SchedulerRestrictionReadOnlyInstrumentationTest {
    @Test public void twoPlatformPackageIdentityReadsAreStableOrFailClosed() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SchedulerInstalledPackageAndroidSource source =
                new SchedulerInstalledPackageAndroidSource(context);
        SchedulerInstalledPackageIdentity.Result observation =
                SchedulerInstalledPackageIdentity.captureStable(context.getPackageName(), source);
        assertEquals("own installed signed APK identity must be readable",
                SchedulerInstalledPackageIdentity.Status.MATCHING_READS, observation.status);
        assertNotNull(observation.snapshot);
        assertEquals(context.getApplicationInfo().uid, observation.snapshot.uid);
        assertEquals(context.getPackageName(), observation.snapshot.packageName);
        assertEquals(64, observation.snapshot.currentSignerSha256.length());
        assertEquals(SchedulerInstalledPackageIdentity.Status.MATCHING_READS,
                SchedulerInstalledPackageIdentity.checkAgain(
                        observation.snapshot, source).status);
        assertEquals(SchedulerInstalledPackageIdentity.Status.UNREADABLE,
                SchedulerInstalledPackageIdentity.captureStable(
                        "com.example.thispackagecannotexist", source).status);
    }

    @Test public void readsInstalledPackageIdentityWithoutPerformingAnyMutation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String packageName = context.getPackageName();
        App app = (App) context.getApplicationContext();
        SchedulerRestrictionAndroidReader reader = new SchedulerRestrictionAndroidReader(
                context, app.getShellManager());

        Integer uid = reader.uid(packageName);
        assertNotNull("installed app UID must be visible", uid);
        assertTrue(uid > 0);
        Integer enabled = reader.enabledSetting(packageName);
        assertNotNull("exact enabledSetting must be readable", enabled);
        assertTrue(enabled >= 0 && enabled <= 4);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertNotNull("own suspended state must be readable", reader.suspended(packageName));
        } else {
            assertNull("API24–28 cannot provide a trusted suspension value",
                    reader.suspended(packageName));
        }

        assertNull("reject untrusted package before any shell read",
                reader.uid("com.example;echo-not-safe"));
        SchedulerRestrictionObservation.Result observed = reader.observe(packageName);
        assertNotNull(observed);
        if (app.getShellManager().isAnyShellReady()) {
            // A real privileged backend may expose all or only part of the
            // catalog; a missing mode must never create partial success data.
            if (!observed.complete()) assertNull(observed.snapshot);
        } else {
            assertEquals(SchedulerRestrictionObservation.Status.BACKEND_NOT_READY,
                    observed.status);
            assertNull(observed.snapshot);
        }
    }
}