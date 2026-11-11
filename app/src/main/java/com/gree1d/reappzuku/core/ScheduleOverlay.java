package com.gree1d.reappzuku.core;

import com.gree1d.reappzuku.db.AppPolicy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which apps an active Automation Schedule adds to Auto-Kill. A schedule keeps
 * its own target lists and lays them over the per-app policies while it is
 * active, instead of overwriting the stored lists and putting them back later.
 */
public final class ScheduleOverlay {
    /** {@link AppConstants#KILL_MODE_WHITELIST} or {@link AppConstants#KILL_MODE_BLACKLIST}. */
    public final int killMode;
    public final Set<String> whitelist;
    public final Set<String> blacklist;

    public ScheduleOverlay(int killMode, Set<String> whitelist, Set<String> blacklist) {
        this.killMode = killMode;
        this.whitelist = whitelist == null ? Collections.emptySet() : new HashSet<>(whitelist);
        this.blacklist = blacklist == null ? Collections.emptySet() : new HashSet<>(blacklist);
    }

    /**
     * @param explicit the app's explicit policy, or null
     * @return true when the schedule wants the app stopped; an app the user
     *         protected stays protected
     */
    public boolean targets(String packageName, AppPolicy explicit) {
        if (explicit != null && explicit.strategy == AppPolicy.STRATEGY_PROTECTED) return false;
        return killMode == AppConstants.KILL_MODE_WHITELIST
                ? !whitelist.contains(packageName)
                : blacklist.contains(packageName);
    }
}
