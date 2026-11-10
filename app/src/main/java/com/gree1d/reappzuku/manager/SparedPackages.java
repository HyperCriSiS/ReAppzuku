package com.gree1d.reappzuku.manager;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers which kill candidates were found protected. A protected app stays
 * a candidate on every pass, and without this memory each pass would collect
 * the system dumps again only to spare the same apps.
 */
final class SparedPackages {
    static final long TTL_MS = 5 * 60_000L;

    private final Map<String, Long> sparedAt = new HashMap<>();

    synchronized void spare(String pkg, long nowMs) {
        sparedAt.put(pkg, nowMs);
    }

    synchronized boolean isSpared(String pkg, long nowMs) {
        Long at = sparedAt.get(pkg);
        return at != null && nowMs - at < TTL_MS;
    }
}
