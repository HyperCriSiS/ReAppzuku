package com.gree1d.reappzuku.ui;

import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.gree1d.reappzuku.R;

import java.util.function.IntConsumer;

/** The "Strategy" row of the app option sheets: shows the current strategy and lets the user pick one. */
final class PolicyStrategyRow {

    private PolicyStrategyRow() {
    }

    /**
     * @param status a strategy; a list status without a strategy of its own
     *               (needs setup), for which every choice is a change; or a
     *               negative value when the app cannot be managed, which
     *               hides the row
     */
    static void bind(View row, TextView value, int status, IntConsumer onChosen) {
        if (status < 0) {
            row.setVisibility(View.GONE);
            return;
        }
        String[] names = row.getResources().getStringArray(R.array.policy_editor_strategies);
        int[] current = {status < names.length ? status : -1};
        value.setText(current[0] >= 0 ? names[current[0]]
                : row.getResources().getString(R.string.policy_badge_needs_setup));
        row.setOnClickListener(v -> new MaterialAlertDialogBuilder(row.getContext())
                .setTitle(R.string.policy_editor_strategy)
                .setSingleChoiceItems(names, current[0], (dialog, which) -> {
                    dialog.dismiss();
                    if (which == current[0]) return;
                    current[0] = which;
                    value.setText(names[which]);
                    onChosen.accept(which);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show());
    }
}
