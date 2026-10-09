package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;
import com.gree1d.reappzuku.ui.NewAppSetupSettingsActivity;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-privileged UI tests for multi-item setup review and the retroactive action
 * confirmation. All fixture package names are reserved for this test only.
 */
public final class NewAppSetupReviewInstrumentationTest {
    private static final String FIRST = "com.reappzuku.aareviewprobe";
    private static final String SECOND = "com.reappzuku.bbreviewprobe";

    @Test
    public void multiplePendingAppsRemainIndependentlyReviewableAfterSavingOne()
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        AppDatabase db = AppDatabase.getInstance(context);
        assertNull(db.appPolicyDao().getByPackage(FIRST));
        assertNull(db.appPolicyDao().getByPackage(SECOND));
        Set<String> before = new HashSet<>(NewAppSetupStore.getPending(context));
        Activity settings = null;
        Activity editor = null;
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                AppPolicyEditorActivity.class.getName(), null, false);
        instrumentation.addMonitor(monitor);
        try {
            NewAppSetupStore.addPending(context, FIRST);
            NewAppSetupStore.addPending(context, SECOND);
            settings = instrumentation.startActivitySync(
                    new Intent(context, NewAppSetupSettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            assertNotNull(settings);
            Button reviewNext = settings.findViewById(R.id.new_app_setup_review_next);
            LinearLayout rows = settings.findViewById(R.id.new_app_setup_pending_apps);
            assertNotNull(rows);
            assertNotNull(reviewNext);
            assertTrue("Two test rows should fit within the bounded preview",
                    rows.getChildCount() >= 2);
            Button firstRow = findRow(rows, FIRST);
            assertNotNull(firstRow);
            instrumentation.runOnMainSync(firstRow::performClick);
            editor = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull(editor);
            assertEquals(FIRST, editor.getIntent().getStringExtra(
                    AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
            Activity firstEditor = editor;
            waitForEnabled(instrumentation, firstEditor.findViewById(R.id.policy_save));
            instrumentation.runOnMainSync(
                    () -> firstEditor.findViewById(R.id.policy_save).performClick());

            long deadline = System.currentTimeMillis() + 8000L;
            while (NewAppSetupStore.isPending(context, FIRST)
                    && System.currentTimeMillis() < deadline) {
                Thread.sleep(50L);
            }
            assertFalse(NewAppSetupStore.isPending(context, FIRST));
            assertNotNull(db.appPolicyDao().getByPackage(FIRST));
            assertTrue(NewAppSetupStore.isPending(context, SECOND));

            instrumentation.runOnMainSync(firstEditor::finish);
            instrumentation.waitForIdleSync();
            editor = null;
            instrumentation.waitForIdleSync();

            // Review-next always follows the current durable, sorted pending snapshot.
            List<String> remaining = NewAppSetupStore.getPending(context);
            String expectedNext = remaining.get(0);
            instrumentation.runOnMainSync(reviewNext::performClick);
            editor = instrumentation.waitForMonitorWithTimeout(monitor, 5000L);
            assertNotNull(editor);
            assertEquals(expectedNext, editor.getIntent().getStringExtra(
                    AppPolicyEditorActivity.EXTRA_PACKAGE_NAME));
            assertTrue("Opening review without save must keep the second entry",
                    NewAppSetupStore.isPending(context, SECOND));
        } finally {
            instrumentation.removeMonitor(monitor);
            finish(instrumentation, editor);
            finish(instrumentation, settings);
            db.appPolicyDao().deleteByPackage(FIRST);
            db.appPolicyDao().deleteByPackage(SECOND);
            restore(context, FIRST, before.contains(FIRST));
            restore(context, SECOND, before.contains(SECOND));
            assertEquals(before, new HashSet<>(NewAppSetupStore.getPending(context)));
        }
    }

    @Test
    public void changingModeWithPendingAppsCanBeCancelledWithoutReplayingQueue()
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        int originalMode = NewAppSetupStore.getMode(context);
        Set<String> before = new HashSet<>(NewAppSetupStore.getPending(context));
        Activity settings = null;
        try {
            NewAppSetupStore.addPending(context, FIRST);
            NewAppSetupStore.addPending(context, SECOND);
            settings = instrumentation.startActivitySync(
                    new Intent(context, NewAppSetupSettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            instrumentation.waitForIdleSync();
            Spinner mode = settings.findViewById(R.id.new_app_setup_mode);
            Button next = settings.findViewById(R.id.new_app_setup_review_next);
            waitForEnabled(instrumentation, next);
            final int alternate = originalMode == 0 ? 1 : 0;
            instrumentation.runOnMainSync(() -> mode.setSelection(alternate));
            instrumentation.waitForIdleSync();
            assertEquals("A proposed mode change must not commit before confirmation",
                    originalMode, NewAppSetupStore.getMode(context));
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            instrumentation.waitForIdleSync();
            AtomicInteger selection = new AtomicInteger(-1);
            instrumentation.runOnMainSync(
                    () -> selection.set(mode.getSelectedItemPosition()));
            assertEquals("Cancel must restore the original visible choice",
                    originalMode, selection.get());
            assertEquals(originalMode, NewAppSetupStore.getMode(context));
            Set<String> expected = new HashSet<>(before);
            expected.add(FIRST);
            expected.add(SECOND);
            assertEquals(expected, new HashSet<>(NewAppSetupStore.getPending(context)));
        } finally {
            finish(instrumentation, settings);
            restore(context, FIRST, before.contains(FIRST));
            restore(context, SECOND, before.contains(SECOND));
            assertEquals(before, new HashSet<>(NewAppSetupStore.getPending(context)));
        }
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

    private static void waitForEnabled(Instrumentation instrumentation, Button button)
            throws Exception {
        assertNotNull(button);
        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline) {
            AtomicInteger enabled = new AtomicInteger();
            instrumentation.runOnMainSync(() -> enabled.set(button.isEnabled() ? 1 : 0));
            if (enabled.get() == 1) return;
            Thread.sleep(50L);
        }
        throw new AssertionError("Review/Save button remained disabled");
    }

    private static void restore(Context context, String packageName, boolean existed) {
        if (existed) NewAppSetupStore.addPending(context, packageName);
        else NewAppSetupStore.removePending(context, packageName);
    }

    private static void finish(Instrumentation instrumentation, Activity activity) {
        if (activity == null) return;
        instrumentation.runOnMainSync(activity::finish);
        instrumentation.waitForIdleSync();
    }
}
