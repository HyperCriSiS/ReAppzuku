package com.gree1d.reappzuku.service;

import java.util.Set;

final class AppLaunchTriggerPolicy {
    private AppLaunchTriggerPolicy() {
    }

    static boolean isEligible(boolean triggerEnabled,
                              boolean autoKillEnabled,
                              Set<String> targetPackages,
                              String packageName) {
        return triggerEnabled
                && autoKillEnabled
                && packageName != null
                && targetPackages != null
                && targetPackages.contains(packageName);
    }

    static boolean isDuplicateWithinInterval(String packageName,
                                             String lastTriggeredPackage,
                                             long lastTriggerTime,
                                             long now,
                                             long minTriggerIntervalMs) {
        if (packageName == null
                || lastTriggeredPackage == null
                || !packageName.equals(lastTriggeredPackage)
                || minTriggerIntervalMs <= 0L) {
            return false;
        }
        return (now - lastTriggerTime) < minTriggerIntervalMs;
    }
}
