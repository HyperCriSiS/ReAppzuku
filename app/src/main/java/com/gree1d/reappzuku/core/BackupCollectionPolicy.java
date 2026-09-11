package com.gree1d.reappzuku.core;

/** Resource bounds for package-oriented backup collections. */
public final class BackupCollectionPolicy {
    /** Far above realistic installed-app counts while preventing adversarial restore amplification. */
    public static final int MAX_PACKAGE_ENTRIES = 10_000;

    private BackupCollectionPolicy() {}

    public static void requirePackageEntryCount(String key, int count) {
        if (count < 0 || count > MAX_PACKAGE_ENTRIES) {
            throw new IllegalArgumentException(
                    "Backup package collection out of bounds: " + key + " count=" + count);
        }
    }
}
