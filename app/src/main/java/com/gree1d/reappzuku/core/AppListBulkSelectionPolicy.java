package com.gree1d.reappzuku.core;

import com.gree1d.reappzuku.utils.AppModel;

import java.util.List;

/**
 * Bulk selection operates only on the currently visible, already-filtered rows.
 * The full inventory is deliberately not accepted: filtered-out packages must
 * never be added to a potentially destructive bulk action by "Select all".
 */
public final class AppListBulkSelectionPolicy {
    private AppListBulkSelectionPolicy() {}

    public static void selectVisible(List<AppModel> visibleApps) {
        for (AppModel app : visibleApps) {
            if (!app.isProtected() && !app.isWhitelisted()) {
                app.setSelected(true);
            }
        }
    }
}
