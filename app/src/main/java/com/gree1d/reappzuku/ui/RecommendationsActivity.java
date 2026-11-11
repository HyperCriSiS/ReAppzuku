package com.gree1d.reappzuku.ui;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.core.AppPolicyQuickChoice;
import com.gree1d.reappzuku.core.BaseActivity;
import com.gree1d.reappzuku.core.RecommendationEffect;
import com.gree1d.reappzuku.core.SuggestionEngine;
import com.gree1d.reappzuku.core.SuggestionLoader;

import java.util.List;
import java.util.concurrent.ExecutorService;

/** Lists what the app recommends from its own measurements. Nothing changes until the user applies one. */
public class RecommendationsActivity extends BaseActivity {
    private ExecutorService executor;
    private LinearLayout list;
    private TextView empty;
    private TextView effectsTitle;
    private LinearLayout effectsList;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recommendations);

        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.recommendations_title);
        }
        list = findViewById(R.id.recommendations_list);
        empty = findViewById(R.id.recommendations_empty);
        effectsTitle = findViewById(R.id.recommendations_effects_title);
        effectsList = findViewById(R.id.recommendations_effects_list);
        executor = ((App) getApplication()).getSharedExecutor();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reload() {
        executor.execute(() -> {
            List<SuggestionEngine.Suggestion> suggestions = SuggestionLoader.load(getApplicationContext());
            List<RecommendationEffect> effects = RecommendationEffect.loadReady(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                show(suggestions);
                showEffects(effects);
            });
        });
    }

    private void show(List<SuggestionEngine.Suggestion> suggestions) {
        list.removeAllViews();
        empty.setVisibility(suggestions.isEmpty() ? View.VISIBLE : View.GONE);
        PackageManager packageManager = getPackageManager();
        LayoutInflater inflater = getLayoutInflater();
        for (SuggestionEngine.Suggestion suggestion : suggestions) {
            View row = inflater.inflate(R.layout.item_recommendation, list, false);
            CharSequence name = suggestion.packageName;
            try {
                name = packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(suggestion.packageName, 0));
                ((ImageView) row.findViewById(R.id.recommendation_icon)).setImageDrawable(
                        packageManager.getApplicationIcon(suggestion.packageName));
            } catch (PackageManager.NameNotFoundException e) {
                // The row keeps the package name and no icon.
            }
            ((TextView) row.findViewById(R.id.recommendation_app)).setText(name);
            ((TextView) row.findViewById(R.id.recommendation_reason)).setText(reasonText(suggestion));
            View apply = row.findViewById(R.id.recommendation_apply);
            View dismiss = row.findViewById(R.id.recommendation_dismiss);
            // Every row has the same two button texts; a screen reader needs the app with them.
            apply.setContentDescription(getString(R.string.recommendations_apply) + ": " + name);
            dismiss.setContentDescription(getString(R.string.recommendations_dismiss) + ": " + name);
            apply.setOnClickListener(v -> {
                // A second tap must not run the change twice.
                apply.setEnabled(false);
                dismiss.setEnabled(false);
                apply(suggestion, row);
            });
            dismiss.setOnClickListener(v -> {
                SuggestionLoader.dismiss(this, suggestion);
                removeRow(row);
            });
            list.addView(row);
        }
    }

    /** What earlier changes did; each row offers Keep, and Undo when nothing clear changed. */
    private void showEffects(List<RecommendationEffect> effects) {
        effectsList.removeAllViews();
        effectsTitle.setVisibility(effects.isEmpty() ? View.GONE : View.VISIBLE);
        PackageManager packageManager = getPackageManager();
        for (RecommendationEffect effect : effects) {
            View row = getLayoutInflater().inflate(R.layout.item_recommendation, effectsList, false);
            CharSequence name = effect.packageName;
            try {
                name = packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(effect.packageName, 0));
                ((ImageView) row.findViewById(R.id.recommendation_icon)).setImageDrawable(
                        packageManager.getApplicationIcon(effect.packageName));
            } catch (PackageManager.NameNotFoundException e) {
                RecommendationEffect.forget(this, effect.packageName);
                continue; // uninstalled: nothing left to keep or undo
            }
            ((TextView) row.findViewById(R.id.recommendation_app)).setText(name);
            ((TextView) row.findViewById(R.id.recommendation_reason)).setText(effectText(effect));
            TextView keep = row.findViewById(R.id.recommendation_dismiss);
            TextView undo = row.findViewById(R.id.recommendation_apply);
            keep.setText(R.string.recommendation_effect_keep);
            undo.setText(R.string.recommendation_effect_undo);
            keep.setContentDescription(getString(R.string.recommendation_effect_keep) + ": " + name);
            undo.setContentDescription(getString(R.string.recommendation_effect_undo) + ": " + name);
            undo.setVisibility(effect.verdict == RecommendationEffect.VERDICT_NO_CLEAR_EFFECT
                    ? View.VISIBLE : View.GONE);
            keep.setOnClickListener(v -> {
                RecommendationEffect.forget(this, effect.packageName);
                effectsList.removeView(row);
                effectsTitle.setVisibility(effectsList.getChildCount() == 0 ? View.GONE : View.VISIBLE);
            });
            undo.setOnClickListener(v -> {
                keep.setEnabled(false);
                undo.setEnabled(false);
                executor.execute(() -> {
                    // List statuses and strategies share their numbers.
                    AppPolicyQuickChoice.apply(getApplicationContext(),
                            effect.packageName, effect.previousStatus);
                    RecommendationEffect.forget(getApplicationContext(), effect.packageName);
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, R.string.policy_editor_saved, Toast.LENGTH_SHORT).show();
                        effectsList.removeView(row);
                        effectsTitle.setVisibility(
                                effectsList.getChildCount() == 0 ? View.GONE : View.VISIBLE);
                    });
                });
            });
            effectsList.addView(row);
        }
    }

    private String effectText(RecommendationEffect effect) {
        int before = (int) Math.round(effect.shareBefore * 100);
        int after = (int) Math.round(effect.shareAfter * 100);
        switch (effect.verdict) {
            case RecommendationEffect.VERDICT_HELPED:
                return getString(R.string.recommendation_effect_helped, before, after);
            case RecommendationEffect.VERDICT_NO_CLEAR_EFFECT:
                return getString(R.string.recommendation_effect_none, before, after);
            default:
                return getString(R.string.recommendation_effect_unknown);
        }
    }

    /** Removes one row and keeps the others, so the focus does not jump away on a TV remote. */
    private void removeRow(View row) {
        int index = list.indexOfChild(row);
        list.removeView(row);
        empty.setVisibility(list.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        View next = list.getChildAt(Math.min(index, list.getChildCount() - 1));
        if (next != null) next.findViewById(R.id.recommendation_apply).requestFocus();
    }

    private String reasonText(SuggestionEngine.Suggestion suggestion) {
        int percent = (int) Math.round(suggestion.facts.batteryShare * 100);
        switch (suggestion.reason) {
            case SuggestionEngine.REASON_RESTARTS:
                return getString(R.string.recommendations_reason_restarts,
                        suggestion.facts.kills, suggestion.facts.relaunches);
            case SuggestionEngine.REASON_UNUSED:
                return getString(R.string.recommendations_reason_unused,
                        suggestion.facts.daysSinceUsed, percent);
            default:
                return getString(R.string.recommendations_reason_heavy, percent);
        }
    }

    private void apply(SuggestionEngine.Suggestion suggestion, View row) {
        executor.execute(() -> {
            RecommendationEffect.recordApplied(getApplicationContext(),
                    suggestion.packageName, suggestion.facts.status);
            AppPolicyQuickChoice.applyPreset(getApplicationContext(),
                    suggestion.packageName, SuggestionLoader.APPLIED_PRESET);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, R.string.policy_editor_saved, Toast.LENGTH_SHORT).show();
                removeRow(row);
            });
        });
    }
}
