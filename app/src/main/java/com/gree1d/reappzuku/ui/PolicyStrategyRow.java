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
     * @param status a strategy, or a list status without a strategy of its
     *               own (needs setup), which is shown as unmanaged
     */
    static void bind(View row, TextView value, int status, IntConsumer onChosen) {
        String[] names = row.getResources().getStringArray(R.array.policy_editor_strategies);
        int[] current = {status >= 0 && status < names.length ? status : 0};
        value.setText(names[current[0]]);
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
