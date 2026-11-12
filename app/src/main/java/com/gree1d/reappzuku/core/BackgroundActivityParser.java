package com.gree1d.reappzuku.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads how long each app kept the device busy in the background from
 * "dumpsys batterystats --charged --checkin". The system has counted this
 * since the last full charge, so it is known on the first day.
 *
 * <pre>
 * 9,0,l,bt,1,419593748,...            time on battery in ms
 * 9,10453,l,awl,1282637,1282637       partial wakelock time of the uid in ms
 * 9,10453,l,jb,"name",1298125,4,...   one job: time in ms, count, ...
 * </pre>
 */
public final class BackgroundActivityParser {
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    /** A shorter window says too little: one job right after unplugging would look like hours per day. */
    static final long MIN_WINDOW_MS = 6L * 60 * 60 * 1000;
    /** Less than this in the whole window is not worth naming, whatever it is per day. */
    static final long MIN_BUSY_MS = 5L * 60 * 1000;

    private BackgroundActivityParser() {
    }

    /**
     * @return minutes per day each uid held a wakelock or ran jobs, whichever
     *         is longer (a running job holds a wakelock, so the two overlap).
     *         Empty when the statistics cover less than {@link #MIN_WINDOW_MS};
     *         a uid with less than {@link #MIN_BUSY_MS} in total is left out.
     */
    public static Map<Integer, Double> minutesPerDay(String checkin) {
        Map<Integer, Double> result = new HashMap<>();
        if (checkin == null) return result;
        long windowMs = 0;
        Map<Integer, Long> wakelockMs = new HashMap<>();
        Map<Integer, Long> jobMs = new HashMap<>();
        for (String line : checkin.split("\n")) {
            String[] parts = line.trim().split(",");
            if (parts.length < 5 || !"l".equals(parts[2])) continue;
            try {
                int uid = Integer.parseInt(parts[1]);
                switch (parts[3]) {
                    case "bt":
                        if (parts.length > 5) windowMs = Long.parseLong(parts[5]);
                        break;
                    case "awl":
                        wakelockMs.put(uid, Long.parseLong(parts[4]));
                        break;
                    case "jb":
                        // The job name may contain commas; the four numbers are at the end.
                        if (parts.length >= 9) {
                            jobMs.merge(uid, Long.parseLong(parts[parts.length - 4]), Long::sum);
                        }
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException e) {
                // A line in another layout is skipped.
            }
        }
        if (windowMs < MIN_WINDOW_MS) return result;
        double days = (double) windowMs / DAY_MS;
        for (Map.Entry<Integer, Long> entry : wakelockMs.entrySet()) {
            jobMs.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        for (Map.Entry<Integer, Long> entry : jobMs.entrySet()) {
            if (entry.getValue() < MIN_BUSY_MS) continue;
            result.put(entry.getKey(), entry.getValue() / 60_000.0 / days);
        }
        return result;
    }
}
