package com.gree1d.reappzuku.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure, bounded and deterministic New App Setup queue presentation decisions. */
public final class NewAppSetupReviewPolicy {
    public static final int MAX_VISIBLE_APPS = 8;

    private NewAppSetupReviewPolicy() {}

    public static List<String> sortedSnapshot(Collection<String> packages) {
        List<String> sorted = new ArrayList<>(packages);
        sorted.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return Collections.unmodifiableList(sorted);
    }

    public static List<String> preview(List<String> pending) {
        return pending.subList(0, Math.min(MAX_VISIBLE_APPS, pending.size()));
    }

    public static boolean confirmModeChange(int previous, int selected, int pendingCount) {
        return previous != selected && pendingCount > 0;
    }

    public static boolean confirmPresetChange(
            int mode, long previousId, long selectedId, int pendingCount) {
        return mode == NewAppSetupPolicy.MODE_APPLY_DEFAULT_PRESET
                && previousId != selectedId && pendingCount > 0;
    }
}
