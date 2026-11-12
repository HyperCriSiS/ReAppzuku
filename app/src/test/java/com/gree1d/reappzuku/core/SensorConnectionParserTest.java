package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SensorConnectionParserTest {

    // Taken from an Android 16 device.
    private static final String DUMP = String.join("\n",
            "Active sensors:",
            "icm4x6xx Accelerometer Non-wakeup (handle=0x0000000b, connections=5)",
            "icm4x6xx Gyroscope Non-wakeup (handle=0x00000029, connections=3)",
            "Socket Buffer size = 984 events",
            "11 open event connections",
            "Connection Number: 0 ",
            "\tOperating Mode: NORMAL",
            "\t com.android.systemui.doze.DozeSensors$TriggerSensor | WakeLockRefCount 0 | uid 10135"
                    + " | cache size 0 | max cache size 0 | has sensor access: true",
            "\t Double Tap Sensor 0x01000002 | first flush pending: false | pending flush events 0 ",
            "Connection Number: 3 ",
            "\tOperating Mode: NORMAL",
            "\t com.facebook.ads.redexgen.X.lp | WakeLockRefCount 0 | uid 10566 | cache size 0"
                    + " | max cache size 0 | has sensor access: false",
            "\t icm4x6xx Accelerometer Non-wakeup 0x0000000b | first flush pending: false"
                    + " | pending flush events 0 ",
            "Connection Number: 4 ",
            "\tOperating Mode: NORMAL",
            "\t com.perimeterx.mobile_sdk.detections.device.f | WakeLockRefCount 0 | uid 10428"
                    + " | cache size 0 | max cache size 10000 | has sensor access: false",
            "\t AF6133E Magnetometer Non-wakeup 0x00000015 | first flush pending: false"
                    + " | pending flush events 0 ",
            "\t icm4x6xx Accelerometer Non-wakeup 0x0000000b | first flush pending: false"
                    + " | pending flush events 0 ",
            "Connection Number: 5 ",
            "\tOperating Mode: NORMAL",
            "\t com.facebook.ads.redexgen.X.lp | WakeLockRefCount 0 | uid 10566 | cache size 0"
                    + " | max cache size 0 | has sensor access: false",
            "\t icm4x6xx Gyroscope Non-wakeup 0x00000029 | first flush pending: false"
                    + " | pending flush events 0 ",
            "\t icm4x6xx Accelerometer Non-wakeup 0x0000000b | first flush pending: false"
                    + " | pending flush events 0 ",
            "0 open direct connections",
            "Previous Registrations:",
            "04:15:43 + 0x00000029 pid=26638 uid=10556 samplingPeriod=  200000us batchingPeriod="
                    + "       0us result=OK (sensor, package): (icm4x6xx Gyroscope Non-wakeup, x)");

    @Test
    public void readsTheSensorsEachUidHoldsNow() {
        Map<Integer, List<String>> byUid = SensorConnectionParser.parse(DUMP);
        assertEquals(Arrays.asList("AF6133E Magnetometer Non-wakeup", "icm4x6xx Accelerometer Non-wakeup"),
                byUid.get(10428));
        // Two connections of one app are merged, each sensor once.
        assertEquals(Arrays.asList("icm4x6xx Accelerometer Non-wakeup", "icm4x6xx Gyroscope Non-wakeup"),
                byUid.get(10566));
        assertEquals(Arrays.asList("Double Tap Sensor"), byUid.get(10135));
        // Neither the list of active sensors nor past registrations count as holding one.
        assertEquals(3, byUid.size());
        assertFalse(byUid.containsKey(10556));
    }

    @Test
    public void readsTheOlderStatusLayout() {
        Map<Integer, List<String>> byUid = SensorConnectionParser.parse(String.join("\n",
                "Connection Number: 0 ",
                "\tOperating Mode: NORMAL",
                "\t com.example.Listener | WakeLockRefCount 0 | uid 10010 | cache size 0 | max cache size 0",
                "\t BMI160 accelerometer 0x00000001 | status: active | pending flush events 0 "));
        assertEquals(Arrays.asList("BMI160 accelerometer"), byUid.get(10010));
    }

    @Test
    public void anEmptyOrMissingDumpHoldsNothing() {
        assertTrue(SensorConnectionParser.parse(null).isEmpty());
        assertTrue(SensorConnectionParser.parse("").isEmpty());
    }

    @Test
    public void theScanAsksTheSystemOnceNotOncePerApp() throws Exception {
        String scan = read("app/src/main/java/com/gree1d/reappzuku/manager/ScanSystem.java");
        assertTrue(scan.contains("SensorConnectionParser.parse(output)"));
        // One battery statistics dump per running app took minutes on a device with 140 apps.
        assertFalse(scan.contains("\"dumpsys batterystats \" + pkg"));
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative));
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
