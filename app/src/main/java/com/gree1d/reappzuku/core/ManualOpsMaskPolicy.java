package com.gree1d.reappzuku.core;

/** Bounds persisted/manual AppOps bitmasks to the currently known operation catalog. */
public final class ManualOpsMaskPolicy {
    private ManualOpsMaskPolicy() {}

    public static int knownBitsMask(int opCount) {
        if (opCount < 0 || opCount > 31) {
            throw new IllegalArgumentException("Manual AppOps count out of bounds: " + opCount);
        }
        if (opCount == 31) return Integer.MAX_VALUE;
        return (1 << opCount) - 1;
    }

    public static int sanitize(int mask, int opCount) {
        return mask & knownBitsMask(opCount);
    }

    public static int requireKnownBits(int mask, int opCount) {
        int knownMask = knownBitsMask(opCount);
        int unknownBits = mask & ~knownMask;
        if (unknownBits != 0) {
            throw new IllegalArgumentException(
                    "Unknown manual AppOps bits: 0x" + Integer.toHexString(unknownBits));
        }
        return mask;
    }
}
