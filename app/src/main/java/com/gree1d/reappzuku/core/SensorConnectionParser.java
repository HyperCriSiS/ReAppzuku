package com.gree1d.reappzuku.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
}
