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
import com.gree1d.reappzuku.core.SuggestionEngine;
import com.gree1d.reappzuku.core.SuggestionLoader;

import java.util.List;
import java.util.concurrent.ExecutorService;

/** Lists what the app recommends from its own measurements. Nothing changes until the user applies one. */
public class RecommendationsActivity extends BaseActivity {
    private ExecutorService executor;
    private LinearLayout list;
    private TextView empty;

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
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                show(suggestions);
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
            row.findViewById(R.id.recommendation_apply).setOnClickListener(v -> apply(suggestion));
            row.findViewById(R.id.recommendation_dismiss).setOnClickListener(v -> {
                SuggestionLoader.dismiss(this, suggestion);
                reload();
            });
            list.addView(row);
        }
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

    private void apply(SuggestionEngine.Suggestion suggestion) {
        executor.execute(() -> {
            AppPolicyQuickChoice.apply(
                    getApplicationContext(), suggestion.packageName, suggestion.strategy);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this, R.string.policy_editor_saved, Toast.LENGTH_SHORT).show();
                reload();
            });
        });
    }
}
