package com.gree1d.reappzuku.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.LinearLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.core.NewAppSetupCoordinator;
import com.gree1d.reappzuku.core.NewAppSetupPolicy;
import com.gree1d.reappzuku.core.NewAppSetupStore;
import com.gree1d.reappzuku.core.NewAppSetupReviewPolicy;
import com.gree1d.reappzuku.core.PolicyPresetSeeder;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.PolicyPreset;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class NewAppSetupSettingsActivity extends BaseActivity {
    private final List<PolicyPreset> presets = new ArrayList<>();
    private Spinner modeSpinner;
    private Spinner presetSpinner;
    private TextView pendingText;
    private TextView nextAppText;
    private TextView overflowText;
    private LinearLayout pendingApps;
    private int selectedModePosition = -1;
    private int selectedPresetPosition = -1;
    private boolean changeDialogOpen;
    private Button reviewNext;
    private ExecutorService executor;
    private boolean bindingUi = true;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_app_setup_settings);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.new_app_setup_settings_title);
        }

        modeSpinner = findViewById(R.id.new_app_setup_mode);
        presetSpinner = findViewById(R.id.new_app_setup_preset);
        pendingText = findViewById(R.id.new_app_setup_pending);
        nextAppText = findViewById(R.id.new_app_setup_next_app);
        pendingApps = findViewById(R.id.new_app_setup_pending_apps);
        overflowText = findViewById(R.id.new_app_setup_pending_overflow);
        reviewNext = findViewById(R.id.new_app_setup_review_next);
        executor = ((App) getApplication()).getSharedExecutor();
        // The initial asynchronous preset fetch must not accept visible but ignored edits.
        modeSpinner.setEnabled(false);
        presetSpinner.setEnabled(false);

        modeSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[] {
                        getString(R.string.new_app_setup_mode_ask),
                        getString(R.string.new_app_setup_mode_apply),
                        getString(R.string.new_app_setup_mode_leave)
                }));

        modeSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (bindingUi || position == selectedModePosition || changeDialogOpen) return;
            final int previous = selectedModePosition;
            if (NewAppSetupReviewPolicy.confirmModeChange(
                    previous, position, NewAppSetupStore.getPending(this).size())) {
                confirmPendingReplay(() -> {
                    selectedModePosition = position;
                    NewAppSetupStore.setMode(this, position);
                    updatePresetEnabled();
                    replayPending();
                }, () -> restoreSelection(modeSpinner, previous));
            } else {
                selectedModePosition = position;
                NewAppSetupStore.setMode(this, position);
                updatePresetEnabled();
                replayPending();
            }
        }));

        presetSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (bindingUi || changeDialogOpen || position == selectedPresetPosition
                    || position < 0 || position >= presets.size()) return;
            int previous = selectedPresetPosition;
            long newPresetId = presets.get(position).id;
            if (NewAppSetupReviewPolicy.confirmPresetChange(
                    NewAppSetupStore.getMode(this),
                    NewAppSetupStore.getDefaultPresetId(this),
                    newPresetId, NewAppSetupStore.getPending(this).size())) {
                confirmPendingReplay(() -> {
                    selectedPresetPosition = position;
                    NewAppSetupStore.setDefaultPresetId(this, newPresetId);
                    replayPending();
                }, () -> restoreSelection(presetSpinner, previous));
            } else {
                selectedPresetPosition = position;
                NewAppSetupStore.setDefaultPresetId(this, newPresetId);
            }
        }));

        reviewNext.setOnClickListener(v -> {
            List<String> pending = NewAppSetupStore.getPending(this);
            if (pending.isEmpty()) {
                updatePending();
                return;
            }
            openPendingApp(pending.get(0));
        });

        loadPresets();
    }

    @Override protected void onResume() {
        super.onResume();
        updatePending();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadPresets() {
        selectedModePosition = NewAppSetupStore.getMode(this);
        modeSpinner.setSelection(selectedModePosition, false);
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            PolicyPresetSeeder.seedBuiltIns(db);
            List<PolicyPreset> loaded = db.policyPresetDao().getAll();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                presets.clear();
                presets.addAll(loaded);
                List<String> names = new ArrayList<>();
                for (PolicyPreset preset : presets) names.add(displayPresetName(preset));
                presetSpinner.setAdapter(new ArrayAdapter<>(
                        this, android.R.layout.simple_spinner_dropdown_item, names));
                long selectedId = NewAppSetupStore.getDefaultPresetId(this);
                int selected = 0;
                for (int i = 0; i < presets.size(); i++) {
                    if (presets.get(i).id == selectedId) {
                        selected = i;
                        break;
                    }
                }
                presetSpinner.setSelection(selected, false);
                selectedPresetPosition = selected;
                bindingUi = false;
                modeSpinner.setEnabled(true);
                updatePresetEnabled();
                updatePending();
            });
        });
    }

    private String displayPresetName(PolicyPreset preset) {
        if (!preset.builtIn) return preset.name;
        if (preset.id == PolicyPresetSeeder.PRESET_NEVER_TOUCH) return getString(R.string.policy_preset_never_touch);
        if (preset.id == PolicyPresetSeeder.PRESET_MESSENGER) return getString(R.string.policy_preset_messenger);
        if (preset.id == PolicyPresetSeeder.PRESET_MEDIA) return getString(R.string.policy_preset_media);
        if (preset.id == PolicyPresetSeeder.PRESET_BALANCED) return getString(R.string.policy_preset_balanced);
        if (preset.id == PolicyPresetSeeder.PRESET_RARELY_USED) return getString(R.string.policy_preset_rarely_used);
        if (preset.id == PolicyPresetSeeder.PRESET_AGGRESSIVE) return getString(R.string.policy_preset_aggressive);
        return preset.name;
    }

    private void updatePresetEnabled() {
        boolean enabled = NewAppSetupStore.getMode(this)
                == NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET;
        presetSpinner.setEnabled(enabled);
        presetSpinner.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private void updatePending() {
        if (isFinishing() || isDestroyed()) return;
        List<String> pending = NewAppSetupStore.getPending(this);
        int count = pending.size();
        pendingText.setText(getString(R.string.new_app_setup_pending_count, count));
        reviewNext.setEnabled(count > 0);
        reviewNext.setAlpha(count > 0 ? 1.0f : 0.5f);
        nextAppText.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        pendingApps.removeAllViews();
        if (count > 0) {
            nextAppText.setText(getString(R.string.new_app_setup_next_app,
                    displayAppLabel(pending.get(0))));
        }
        // Bound the number of view allocations for a large restored queue.
        for (String packageName : NewAppSetupReviewPolicy.preview(pending)) {
            Button row = new Button(this);
            row.setAllCaps(false);
            String description = getString(
                    R.string.new_app_setup_review_app, displayAppLabel(packageName));
            row.setText(description);
            row.setContentDescription(description);
            row.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
            row.setOnClickListener(v -> openPendingApp(packageName));
            pendingApps.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        int remaining = count - NewAppSetupReviewPolicy.preview(pending).size();
        overflowText.setVisibility(remaining > 0 ? View.VISIBLE : View.GONE);
        if (remaining > 0) {
            overflowText.setText(getString(R.string.new_app_setup_more_pending, remaining));
        }
    }

    private String displayAppLabel(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence name = getPackageManager().getApplicationLabel(info);
            if (name != null && name.length() > 0
                    && !name.toString().equals(packageName)) {
                return name + " (" + packageName + ")";
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // A pending package may have been removed before durable reconciliation.
        }
        return packageName;
    }

    private void openPendingApp(String packageName) {
        // Another activity or Worker may have resolved the item since it was drawn.
        if (!NewAppSetupStore.isPending(this, packageName)) {
            updatePending();
            return;
        }
        Intent intent = new Intent(this, AppPolicyEditorActivity.class);
        intent.putExtra(AppPolicyEditorActivity.EXTRA_PACKAGE_NAME, packageName);
        startActivity(intent);
    }

    private void restoreSelection(Spinner spinner, int position) {
        bindingUi = true;
        spinner.setSelection(position, false);
        bindingUi = false;
        updatePresetEnabled();
    }

    private void confirmPendingReplay(Runnable confirmed, Runnable cancelled) {
        changeDialogOpen = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_app_setup_pending_reprocess_title)
                .setMessage(getString(R.string.new_app_setup_pending_reprocess_message,
                        NewAppSetupStore.getPending(this).size()))
                .setPositiveButton(R.string.new_app_setup_pending_reprocess_confirm,
                        (dialog, which) -> {
                            changeDialogOpen = false;
                            confirmed.run();
                        })
                .setNegativeButton(android.R.string.cancel,
                        (dialog, which) -> {
                            changeDialogOpen = false;
                            cancelled.run();
                        })
                .setOnCancelListener(dialog -> {
                    changeDialogOpen = false;
                    cancelled.run();
                })
                .show();
    }

    private void replayPending() {
        executor.execute(() -> {
            NewAppSetupCoordinator.replayPending(this);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) updatePending();
            });
        });
    }

    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        interface Callback { void onItemSelected(int position); }
        private final Callback callback;
        SimpleItemSelectedListener(Callback callback) { this.callback = callback; }
        @Override public void onItemSelected(
                android.widget.AdapterView<?> parent, View view, int position, long id) {
            callback.onItemSelected(position);
        }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}