package com.gree1d.reappzuku.core;

import java.util.regex.Pattern;

/**
 * Validates package identifiers before they can cross a privileged shell boundary.
 *
 * The accepted alphabet is deliberately narrower than arbitrary shell text. It covers normal
 * Android application IDs while rejecting whitespace, separators, substitutions and control
 * characters that could change shell command meaning.
 */
public final class PackageNameValidator {
    private static final int MAX_PACKAGE_NAME_LENGTH = 255;
    private static final Pattern SAFE_PACKAGE_NAME =
            Pattern.compile("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$");

    private PackageNameValidator() {}

    public static boolean isValid(String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        if (packageName.length() > MAX_PACKAGE_NAME_LENGTH) return false;
        if (!packageName.equals(packageName.trim())) return false;
        return SAFE_PACKAGE_NAME.matcher(packageName).matches();
    }

    public static String requireValid(String packageName) {
        if (!isValid(packageName)) {
            throw new IllegalArgumentException("Invalid Android package name");
        }
        return packageName;
    }
}
