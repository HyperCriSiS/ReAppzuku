package com.gree1d.reappzuku.ui;

import android.content.Intent;
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
        reviewNext = findViewById(R.id.new_app_setup_review_next);
        executor = ((App) getApplication()).getSharedExecutor();

        modeSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[] {
                        getString(R.string.new_app_setup_mode_ask),
                        getString(R.string.new_app_setup_mode_apply),
                        getString(R.string.new_app_setup_mode_leave)
                }));

        modeSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (bindingUi) return;
            NewAppSetupStore.setMode(this, position);
            updatePresetEnabled();
            replayPending();
        }));

        presetSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (bindingUi || position < 0 || position >= presets.size()) return;
            NewAppSetupStore.setDefaultPresetId(this, presets.get(position).id);
            if (NewAppSetupStore.getMode(this)
                    == NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET) {
                replayPending();
            }
        }));

        reviewNext.setOnClickListener(v -> {
            List<String> pending = NewAppSetupStore.getPending(this);
            if (pending.isEmpty()) return;
            Intent intent = new Intent(this, AppPolicyEditorActivity.class);
            intent.putExtra(AppPolicyEditorActivity.EXTRA_PACKAGE_NAME, pending.get(0));
            startActivity(intent);
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
        modeSpinner.setSelection(NewAppSetupStore.getMode(this), false);
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
                bindingUi = false;
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
        int count = NewAppSetupStore.getPending(this).size();
        pendingText.setText(getString(R.string.new_app_setup_pending_count, count));
        reviewNext.setEnabled(count > 0);
        reviewNext.setAlpha(count > 0 ? 1.0f : 0.5f);
    }

    private void replayPending() {
        executor.execute(() -> {
            NewAppSetupCoordinator.replayPending(this);
            runOnUiThread(this::updatePending);
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
