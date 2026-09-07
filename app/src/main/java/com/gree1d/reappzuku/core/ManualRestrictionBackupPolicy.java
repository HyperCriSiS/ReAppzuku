package com.gree1d.reappzuku.core;

/** Validation rules for portable per-package manual restriction settings. */
public final class ManualRestrictionBackupPolicy {
    public static final int BUCKET_NONE = 0;
    public static final int BUCKET_RARE = 40;
    public static final int BUCKET_RESTRICTED = 45;

    private ManualRestrictionBackupPolicy() {}

    public static int requireBucket(int bucket) {
        if (bucket != BUCKET_NONE && bucket != BUCKET_RARE && bucket != BUCKET_RESTRICTED) {
            throw new IllegalArgumentException("Unsupported manual standby bucket: " + bucket);
        }
        return bucket;
    }
}
