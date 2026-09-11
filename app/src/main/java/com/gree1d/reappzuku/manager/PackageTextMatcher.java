package com.gree1d.reappzuku.manager;

import com.gree1d.reappzuku.core.PackageNameValidator;

/**
 * Exact package-token matching for Android text dumps consumed by Smart Lifecycle.
 *
 * Android package names are frequently followed by component ('/'), remote-process (':')
 * or formatting delimiters. A plain String.contains() makes prefix-neighbor packages such
 * as com.example.app2 look like com.example.app and can incorrectly suppress lifecycle work.
 */
final class PackageTextMatcher {
    private PackageTextMatcher() {
    }

    static boolean containsExactPackage(String text, String packageName) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        PackageNameValidator.requireValid(packageName);

        int index = text.indexOf(packageName);
        while (index >= 0) {
            if (hasPackageBoundary(text, index, packageName.length())) {
                return true;
            }
            index = text.indexOf(packageName, index + packageName.length());
        }
        return false;
    }

    static boolean containsMarkerNearPackage(
            String text, String packageName, String marker, int radius) {
        if (text == null || text.isEmpty() || marker == null || marker.isEmpty()) {
            return false;
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
        PackageNameValidator.requireValid(packageName);

        int index = text.indexOf(packageName);
        while (index >= 0) {
            if (hasPackageBoundary(text, index, packageName.length())) {
                int start = Math.max(0, index - radius);
                int end = Math.min(text.length(), index + packageName.length() + radius);
                if (text.substring(start, end).contains(marker)) {
                    return true;
                }
            }
            index = text.indexOf(packageName, index + packageName.length());
        }
        return false;
    }

    private static boolean hasPackageBoundary(String text, int start, int length) {
        int before = start - 1;
        int after = start + length;
        return (before < 0 || !isPackageNameChar(text.charAt(before)))
                && (after >= text.length() || !isPackageNameChar(text.charAt(after)));
    }

    private static boolean isPackageNameChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
                || c == '.';
    }
}
