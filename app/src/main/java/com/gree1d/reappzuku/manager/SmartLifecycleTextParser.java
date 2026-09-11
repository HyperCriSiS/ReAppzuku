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
