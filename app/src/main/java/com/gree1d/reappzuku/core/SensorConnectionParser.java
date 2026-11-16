package com.gree1d.reappzuku.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads which apps hold a sensor right now from "dumpsys sensorservice".
 * A connection is a listener line carrying the uid, followed by one line per
 * sensor:
 *
 * <pre>
 * Connection Number: 4
 *      Operating Mode: NORMAL
 *       com.example.Listener | WakeLockRefCount 0 | uid 10428 | cache size 0 | ...
 *       icm4x6xx Accelerometer Non-wakeup 0x0000000b | first flush pending: false | ...
 * </pre>
 */
public final class SensorConnectionParser {
    private static final Pattern UID = Pattern.compile("\\| uid (\\d+) \\|");
    private static final Pattern SENSOR = Pattern.compile("^(.+?) 0x[0-9a-fA-F]{8} \\|");
    private static final Pattern ACCESS = Pattern.compile("\\| has sensor access: (true|false)");

    private SensorConnectionParser() {
    }

    /** @return the sensor names each uid holds, in the order of the dump, without duplicates */
    public static Map<Integer, List<String>> parse(String dump) {
        Map<Integer, List<String>> byUid = new LinkedHashMap<>();
        if (dump == null) return byUid;
        boolean inConnections = false;
        int uid = -1;
        for (String line : dump.split("\n")) {
            String text = line.trim();
            if (text.startsWith("Connection Number:")) {
                inConnections = true;
                uid = -1;
                continue;
            }
            if (text.startsWith("Previous Registrations")) break;
            if (!inConnections) continue;
            Matcher owner = UID.matcher(text);
            if (owner.find()) {
                uid = Integer.parseInt(owner.group(1));
                continue;
            }
            Matcher sensor = SENSOR.matcher(text);
            if (uid >= 0 && sensor.find()) {
                List<String> sensors = byUid.computeIfAbsent(uid, k -> new ArrayList<>());
                String name = sensor.group(1).trim();
                if (!sensors.contains(name)) sensors.add(name);
            }
        }
        return byUid;
    }

    /**
     * Uids that hold a sensor only through connections without sensor access.
     * Android stops delivering events to an idle background app but leaves its
     * sensors switched on, so the hardware runs for nothing. A uid with any
     * connection that still has access is not listed.
     */
    public static Set<Integer> uidsHoldingWithoutAccess(String dump) {
        Set<Integer> withoutAccess = new HashSet<>();
        Set<Integer> withAccess = new HashSet<>();
        if (dump == null) return withoutAccess;
        boolean inConnections = false;
        int uid = -1;
        boolean access = true;
        for (String line : dump.split("\n")) {
            String text = line.trim();
            if (text.startsWith("Connection Number:")) {
                inConnections = true;
                uid = -1;
                continue;
            }
            if (text.startsWith("Previous Registrations")) break;
            if (!inConnections) continue;
            Matcher owner = UID.matcher(text);
            if (owner.find()) {
                uid = Integer.parseInt(owner.group(1));
                Matcher state = ACCESS.matcher(text);
                // Without the field the access is unknown; count it as access.
                access = !state.find() || "true".equals(state.group(1));
                continue;
            }
            if (uid >= 0 && SENSOR.matcher(text).find()) {
                (access ? withAccess : withoutAccess).add(uid);
            }
        }
        withoutAccess.removeAll(withAccess);
        return withoutAccess;
    }
}
