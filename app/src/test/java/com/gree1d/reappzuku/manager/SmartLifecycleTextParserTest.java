package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;

public class SmartLifecycleTextParserTest {
    @Test
    public void foregroundParserCoversActivityAndWindowForms() {
        assertEquals("com.example.app", SmartLifecycleTextParser.parseForegroundPackage(
                "mResumedActivity: ActivityRecord{abc u0 com.example.app/.MainActivity t12}"));
        assertEquals("com.example.app", SmartLifecycleTextParser.parseForegroundPackage(
                "topResumedActivity=ActivityRecord{abc u0 com.example.app/com.example.app.Home}"));
        assertEquals("com.example.app", SmartLifecycleTextParser.parseForegroundPackage(
                "mCurrentFocus=Window{abc u0 com.example.app/.MainActivity}"));
    }

    @Test
    public void foregroundParserIgnoresUnrelatedPackageText() {
        assertNull(SmartLifecycleTextParser.parseForegroundPackage(
                "ProcessRecord{abc 123:com.example.app/u0a123}"));
        assertNull(SmartLifecycleTextParser.parseForegroundPackage("mResumedActivity: null"));
        assertNull(SmartLifecycleTextParser.parseForegroundPackage(null));
    }

    @Test
    public void runningParserCollapsesRemoteProcessesAndRejectsNoise() {
        Set<String> packages = SmartLifecycleTextParser.parseRunningPackages(
                "NAME\n"
                        + "com.example.app\n"
                        + "com.example.app:remote\n"
                        + "com.other.service:worker\r\n"
                        + "system_server\n"
                        + "1234\n"
                        + "com.bad;id\n");
        assertEquals(2, packages.size());
        assertTrue(packages.contains("com.example.app"));
        assertTrue(packages.contains("com.other.service"));
        assertFalse(packages.contains("com.bad;id"));
    }

    @Test
    public void runningParserHandlesEmptyInput() {
        assertTrue(SmartLifecycleTextParser.parseRunningPackages("").isEmpty());
        assertTrue(SmartLifecycleTextParser.parseRunningPackages(null).isEmpty());
    }
}
