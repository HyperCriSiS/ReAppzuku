package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProcessTopologyTest {
    @Test
    public void exactShizukuSuffixIsProviderProcess() {
        assertTrue(ProcessTopology.isShizukuProviderProcess(
                "com.gree1d.reappzuku", "com.gree1d.reappzuku:shizuku"));
    }

    @Test
    public void normalAndOtherProcessesAreNotProviderProcess() {
        assertFalse(ProcessTopology.isShizukuProviderProcess(
                "com.gree1d.reappzuku", "com.gree1d.reappzuku"));
        assertFalse(ProcessTopology.isShizukuProviderProcess(
                "com.gree1d.reappzuku", "com.gree1d.reappzuku:shell_service"));
        assertFalse(ProcessTopology.isShizukuProviderProcess(
                "com.gree1d.reappzuku", "com.gree1d.reappzuku:shizuku-extra"));
    }

    @Test
    public void missingIdentityFailsClosedToNormalProcessClassification() {
        assertFalse(ProcessTopology.isShizukuProviderProcess(null, "com.gree1d.reappzuku:shizuku"));
        assertFalse(ProcessTopology.isShizukuProviderProcess("com.gree1d.reappzuku", null));
    }
}
