package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;
import com.gree1d.reappzuku.ui.NewAppSetupSettingsActivity;

import org.junit.Test;

import java.util.List;

/**
 * Android 7 policy-editor navigation regression checks.
 * The fixture is a synthetic package and is never saved as a policy. Queue
 * cleanup is scoped to this fixture only, preserving all other entries.
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
            restoreProbeQueue(context, wasPending);
        }
    }

    @Test
    public void reviewNextOpensPendingPackageInPolicyEditorWithoutSaving() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        boolean wasPending = NewAppSetupStore.isPending(context, PROBE);
        Activity settings = null;
        Activity editor = null;
        Instrumentation.ActivityMonitor monitor =
                new Instrumentation.ActivityMonitor(
                        AppPolicyEditorActivity.class.getName(), null, false);
        instrumentation.addMonitor(monitor);
        try {
            NewAppSetupStore.addPending(context, PROBE);
            List<String> pending = NewAppSetupStore.getPending(context);
            assertTrue(pending.contains(PROBE));
            String expectedFirst = pending.get(0);

            Intent settingsIntent = new Intent(context, NewAppSetupSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            settings = instrumentation.startActivitySync(settingsIntent);
            instrumentation.waitForIdleSync();
            assertTrue(settings instanceof NewAppSetupSettingsActivity);
            Button reviewNext = settings.findViewById(R.id.new_app_setup_review_next);
            assertNotNull(reviewNext);
            assertTrue(reviewNext.isEnabled());

            instrumentation.runOnMainSync(reviewNext::performClick);
            editor = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull("Review next must open the policy editor", editor);
            assertEquals(expectedFirst, editor.getIntent().getStringExtra(
                    AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
            assertTrue(NewAppSetupStore.isPending(context, PROBE));
        } finally {
            instrumentation.removeMonitor(monitor);
            finishActivity(instrumentation, editor);
            finishActivity(instrumentation, settings);
            restoreProbeQueue(context, wasPending);
        }
    }

    private static void restoreProbeQueue(Context context, boolean wasPending) {
        if (wasPending) NewAppSetupStore.addPending(context, PROBE);
        else NewAppSetupStore.removePending(context, PROBE);
    }

    private static void finishActivity(Instrumentation instrumentation, Activity activity) {
        if (activity == null) return;
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }
}
