package com.gree1d.reappzuku.manager;

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
        if (type != BackgroundAppManager.RestrictionType.MANUAL) return type.name();
        return type.name() + ":" + manualOpsMask + ":" + manualBucket + ":" + manualWhitelistRemoval;
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
}
