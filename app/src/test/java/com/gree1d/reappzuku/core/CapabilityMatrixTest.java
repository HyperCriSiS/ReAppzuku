package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.core.CapabilityMatrix.Availability;
import com.gree1d.reappzuku.core.CapabilityMatrix.Feature;
import com.gree1d.reappzuku.core.CapabilityMatrix.Mode;

import org.junit.Test;

public class CapabilityMatrixTest {

    private static final int API_30 = 30;
    private static final int API_29 = 29;

    @Test
    public void onlyReadyBackendsCountAsAnOperatingMode() {
        for (ShellBackendState state : ShellBackendState.values()) {
            Mode expected = state == ShellBackendState.ROOT_READY ? Mode.ROOT
                    : state == ShellBackendState.SHIZUKU_READY ? Mode.SHIZUKU
                    : Mode.NONE;
            assertEquals(state.name(), expected, CapabilityMatrix.modeOf(state));
        }
    }

    @Test
    public void withoutPrivilegeEveryInterventionIsUnavailable() {
        CapabilityMatrix matrix = new CapabilityMatrix(Mode.NONE, false, API_30, true);
        for (Feature feature : Feature.values()) {
            if (feature == Feature.EXACT_ALARMS) continue;
            assertEquals(feature.name(),
                    Availability.UNAVAILABLE_NEEDS_PRIVILEGE, matrix.of(feature));
        }
    }

    @Test
    public void privilegedModesAllowEverythingOnAndroid11() {
        for (Mode mode : new Mode[] {Mode.ROOT, Mode.SHIZUKU}) {
            CapabilityMatrix matrix = new CapabilityMatrix(mode, mode == Mode.SHIZUKU, API_30, true);
            for (Feature feature : Feature.values()) {
                if (feature == Feature.MEMORY_PSS) continue;
                assertEquals(mode + " " + feature, Availability.AVAILABLE, matrix.of(feature));
            }
        }
    }

    @Test
    public void restrictionAndCacheClearingNeedAndroid11() {
        CapabilityMatrix matrix = new CapabilityMatrix(Mode.ROOT, false, API_29, true);
        assertEquals(Availability.UNAVAILABLE_NEEDS_ANDROID_11,
                matrix.of(Feature.BACKGROUND_RESTRICTION));
        assertEquals(Availability.UNAVAILABLE_NEEDS_ANDROID_11, matrix.of(Feature.CLEAR_CACHE));
        assertEquals(Availability.AVAILABLE, matrix.of(Feature.KILL_APPS));
    }

    @Test
    public void pssNeedsTheShizukuServiceEvenWhenRootIsActive() {
        assertEquals(Availability.AVAILABLE,
                new CapabilityMatrix(Mode.SHIZUKU, true, API_30, true).of(Feature.MEMORY_PSS));
        assertEquals(Availability.AVAILABLE,
                new CapabilityMatrix(Mode.ROOT, true, API_30, true).of(Feature.MEMORY_PSS));
        assertEquals(Availability.DEGRADED_RSS_ONLY,
                new CapabilityMatrix(Mode.ROOT, false, API_30, true).of(Feature.MEMORY_PSS));
    }

    @Test
    public void exactAlarmsDependOnlyOnTheAlarmPermission() {
        assertEquals(Availability.DEGRADED_INEXACT_ALARMS,
                new CapabilityMatrix(Mode.ROOT, false, API_30, false).of(Feature.EXACT_ALARMS));
        assertEquals(Availability.AVAILABLE,
                new CapabilityMatrix(Mode.NONE, false, API_30, true).of(Feature.EXACT_ALARMS));
    }

    @Test
    public void degradedFeaturesStayUsable() {
        assertTrue(Availability.AVAILABLE.isUsable());
        assertTrue(Availability.DEGRADED_RSS_ONLY.isUsable());
        assertTrue(Availability.DEGRADED_INEXACT_ALARMS.isUsable());
        assertFalse(Availability.UNAVAILABLE_NEEDS_PRIVILEGE.isUsable());
        assertFalse(Availability.UNAVAILABLE_NEEDS_ANDROID_11.isUsable());
    }
}
