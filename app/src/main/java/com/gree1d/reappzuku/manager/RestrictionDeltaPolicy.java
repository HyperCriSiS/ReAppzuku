package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.db.AppPolicy;

import java.util.HashSet;
import java.util.Set;

/**
 * Decides which apps a restriction pass has to touch. A pass used to lift and
 * re-apply every restricted app and force-stop each one, even when a single
 * app changed. Each applied restriction is now remembered as a signature and
 * an app is only touched when its signature changes.
 */
final class RestrictionDeltaPolicy {

    private RestrictionDeltaPolicy() {
    }

    /** Everything that defines what gets applied to one app. */
    static String signature(BackgroundAppManager.RestrictionType type,
                            int manualOpsMask, int manualBucket, boolean manualWhitelistRemoval) {
        // Bump the version when the op lists of a level change, so stored apps are re-applied.
        String base = "v1:" + type.name();
        if (type != BackgroundAppManager.RestrictionType.MANUAL) return base;
        return base + ":" + manualOpsMask + ":" + manualBucket + ":" + manualWhitelistRemoval;
    }

    /**
     * An apply is only remembered when every step worked. Anything less is
     * retried on the next pass instead of being skipped as unchanged.
     */
    static boolean applySucceeded(int[] opsCount, boolean bucketOk, boolean whitelistOk) {
        return opsCount[0] > 0 && opsCount[1] == 0 && bucketOk && whitelistOk;
    }

    /**
     * A failed lift keeps its signature so the next pass tries again. An
     * uninstalled app has nothing left to lift.
     */
    static boolean liftCompleted(int[] opsCount, boolean installed) {
        return opsCount[0] > 0 || !installed;
    }

    /** The restriction level an app policy stores for the given applied state. */
    static int policyLevel(boolean restricted, BackgroundAppManager.RestrictionType type) {
        if (!restricted) return AppPolicy.RESTRICTION_NONE;
        switch (type) {
            case MEDIUM: return AppPolicy.RESTRICTION_MEDIUM;
            case HARD: return AppPolicy.RESTRICTION_HARD;
            case MANUAL: return AppPolicy.RESTRICTION_MANUAL;
            default: return AppPolicy.RESTRICTION_SOFT;
        }
    }

    /** @return null for {@link AppPolicy#RESTRICTION_NONE} and unknown levels */
    static BackgroundAppManager.RestrictionType typeForLevel(int policyLevel) {
        switch (policyLevel) {
            case AppPolicy.RESTRICTION_SOFT: return BackgroundAppManager.RestrictionType.SOFT;
            case AppPolicy.RESTRICTION_MEDIUM: return BackgroundAppManager.RestrictionType.MEDIUM;
            case AppPolicy.RESTRICTION_HARD: return BackgroundAppManager.RestrictionType.HARD;
            case AppPolicy.RESTRICTION_MANUAL: return BackgroundAppManager.RestrictionType.MANUAL;
            default: return null;
        }
    }

    static boolean needsApply(String appliedSignature, String desiredSignature) {
        return !desiredSignature.equals(appliedSignature);
    }

    /**
     * Only apps this app restricted itself are lifted: the previous target
     * set, apps with a remembered signature, and apps the user removes
     * explicitly. Restrictions set elsewhere, for example in the system
     * battery settings, are left alone.
     */
    static Set<String> packagesToLift(Set<String> previouslyDesired, Set<String> applied,
                                      Set<String> explicitlyRemoved, Set<String> desired) {
        Set<String> lift = new HashSet<>(previouslyDesired);
        lift.addAll(applied);
        lift.addAll(explicitlyRemoved);
        lift.removeAll(desired);
        return lift;
    }

    /**
     * First pass after an update: nothing is tracked yet, so the live device
     * state is lifted once, as every pass did before. Tracking only starts
     * once that live state could actually be read.
     */
    static Set<String> packagesToLiftUntracked(Set<String> liveRestricted, Set<String> previouslyDesired,
                                               Set<String> explicitlyRemoved, Set<String> desired) {
        return packagesToLift(previouslyDesired, liveRestricted, explicitlyRemoved, desired);
    }
}
