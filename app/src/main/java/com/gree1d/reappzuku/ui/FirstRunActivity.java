package com.gree1d.reappzuku.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.core.CapabilityMatrix;
import com.gree1d.reappzuku.core.FirstRunPolicy;
import com.gree1d.reappzuku.core.NewAppSetupCoordinator;
import com.gree1d.reappzuku.core.NewAppSetupStore;
import com.gree1d.reappzuku.core.PreferenceKeys;

import java.util.concurrent.ExecutorService;

/**
 * One screen that says what the current access mode allows and asks how newly
 * installed apps should be handled. Apps that are already installed are not
 * changed here.
 */
public class FirstRunActivity extends BaseActivity {
    private ExecutorService executor;
    private RadioGroup choices;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_run);

        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.first_run_title);
        }
        App app = (App) getApplication();
        executor = app.getSharedExecutor();
        choices = findViewById(R.id.first_run_choices);

        if (savedInstanceState == null) {
            choices.check(buttonFor(FirstRunPolicy.choiceFor(
                    NewAppSetupStore.getMode(this), NewAppSetupStore.getDefaultPresetId(this))));
        }
        findViewById(R.id.first_run_done).setOnClickListener(v -> save());
        findViewById(R.id.first_run_later).setOnClickListener(v -> finishSetup());

        TextView mode = findViewById(R.id.first_run_mode);
        TextView limits = findViewById(R.id.first_run_limits);
        executor.execute(() -> {
            // Probing root must not run on the main thread.
            CapabilityMatrix matrix = CapabilityMatrix.current(this, app.getShellManager());
            String modeText = CapabilityText.mode(this, matrix);
            String limitsText = CapabilityText.limits(this, matrix);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                mode.setText(modeText);
                limits.setText(limitsText);
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void save() {
        int choice = choiceFor(choices.getCheckedRadioButtonId());
        NewAppSetupStore.setMode(this, FirstRunPolicy.newAppMode(choice));
        NewAppSetupStore.setDefaultPresetId(this, FirstRunPolicy.defaultPreset(choice));
        // Apps that were waiting for a decision follow the new mode.
        executor.execute(() -> NewAppSetupCoordinator.replayPending(getApplicationContext()));
        finishSetup();
    }

    private void finishSetup() {
        sharedPreferences.edit().putBoolean(PreferenceKeys.KEY_FIRST_RUN_DONE, true).apply();
        finish();
    }

    private static int buttonFor(int choice) {
        switch (choice) {
            case FirstRunPolicy.CHOICE_GENTLE: return R.id.first_run_choice_gentle;
            case FirstRunPolicy.CHOICE_BALANCED: return R.id.first_run_choice_balanced;
            default: return R.id.first_run_choice_ask;
        }
    }

    private static int choiceFor(int buttonId) {
        if (buttonId == R.id.first_run_choice_gentle) return FirstRunPolicy.CHOICE_GENTLE;
        if (buttonId == R.id.first_run_choice_balanced) return FirstRunPolicy.CHOICE_BALANCED;
        return FirstRunPolicy.CHOICE_ASK;
    }
}
