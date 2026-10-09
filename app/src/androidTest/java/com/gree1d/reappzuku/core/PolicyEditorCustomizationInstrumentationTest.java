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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Disposable-emulator proof for the actual Policy Editor UI and persisted preset provenance. */
public class PolicyEditorCustomizationInstrumentationTest {
    private static final String FOCUS_PACKAGE = "com.reappzuku.presetfocusprobe";
    private static final String OVERRIDE_PACKAGE = "com.reappzuku.presetoverrideprobe";

    @Test
    public void focusAndEditThenRevertDoNotPersistSpuriousCustomization() throws Exception {
        checkEditorRoundTrip(FOCUS_PACKAGE, false);
    }

    @Test
    public void preexistingCustomizedPresetIsNotOverwrittenOnSpinnerInitialization()
            throws Exception {
        checkEditorRoundTrip(OVERRIDE_PACKAGE, true);
    }

    private static void checkEditorRoundTrip(String packageName, boolean existingOverride)
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        AppDatabase db = AppDatabase.getInstance(context);
        assertNull("Use only test-owned policy IDs", db.appPolicyDao().getByPackage(packageName));

        Activity activity = null;
        try {
            PolicyPresetSeeder.seedBuiltIns(db);
            PolicyPreset preset = db.policyPresetDao().getById(PolicyPresetSeeder.PRESET_BALANCED);
            assertNotNull(preset);
            long initial = existingOverride ? preset.standbyDelayMs + 60_000L
                    : preset.standbyDelayMs;
            AppPolicy policy = AppPolicyEditorModel.fromPreset(packageName, preset, 1L);
            policy.standbyDelayMs = initial;
            policy.customized = existingOverride;
            db.appPolicyDao().upsert(policy);

            Intent intent = new Intent(context, AppPolicyEditorActivity.class);
            intent.putExtra(AppPolicyEditorActivity.EXTRA_PACKAGE_NAME, packageName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = instrumentation.startActivitySync(intent);
            assertTrue(activity instanceof AppPolicyEditorActivity);
            Button save = activity.findViewById(R.id.policy_save);
            EditText standby = activity.findViewById(R.id.policy_standby_minutes);
            Spinner spinner = activity.findViewById(R.id.policy_preset);
            TextView status = activity.findViewById(R.id.policy_preset_status);
            assertNotNull(save);
            assertNotNull(standby);
            assertNotNull(spinner);
            assertNotNull(status);
            waitForEnabled(instrumentation, save);
            instrumentation.waitForIdleSync();

            AtomicInteger selectedPosition = new AtomicInteger();
            AtomicReference<String> displayed = new AtomicReference<>();
            AtomicInteger visible = new AtomicInteger();
            instrumentation.runOnMainSync(() -> {
                selectedPosition.set(spinner.getSelectedItemPosition());
                displayed.set(standby.getText().toString());
                visible.set(status.getVisibility());
            });
            assertTrue("The persisted preset must remain selected", selectedPosition.get() > 0);
            assertEquals(Long.toString(initial / 60_000L), displayed.get());
            assertEquals(existingOverride ? View.VISIBLE : View.GONE, visible.get());

            long expected = preset.standbyDelayMs;
            instrumentation.runOnMainSync(() -> {
                standby.requestFocus();
                if (!existingOverride) {
                    standby.setText(Long.toString((expected + 60_000L) / 60_000L));
                    assertEquals(View.VISIBLE, status.getVisibility());
                }
                standby.setText(Long.toString(expected / 60_000L));
                assertEquals(View.GONE, status.getVisibility());
                save.performClick();
            });

            AppPolicy persisted = waitForSaved(db, packageName);
            assertEquals(Long.valueOf(preset.id), persisted.presetId);
            assertFalse("Reverted values must not retain a stale customized flag",
                    persisted.customized);
            assertEquals(expected, persisted.standbyDelayMs);
        } finally {
            if (activity != null) {
                Activity toFinish = activity;
                instrumentation.runOnMainSync(toFinish::finish);
                instrumentation.waitForIdleSync();
            }
            db.appPolicyDao().deleteByPackage(packageName);
        }
    }

    private static AppPolicy waitForSaved(AppDatabase db, String packageName)
            throws Exception {
        long deadline = System.currentTimeMillis() + 8_000L;
        while (System.currentTimeMillis() < deadline) {
            AppPolicy policy = db.appPolicyDao().getByPackage(packageName);
            if (policy != null && policy.updatedAt > 1L && !policy.customized) {
                return policy;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Edited policy not persisted with matching preset provenance");
    }

    private static void waitForEnabled(
            Instrumentation instrumentation, Button button) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        AtomicBoolean enabled = new AtomicBoolean(false);
        do {
            instrumentation.runOnMainSync(() -> enabled.set(button.isEnabled()));
            if (enabled.get()) return;
            Thread.sleep(50L);
        } while (System.currentTimeMillis() < deadline);
        throw new AssertionError("Policy Editor did not finish loading");
    }
}
