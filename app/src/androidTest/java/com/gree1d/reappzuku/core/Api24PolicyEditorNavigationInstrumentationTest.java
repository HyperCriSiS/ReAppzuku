package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;
import com.gree1d.reappzuku.ui.NewAppSetupSettingsActivity;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android 7 policy-editor navigation checks. The editor cancel test uses a
 * synthetic package; Review next uses the installed instrumentation package
 * so legitimate stale-queue pruning does not invalidate the test fixture.
 * Existing queue membership, setup mode and any prior fixture policy are restored.
 */
public class Api24PolicyEditorNavigationInstrumentationTest {
    private static final String PROBE = "com.reappzuku.api24editorprobe";

    @Test
    public void editorDeepLinkCancelDoesNotPersistPolicyOrDismissPending() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        assertNull(AppDatabase.getInstance(context).appPolicyDao().getByPackage(PROBE));
        boolean wasPending = NewAppSetupStore.isPending(context, PROBE);
        Activity editor = null;
        try {
            NewAppSetupStore.addPending(context, PROBE);
            Intent deepLink = NewAppSetupNotifier.createPolicyEditorIntent(context, PROBE);
            assertEquals(AppPolicyEditorActivity.class.getName(),
                    deepLink.getComponent().getClassName());
            assertEquals(PROBE, deepLink.getStringExtra(
                    AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
            assertTrue((deepLink.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);

            editor = instrumentation.startActivitySync(deepLink);
            instrumentation.waitForIdleSync();
            assertTrue(editor instanceof AppPolicyEditorActivity);
            TextView packageView = editor.findViewById(R.id.policy_package);
            assertNotNull(packageView);
            assertEquals(PROBE, packageView.getText().toString());
            assertTrue(NewAppSetupStore.isPending(context, PROBE));

            Activity finalEditor = editor;
            instrumentation.runOnMainSync(
                    () -> finalEditor.findViewById(R.id.policy_cancel).performClick());
            instrumentation.waitForIdleSync();

            assertTrue(editor.isFinishing());
            assertTrue(NewAppSetupStore.isPending(context, PROBE));
            assertNull(AppDatabase.getInstance(context).appPolicyDao().getByPackage(PROBE));
        } finally {
            finishActivity(instrumentation, editor);
            restoreQueueMembership(context, PROBE, wasPending);
        }
    }

    @Test
    public void reviewNextOpensPendingPackageInPolicyEditorWithoutSaving() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        // The test APK was installed by this disposable emulator workflow.
        String installedFixture = instrumentation.getContext().getPackageName();
        assertTrue(NewAppSetupCoordinator.isEligible(context, installedFixture));

        AppDatabase db = AppDatabase.getInstance(context);
        AppPolicy originalPolicy = db.appPolicyDao().getByPackage(installedFixture);
        boolean wasPending = NewAppSetupStore.isPending(context, installedFixture);
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        boolean hadMode = prefs.contains(PreferenceKeys.KEY_NEW_APP_SETUP_MODE);
        int oldMode = NewAppSetupStore.getMode(context);

        Activity settings = null;
        Activity editor = null;
        Instrumentation.ActivityMonitor monitor =
                new Instrumentation.ActivityMonitor(
                        AppPolicyEditorActivity.class.getName(), null, false);
        instrumentation.addMonitor(monitor);
        try {
            NewAppSetupStore.setMode(context, NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL);
            NewAppSetupStore.addPending(context, installedFixture);

            Intent settingsIntent = new Intent(context, NewAppSetupSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            settings = instrumentation.startActivitySync(settingsIntent);
            instrumentation.waitForIdleSync();
            assertTrue(settings instanceof NewAppSetupSettingsActivity);
            Button reviewNext = settings.findViewById(R.id.new_app_setup_review_next);
            assertNotNull(reviewNext);

            // Preset loading and queue replay are asynchronous. The installed
            // fixture stays valid even if stale entries get pruned.
            waitUntilEnabled(instrumentation, reviewNext);
            List<String> pending = NewAppSetupStore.getPending(context);
            assertTrue(pending.contains(installedFixture));
            String expectedFirst = pending.get(0);

            instrumentation.runOnMainSync(reviewNext::performClick);
            editor = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull("Review next must open the policy editor", editor);
            assertEquals(expectedFirst, editor.getIntent().getStringExtra(
                    AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
            assertTrue(NewAppSetupStore.isPending(context, installedFixture));
        } finally {
            instrumentation.removeMonitor(monitor);
            finishActivity(instrumentation, editor);
            finishActivity(instrumentation, settings);
            restoreQueueMembership(context, installedFixture, wasPending);
            if (originalPolicy == null) {
                db.appPolicyDao().deleteByPackage(installedFixture);
            } else {
                db.appPolicyDao().upsert(originalPolicy);
            }
            SharedPreferences.Editor restore = prefs.edit();
            if (hadMode) restore.putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE, oldMode);
            else restore.remove(PreferenceKeys.KEY_NEW_APP_SETUP_MODE);
            restore.commit();
        }
    }

    private static void waitUntilEnabled(Instrumentation instrumentation, Button button)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        AtomicBoolean enabled = new AtomicBoolean();
        do {
            instrumentation.runOnMainSync(() -> enabled.set(button.isEnabled()));
            if (enabled.get()) return;
            Thread.sleep(50L);
        } while (System.currentTimeMillis() < deadline);
        assertTrue("Review next must become enabled with an installed pending package",
                enabled.get());
    }

    private static void restoreQueueMembership(
            Context context, String packageName, boolean wasPending) {
        if (wasPending) NewAppSetupStore.addPending(context, packageName);
        else NewAppSetupStore.removePending(context, packageName);
    }

    private static void finishActivity(Instrumentation instrumentation, Activity activity) {
        if (activity == null) return;
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }
}
