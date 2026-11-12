package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parsers for Smart Lifecycle process/foreground shell text. */
final class SmartLifecycleTextParser {
    private static final Pattern FOREGROUND_PACKAGE = Pattern.compile(
            "(?:mResumedActivity|topResumedActivity|mCurrentFocus).*?\\s([A-Za-z0-9_.$]+)/(?:[A-Za-z0-9_.$]+)");

    private SmartLifecycleTextParser() {
    }

    static String parseForegroundPackage(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = FOREGROUND_PACKAGE.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static final Pattern HISTORY_ENTRY = Pattern.compile("\\* Hist +#\\d+: ");
    private static final Pattern RECORD_PACKAGE = Pattern.compile(
            "^ActivityRecord\\{\\S+ u\\d+ ([A-Za-z0-9_.$]+)/");

    /**
     * Packages with an activity on screen or about to be: resumed, paused
     * (picture in picture, split screen), started or requested visible, plus
     * the app on top, which is the one the user left when the screen went off.
     * An app that only sits further down in Recents is not included.
     *
     * @return null when no activity history entry could be read, so the caller
     *         cannot tell which apps are on screen
     */
    static Set<String> parseVisiblePackages(String activitiesDump) {
        if (activitiesDump == null) return null;
        String[] entries = HISTORY_ENTRY.split(activitiesDump, -1);
        Set<String> visible = new HashSet<>();
        boolean anyRecord = false;
        for (int i = 1; i < entries.length; i++) {
            Matcher record = RECORD_PACKAGE.matcher(entries[i]);
            if (!record.find()) continue;
            String entry = entries[i];
            // The first entry belongs to the top task.
            if (!anyRecord || entry.contains("nowVisible=true") || entry.contains("state=RESUMED")
                    || entry.contains("state=PAUSED") || entry.contains("state=STARTED")
                    || entry.contains("mVisibleRequested=true")) {
                visible.add(record.group(1));
            }
            anyRecord = true;
        }
        if (!anyRecord) return null;
        Matcher foreground = FOREGROUND_PACKAGE.matcher(activitiesDump);
        while (foreground.find()) visible.add(foreground.group(1));
        return visible;
    }

    static Set<String> parseRunningPackages(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isEmpty()) return result;
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || "NAME".equalsIgnoreCase(name)) continue;
                int colon = name.indexOf(':');
                if (colon > 0) name = name.substring(0, colon);
                if (isPackageLike(name)) result.add(name);
            }
        } catch (IOException impossibleForStringReader) {
            throw new IllegalStateException(impossibleForStringReader);
        }
        return result;
    }

    private static boolean isPackageLike(String value) {
        if (value.indexOf('.') <= 0) return false;
        try {
            PackageNameValidator.requireValid(value);
            return true;
        } catch (IllegalArgumentException invalidPackage) {
            return false;
        }
    }
}
