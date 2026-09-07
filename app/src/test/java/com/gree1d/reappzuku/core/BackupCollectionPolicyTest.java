package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class BackupCollectionPolicyTest {
    @Test
    public void acceptsEmptyAndMaximumSizedCollections() {
        BackupCollectionPolicy.requirePackageEntryCount("packages", 0);
        BackupCollectionPolicy.requirePackageEntryCount(
                "packages", BackupCollectionPolicy.MAX_PACKAGE_ENTRIES);
    }

    @Test
    public void rejectsOversizedCollection() {
        assertThrows(IllegalArgumentException.class, () ->
                BackupCollectionPolicy.requirePackageEntryCount(
                        "packages", BackupCollectionPolicy.MAX_PACKAGE_ENTRIES + 1));
    }

    @Test
    public void rejectsImpossibleNegativeCount() {
        assertThrows(IllegalArgumentException.class, () ->
                BackupCollectionPolicy.requirePackageEntryCount("packages", -1));
    }
}
