package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;
import com.gree1d.reappzuku.ui.NewAppSetupSettingsActivity;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Disposable API37 runtime proof. Unlike fake uninstalled package names, both
 * installed non-system fixtures survive the real inventory-reconcile Worker.
 * Original queue membership, setup mode and both Room policy rows are restored.
 */
public final class NewAppSetupReviewInstrumentationTest {
    private static final String EXTERNAL_PROBE = "com.reappzuku.securityprobe";

    @Test
    public void multiplePendingAppsRemainIndependentlyReviewableAfterSavingOne()
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        AppDatabase db = AppDatabase.getInstance(context);
        String first = instrumentation.getContext().getPackageName();
        String second = EXTERNAL_PROBE;
        Fixture fixture = new Fixture(context, db, first, second);
        Activity settings = null;
        Activity editor = null;
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                AppPolicyEditorActivity.class.getName(), null, false);
        instrumentation.addMonitor(monitor);
        try {
            fixture.prepare();
            settings = instrumentation.startActivitySync(
                    new Intent(context, NewAppSetupSettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            assertNotNull(settings);
            Button reviewNext = settings.findViewById(R.id.new_app_setup_review_next);
            LinearLayout rows = settings.findViewById(R.id.new_app_setup_pending_apps);
            assertNotNull(rows);
            assertNotNull(reviewNext);
            assertTrue("Both installed test fixtures should fit the bounded preview",
                    rows.getChildCount() >= 2);
            Button firstRow = findRow(rows, first);
            assertNotNull("The first installed package must be individually reviewable", firstRow);
            instrumentation.runOnMainSync(firstRow::performClick);
            editor = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull(editor);
            assertEquals(first, editor.getIntent().getStringExtra(
                    AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));

            Activity savedEditor = editor;
            waitForEnabled(instrumentation, savedEditor.findViewById(R.id.policy_save));
            instrumentation.runOnMainSync(
                    () -> savedEditor.findViewById(R.id.policy_save).performClick());
            long deadline = System.currentTimeMillis() + 8000L;
            while (NewAppSetupStore.isPending(context, first)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50L);
            }
            assertFalse(NewAppSetupStore.isPending(context, first));
            assertNotNull(db.appPolicyDao().getByPackage(first));
            assertTrue(NewAppSetupStore.isPending(context, second));

            instrumentation.runOnMainSync(savedEditor::finish);
            instrumentation.waitForIdleSync();
            editor = null;
            waitForRowGone(instrumentation, rows, first);

            List<String> remaining = NewAppSetupStore.getPending(context);
            String expectedNext = remaining.get(0);
            instrumentation.runOnMainSync(reviewNext::performClick);
            editor = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull(editor);
            assertEquals("Review next must use the current durable sorted queue",
                    expectedNext, editor.getIntent().getStringExtra(
                            AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
            assertTrue("Review without saving must leave the other entry queued",
                    NewAppSetupStore.isPending(context, second));
        } finally {
            instrumentation.removeMonitor(monitor);
            finish(instrumentation, editor);
            finish(instrumentation, settings);
            fixture.restore();
        }
    }

    @Test
    public void changingModeWithPendingAppsCanBeCancelledWithoutReplayingQueue()
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        AppDatabase db = AppDatabase.getInstance(context);
        String first = instrumentation.getContext().getPackageName();
        String second = EXTERNAL_PROBE;
        Fixture fixture = new Fixture(context, db, first, second);
        Activity settings = null;
        try {
            fixture.prepare();
            settings = instrumentation.startActivitySync(
                    new Intent(context, NewAppSetupSettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            Spinner mode = settings.findViewById(R.id.new_app_setup_mode);
            Button reviewNext = settings.findViewById(R.id.new_app_setup_review_next);
            waitForEnabled(instrumentation, reviewNext);
            waitForSpinnerReady(instrumentation, mode);

            int alternate = NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET;
            instrumentation.runOnMainSync(() -> mode.setSelection(alternate));
            instrumentation.waitForIdleSync();
            assertEquals("Mode cannot persist before confirmation",
                    NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL,
                    NewAppSetupStore.getMode(context));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            instrumentation.waitForIdleSync();
            AtomicInteger selected = new AtomicInteger(-1);
            instrumentation.runOnMainSync(
                    () -> selected.set(mode.getSelectedItemPosition()));
            assertEquals(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL, selected.get());
            assertEquals(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL,
                    NewAppSetupStore.getMode(context));
            assertTrue(NewAppSetupStore.isPending(context, first));
            assertTrue(NewAppSetupStore.isPending(context, second));
        } finally {
            finish(instrumentation, settings);
            fixture.restore();
        }
    }

    private static final class Fixture {
        private final Context context;
        private final AppDatabase database;
        private final String first;
        private final String second;
        private final AppPolicy oldFirst;
        private final AppPolicy oldSecond;
        private final Set<String> queueBefore;
        private final SharedPreferences preferences;
        private final boolean hadMode;
        private final int previousMode;

        Fixture(Context context, AppDatabase database, String first, String second) {
            this.context = context;
            this.database = database;
            this.first = first;
            this.second = second;
            this.oldFirst = database.appPolicyDao().getByPackage(first);
            this.oldSecond = database.appPolicyDao().getByPackage(second);
            this.queueBefore = new HashSet<>(NewAppSetupStore.getPending(context));
            this.preferences = context.getSharedPreferences(
                    PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
            this.hadMode = preferences.contains(PreferenceKeys.KEY_NEW_APP_SETUP_MODE);
            this.previousMode = NewAppSetupStore.getMode(context);
        }

        void prepare() throws Exception {
            assertTrue("Instrumentation package must be an installed eligible fixture",
                    NewAppSetupCoordinator.isEligible(context, first));
            assertTrue("External security probe must be an installed eligible fixture",
                    NewAppSetupCoordinator.isEligible(context, second));
            waitForSharedExecutor();
            NewAppSetupStore.setMode(context, NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL);
            // A canonical UNMANAGED pending placeholder cannot be mistaken for
            // an already completed user decision by concurrent startup replay.
            makePending(first);
            makePending(second);
            assertTrue(NewAppSetupStore.isPending(context, first));
            assertTrue(NewAppSetupStore.isPending(context, second));
        }

        private void makePending(String packageName) {
            AppPolicy placeholder = AppPolicyEditorModel.defaultPolicy(
                    packageName, System.currentTimeMillis());
            placeholder.customized = true;
            database.appPolicyDao().upsert(placeholder);
            NewAppSetupStore.addPending(context, packageName);
        }

        void restore() throws Exception {
            waitForSharedExecutor();
            if (oldFirst == null) database.appPolicyDao().deleteByPackage(first);
            else database.appPolicyDao().upsert(oldFirst);
            if (oldSecond == null) database.appPolicyDao().deleteByPackage(second);
            else database.appPolicyDao().upsert(oldSecond);
            restoreMembership(first);
            restoreMembership(second);
            SharedPreferences.Editor edit = preferences.edit();
            if (hadMode) edit.putInt(PreferenceKeys.KEY_NEW_APP_SETUP_MODE, previousMode);
            else edit.remove(PreferenceKeys.KEY_NEW_APP_SETUP_MODE);
            assertTrue("Restore original setup mode", edit.commit());
            assertEquals("All unrelated pending packages must be preserved",
                    queueBefore, new HashSet<>(NewAppSetupStore.getPending(context)));
        }

        private void restoreMembership(String packageName) {
            if (queueBefore.contains(packageName)) NewAppSetupStore.addPending(context, packageName);
            else NewAppSetupStore.removePending(context, packageName);
        }

        private void waitForSharedExecutor() throws Exception {
            ((App) context.getApplicationContext()).getSharedExecutor()
                    .submit(() -> {}).get(60, TimeUnit.SECONDS);
        }
    }

    private static void waitForRowGone(
            Instrumentation instrumentation, LinearLayout rows, String packageName)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            AtomicInteger present = new AtomicInteger();
            instrumentation.runOnMainSync(
                    () -> present.set(findRow(rows, packageName) == null ? 0 : 1));
            if (present.get() == 0) return;
            Thread.sleep(50L);
        }
        throw new AssertionError("Resolved entry remained visible after returning from Editor");
    }

    private static Button findRow(LinearLayout list, String packageName) {
        for (int i = 0; i < list.getChildCount(); i++) {
            View view = list.getChildAt(i);
            if (view instanceof Button
                    && ((Button) view).getText().toString().contains(packageName)) {
                return (Button) view;
            }
        }
        return null;
    }

    private static void waitForSpinnerReady(Instrumentation instrumentation, Spinner spinner)
            throws Exception {
        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline) {
            AtomicInteger ready = new AtomicInteger();
            instrumentation.runOnMainSync(() -> ready.set(spinner.isEnabled() ? 1 : 0));
            if (ready.get() == 1) return;
            Thread.sleep(50L);
        }
        throw new AssertionError("New-app setup mode never finished initializing");
    }

    private static void waitForEnabled(Instrumentation instrumentation, Button button)
            throws Exception {
        assertNotNull(button);
        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline) {
            AtomicInteger ready = new AtomicInteger();
            instrumentation.runOnMainSync(() -> ready.set(button.isEnabled() ? 1 : 0));
            if (ready.get() == 1) return;
            Thread.sleep(50L);
        }
        throw new AssertionError("Review/Save button remained disabled");
    }

    private static void finish(Instrumentation instrumentation, Activity activity) {
        if (activity == null) return;
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }
}
