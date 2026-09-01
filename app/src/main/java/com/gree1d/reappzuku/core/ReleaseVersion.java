package com.gree1d.reappzuku.core;

/** Strict numeric release comparison. Non-version test tags are ignored. */
public final class ReleaseVersion {
    private ReleaseVersion() {}

    public static boolean isNewer(String remote, String local) {
        int[] r = parse(remote);
        int[] l = parse(local);
        if (r == null || l == null) return false;
        int length = Math.max(r.length, l.length);
        for (int i = 0; i < length; i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return false;
    }

    static int[] parse(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("v") || v.startsWith("V")) v = v.substring(1);
        if (v.isEmpty() || !v.matches("\\d+(\\.\\d+)*")) return null;
        String[] parts = v.split("\\.");
        int[] result = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) result[i] = Integer.parseInt(parts[i]);
            return result;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
