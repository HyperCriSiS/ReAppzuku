package com.gree1d.reappzuku.ui;

import android.content.Context;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.core.CapabilityMatrix;

/** The access mode and its limits as text, for every screen that shows them. */
final class CapabilityText {

    private CapabilityText() {
    }

    static String mode(Context context, CapabilityMatrix matrix) {
        switch (matrix.mode()) {
            case ROOT: return context.getString(R.string.settings_shell_root_ok);
            case SHIZUKU: return context.getString(R.string.settings_shell_shizuku_ok);
            default: return context.getString(R.string.settings_shell_no_access);
        }
    }

    static String limits(Context context, CapabilityMatrix matrix) {
        StringBuilder limits = new StringBuilder();
        for (CapabilityMatrix.Feature feature : new CapabilityMatrix.Feature[] {
                CapabilityMatrix.Feature.KILL_APPS,
                CapabilityMatrix.Feature.BACKGROUND_RESTRICTION,
                CapabilityMatrix.Feature.MEMORY_PSS,
                CapabilityMatrix.Feature.EXACT_ALARMS}) {
            int reason;
            switch (matrix.of(feature)) {
                case UNAVAILABLE_NEEDS_PRIVILEGE:
                    // Every intervention shares this reason; say it once.
                    if (feature != CapabilityMatrix.Feature.KILL_APPS) continue;
                    reason = R.string.capability_needs_privilege;
                    break;
                case UNAVAILABLE_NEEDS_ANDROID_11: reason = R.string.capability_needs_android_11; break;
                case DEGRADED_RSS_ONLY: reason = R.string.capability_rss_only; break;
                case DEGRADED_INEXACT_ALARMS: reason = R.string.capability_inexact_alarms; break;
                default: continue;
            }
            if (limits.length() > 0) limits.append('\n');
            limits.append(context.getString(reason));
        }
        return limits.length() > 0 ? limits.toString()
                : context.getString(R.string.capability_all_available);
    }
}
