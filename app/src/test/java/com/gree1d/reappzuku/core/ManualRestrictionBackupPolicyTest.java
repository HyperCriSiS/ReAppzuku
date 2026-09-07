package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ManualRestrictionBackupPolicyTest {
    @Test
    public void acceptsPortableManualBuckets() {
        assertEquals(0, ManualRestrictionBackupPolicy.requireBucket(0));
        assertEquals(40, ManualRestrictionBackupPolicy.requireBucket(40));
        assertEquals(45, ManualRestrictionBackupPolicy.requireBucket(45));
    }

    @Test
    public void rejectsUnsupportedManualBuckets() {
        assertRejected(-1);
        assertRejected(10);
        assertRejected(44);
        assertRejected(46);
    }

    private static void assertRejected(int bucket) {
        try {
            ManualRestrictionBackupPolicy.requireBucket(bucket);
            fail("Expected unsupported manual standby bucket to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
