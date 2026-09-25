package com.gree1d.reappzuku.manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Parses a process-name snapshot and returns killed packages that are running again.
 * Multiple processes belonging to the same package count as one relaunch observation.
 */
final class RelaunchDetector {

    private RelaunchDetector() {
    }

    static Set<String> findRelaunchedPackages(Collection<String> recentlyKilled, String psOutput) {
        if (recentlyKilled == null || recentlyKilled.isEmpty()
                || psOutput == null || psOutput.trim().isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> targets = new HashSet<>();
        for (String packageName : recentlyKilled) {
            if (packageName != null && !packageName.trim().isEmpty()) {
                targets.add(packageName.trim());
            }
        }
        if (targets.isEmpty()) return Collections.emptySet();

        Set<String> relaunched = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(psOutput))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String processName = line.trim();
                if (processName.isEmpty()) continue;

                int colon = processName.indexOf(':');
                String packageName = colon > 0 ? processName.substring(0, colon) : processName;
                if (targets.contains(packageName)) {
                    relaunched.add(packageName);
                }
            }
        } catch (IOException ignored) {
            return Collections.emptySet();
        }
        return relaunched;
    }
}
