package com.gree1d.reappzuku.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.AppPolicyEditorModel;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.core.PackageNameValidator;
import com.gree1d.reappzuku.core.PolicyPresetSeeder;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import com.gree1d.reappzuku.db.PolicyPreset;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppPolicyEditorActivity extends BaseActivity {
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    private static final long MINUTE_MS = 60_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppDatabase database;
    private String packageName;
    private AppPolicy loadedPolicy;
    private final List<PolicyPreset> presets = new ArrayList<>();
    private boolean bindingUi;
    private boolean presetSelectionReady;
    private boolean customized = true;

    private TextView packageView;
    private Spinner presetSpinner;
    private Spinner strategySpinner;
    private EditText standbyMinutes;
    private EditText forceStopMinutes;
    private Spinner killMethodSpinner;
    private Spinner restrictionSpinner;
    private CheckBox bootCleanup;
    private CheckBox protectMedia;
    private CheckBox protectForegroundServices;
    private CheckBox protectWidgets;
    private CheckBox triggerPeriodic;
    private CheckBox triggerScreenOff;
    private CheckBox triggerRam;
    private CheckBox triggerBoot;
    private CheckBox triggerHardware;
    private CheckBox triggerAppLaunch;
    private Button saveButton;
    private Button savePresetButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_policy_editor);

        packageName = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        if (packageName == null || !PackageNameValidator.isValid(packageName)) {
            Toast.makeText(this, R.string.policy_editor_invalid_package, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        database = AppDatabase.getInstance(this);
        bindViews();
        configureStaticAdapters();
        configureListeners();
        packageView.setText(packageName);
        loadPolicy();
    }

    private void bindViews() {
        packageView = findViewById(R.id.policy_package);
        presetSpinner = findViewById(R.id.policy_preset);
        strategySpinner = findViewById(R.id.policy_strategy);
        standbyMinutes = findViewById(R.id.policy_standby_minutes);
        forceStopMinutes = findViewById(R.id.policy_force_stop_minutes);
        killMethodSpinner = findViewById(R.id.policy_kill_method);
        restrictionSpinner = findViewById(R.id.policy_background_restriction);
        bootCleanup = findViewById(R.id.policy_boot_cleanup);
        protectMedia = findViewById(R.id.policy_protect_media);
        protectForegroundServices = findViewById(R.id.policy_protect_fgs);
        protectWidgets = findViewById(R.id.policy_protect_widgets);
        triggerPeriodic = findViewById(R.id.policy_trigger_periodic);
        triggerScreenOff = findViewById(R.id.policy_trigger_screen_off);
        triggerRam = findViewById(R.id.policy_trigger_ram);
        triggerBoot = findViewById(R.id.policy_trigger_boot);
        triggerHardware = findViewById(R.id.policy_trigger_hardware);
        triggerAppLaunch = findViewById(R.id.policy_trigger_app_launch);
        saveButton = findViewById(R.id.policy_save);
        savePresetButton = findViewById(R.id.policy_save_preset);

        findViewById(R.id.policy_cancel).setOnClickListener(v -> finish());
    }

    private void configureStaticAdapters() {
        strategySpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.policy_editor_strategies)));
        killMethodSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.policy_editor_kill_methods)));
        restrictionSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                getResources().getStringArray(R.array.policy_editor_restrictions)));
    }

    private void configureListeners() {
        presetSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            if (!presetSelectionReady
                    || bindingUi
                    || position <= 0
                    || position - 1 >= presets.size()) {
                return;
            }
            PolicyPreset preset = presets.get(position - 1);
            AppPolicy preview = AppPolicyEditorModel.fromPreset(
                    packageName, preset, System.currentTimeMillis());
            customized = false;
            bindPolicyValues(preview);
        }));

        View.OnFocusChangeListener markCustomOnEdit = (v, hasFocus) -> {
            if (hasFocus && !bindingUi) markCustomized();
        };
        standbyMinutes.setOnFocusChangeListener(markCustomOnEdit);
        forceStopMinutes.setOnFocusChangeListener(markCustomOnEdit);

        android.widget.AdapterView.OnItemSelectedListener markCustomSelection =
                new SimpleItemSelectedListener(position -> {
                    if (!bindingUi) markCustomized();
                });
        strategySpinner.setOnItemSelectedListener(markCustomSelection);
        killMethodSpinner.setOnItemSelectedListener(markCustomSelection);
        restrictionSpinner.setOnItemSelectedListener(markCustomSelection);

        for (CheckBox box : allChecks()) {
            box.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!bindingUi) markCustomized();
            });
        }

        saveButton.setOnClickListener(v -> savePolicy(false, null));
        savePresetButton.setOnClickListener(v -> promptSaveAsPreset());
    }

    private List<CheckBox> allChecks() {
        List<CheckBox> result = new ArrayList<>();
        result.add(bootCleanup);
        result.add(protectMedia);
        result.add(protectForegroundServices);
        result.add(protectWidgets);
        result.add(triggerPeriodic);
        result.add(triggerScreenOff);
        result.add(triggerRam);
        result.add(triggerBoot);
        result.add(triggerHardware);
        result.add(triggerAppLaunch);
        return result;
    }

    private void loadPolicy() {
        setEnabled(false);
        executor.execute(() -> {
            PolicyPresetSeeder.seedBuiltIns(database);
            List<PolicyPreset> loadedPresets = database.policyPresetDao().getAll();
            AppPolicy policy = database.appPolicyDao().getByPackage(packageName);
            long now = System.currentTimeMillis();
            if (policy == null) {
                policy = AppPolicyEditorModel.defaultPolicy(packageName, now);
            }
            AppPolicy finalPolicy = policy;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                presets.clear();
                presets.addAll(loadedPresets);
                presetSelectionReady = false;
                bindPresetAdapter(finalPolicy);
                bindPolicyValues(finalPolicy);
                loadedPolicy = finalPolicy;
                customized = finalPolicy.customized || finalPolicy.presetId == null;
                setEnabled(true);
                presetSpinner.post(() -> presetSelectionReady = true);
            });
        });
    }

    private void bindPresetAdapter(AppPolicy policy) {
        List<String> names = new ArrayList<>();
        names.add(getString(R.string.policy_editor_custom));
        for (PolicyPreset preset : presets) names.add(displayPresetName(preset));
        bindingUi = true;
        presetSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, names));

        int selection = 0;
        if (policy.presetId != null) {
            for (int i = 0; i < presets.size(); i++) {
                if (presets.get(i).id == policy.presetId) {
                    selection = i + 1;
                    break;
                }
            }
        }
        presetSpinner.setSelection(selection, false);
        bindingUi = false;
    }

    private String displayPresetName(PolicyPreset preset) {
        if (!preset.builtIn) return preset.name;
        if (preset.id == PolicyPresetSeeder.PRESET_NEVER_TOUCH) {
            return getString(R.string.policy_preset_never_touch);
        }
        if (preset.id == PolicyPresetSeeder.PRESET_MESSENGER) {
            return getString(R.string.policy_preset_messenger);
        }
        if (preset.id == PolicyPresetSeeder.PRESET_MEDIA) {
            return getString(R.string.policy_preset_media);
        }
        if (preset.id == PolicyPresetSeeder.PRESET_BALANCED) {
            return getString(R.string.policy_preset_balanced);
        }
        if (preset.id == PolicyPresetSeeder.PRESET_RARELY_USED) {
            return getString(R.string.policy_preset_rarely_used);
        }
        if (preset.id == PolicyPresetSeeder.PRESET_AGGRESSIVE) {
            return getString(R.string.policy_preset_aggressive);
        }
        return preset.name;
    }

    private void bindPolicyValues(AppPolicy policy) {
        bindingUi = true;
        strategySpinner.setSelection(policy.strategy, false);
        standbyMinutes.setText(formatMinutes(policy.standbyDelayMs));
        forceStopMinutes.setText(formatMinutes(policy.forceStopDelayMs));
        killMethodSpinner.setSelection(policy.killMethod, false);
        restrictionSpinner.setSelection(policy.backgroundRestriction, false);
        bootCleanup.setChecked(policy.bootCleanup);
        protectMedia.setChecked(policy.protectMedia);
        protectForegroundServices.setChecked(policy.protectForegroundServices);
        protectWidgets.setChecked(policy.protectWidgets);
        triggerPeriodic.setChecked(hasTrigger(policy, AppPolicy.TRIGGER_PERIODIC));
        triggerScreenOff.setChecked(hasTrigger(policy, AppPolicy.TRIGGER_SCREEN_OFF));
        triggerRam.setChecked(hasTrigger(policy, AppPolicy.TRIGGER_RAM_THRESHOLD));
        triggerBoot.setChecked(hasTrigger(policy, AppPolicy.TRIGGER_BOOT_CLEANUP));
        triggerHardware.setChecked(hasTrigger(policy, AppPolicy.TRIGGER_HARDWARE_EVENT));
        triggerAppLaunch.setChecked(hasTrigger(policy, AppPolicy.TRIGGER_APP_LAUNCH));
        bindingUi = false;
    }

    private void markCustomized() {
        customized = true;
    }

    private void promptSaveAsPreset() {
        EditText input = new EditText(this);
        input.setHint(R.string.policy_editor_preset_name_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int horizontal = Math.round(24 * getResources().getDisplayMetrics().density);
        input.setPadding(horizontal, 0, horizontal, 0);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.policy_editor_save_as_preset)
                .setView(input)
                .setPositiveButton(R.string.policy_editor_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.length() > 80) {
                        input.setError(getString(R.string.policy_editor_invalid_preset_name));
                        return;
                    }
                    dialog.dismiss();
                    savePolicy(true, name);
                }));
        dialog.show();
    }

    private void savePolicy(boolean saveAsPreset, @Nullable String presetName) {
        AppPolicy policy;
        try {
            policy = readPolicyFromUi();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        setEnabled(false);
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            policy.updatedAt = now;
            if (loadedPolicy != null && loadedPolicy.createdAt > 0L) {
                policy.createdAt = loadedPolicy.createdAt;
            } else if (policy.createdAt <= 0L) {
                policy.createdAt = now;
            }

            if (saveAsPreset && presetName != null) {
                PolicyPreset preset = AppPolicyEditorModel.presetFromPolicy(presetName, policy, now);
                long id = database.policyPresetDao().upsert(preset);
                policy.presetId = id;
                policy.customized = false;
            }

            database.appPolicyDao().upsert(AppPolicyEditorModel.normalize(policy));
            NewAppSetupStore.removePending(this, packageName);
            NewAppSetupNotifier.cancel(this, packageName);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(
                        this,
                        saveAsPreset
                                ? R.string.policy_editor_preset_saved
                                : R.string.policy_editor_saved,
                        Toast.LENGTH_SHORT).show();
                if (saveAsPreset) {
                    loadPolicy();
                } else {
                    finish();
                }
            });
        });
    }

    private AppPolicy readPolicyFromUi() {
        long now = System.currentTimeMillis();
        AppPolicy policy = AppPolicyEditorModel.defaultPolicy(packageName, now);
        policy.strategy = strategySpinner.getSelectedItemPosition();
        policy.standbyDelayMs = parseMinutes(standbyMinutes, false);
        policy.forceStopDelayMs = parseMinutes(forceStopMinutes, true);
        policy.killMethod = killMethodSpinner.getSelectedItemPosition();
        policy.backgroundRestriction = restrictionSpinner.getSelectedItemPosition();
        policy.bootCleanup = bootCleanup.isChecked();
        policy.protectMedia = protectMedia.isChecked();
        policy.protectForegroundServices = protectForegroundServices.isChecked();
        policy.protectWidgets = protectWidgets.isChecked();
        policy.triggerMask = buildTriggerMask();

        int presetPosition = presetSpinner.getSelectedItemPosition();
        if (presetPosition > 0 && presetPosition - 1 < presets.size()) {
            PolicyPreset selected = presets.get(presetPosition - 1);
            policy.presetId = selected.id;
            policy.customized =
                    customized || !AppPolicyEditorModel.matchesPreset(policy, selected);
        } else {
            policy.presetId = null;
            policy.customized = true;
        }
        policy.source = AppPolicy.SOURCE_EXPLICIT;
        return AppPolicyEditorModel.normalize(policy);
    }

    private long buildTriggerMask() {
        long mask = 0L;
        if (triggerPeriodic.isChecked()) mask |= AppPolicy.TRIGGER_PERIODIC;
        if (triggerScreenOff.isChecked()) mask |= AppPolicy.TRIGGER_SCREEN_OFF;
        if (triggerRam.isChecked()) mask |= AppPolicy.TRIGGER_RAM_THRESHOLD;
        if (triggerBoot.isChecked()) mask |= AppPolicy.TRIGGER_BOOT_CLEANUP;
        if (triggerHardware.isChecked()) mask |= AppPolicy.TRIGGER_HARDWARE_EVENT;
        if (triggerAppLaunch.isChecked()) mask |= AppPolicy.TRIGGER_APP_LAUNCH;
        return mask;
    }

    private long parseMinutes(EditText field, boolean allowNever) {
        String raw = field.getText().toString().trim();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException(getString(R.string.policy_editor_delay_required));
        }
        try {
            long minutes = Long.parseLong(raw);
            if (allowNever && minutes == -1L) return Long.MAX_VALUE;
            if (minutes < 0L || minutes > Long.MAX_VALUE / MINUTE_MS) {
                throw new NumberFormatException();
            }
            return Math.multiplyExact(minutes, MINUTE_MS);
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException(getString(R.string.policy_editor_invalid_delay));
        }
    }

    private String formatMinutes(long millis) {
        return millis == Long.MAX_VALUE ? "-1" : Long.toString(millis / MINUTE_MS);
    }

    private boolean hasTrigger(AppPolicy policy, long bit) {
        return (policy.triggerMask & bit) != 0L;
    }

    private void setEnabled(boolean enabled) {
        if (saveButton == null) return;
        presetSpinner.setEnabled(enabled);
        strategySpinner.setEnabled(enabled);
        standbyMinutes.setEnabled(enabled);
        forceStopMinutes.setEnabled(enabled);
        killMethodSpinner.setEnabled(enabled);
        restrictionSpinner.setEnabled(enabled);
        for (CheckBox box : allChecks()) box.setEnabled(enabled);
        saveButton.setEnabled(enabled);
        savePresetButton.setEnabled(enabled);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        interface Callback {
            void onSelected(int position);
        }

        private final Callback callback;

        SimpleItemSelectedListener(Callback callback) {
            this.callback = callback;
        }

        @Override
        public void onItemSelected(
                android.widget.AdapterView<?> parent,
                View view,
                int position,
                long id) {
            callback.onSelected(position);
        }

        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {
        }
    }
}