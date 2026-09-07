package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ManualOpsMaskPolicyTest {
    @Test
    public void knownBitsMaskCoversExactlyDeclaredOps() {
        assertEquals(0, ManualOpsMaskPolicy.knownBitsMask(0));
        assertEquals(0x7ff, ManualOpsMaskPolicy.knownBitsMask(11));
        assertEquals(Integer.MAX_VALUE, ManualOpsMaskPolicy.knownBitsMask(31));
    }

    @Test
    public void sanitizeDropsUnknownAndSignBits() {
        assertEquals(0x7ff, ManualOpsMaskPolicy.sanitize(-1, 11));
        assertEquals(0x001, ManualOpsMaskPolicy.sanitize(0x1801, 11));
    }

    @Test
    public void requireKnownBitsAcceptsKnownMaskAndRejectsUnknownBits() {
        assertEquals(0x401, ManualOpsMaskPolicy.requireKnownBits(0x401, 11));
        assertRejected(1 << 11, 11);
        assertRejected(-1, 11);
    }

    @Test
    public void impossibleCatalogSizeIsRejected() {
        assertCountRejected(-1);
        assertCountRejected(32);
    }

    private static void assertRejected(int mask, int opCount) {
        try {
            ManualOpsMaskPolicy.requireKnownBits(mask, opCount);
            fail("Expected unknown manual AppOps bits to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertCountRejected(int opCount) {
        try {
            ManualOpsMaskPolicy.knownBitsMask(opCount);
            fail("Expected invalid operation count to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
