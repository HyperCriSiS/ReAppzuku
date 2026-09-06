package com.gree1d.reappzuku.utils.triggers.analyzers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ProcessAnalyzerCommandTest {
    @Test
    public void servicesDumpUsesGlobalPackageFilterBeforeSubcommand() {
        assertEquals(
                "dumpsys activity -p com.example.app services",
                ProcessAnalyzer.buildServicesDumpCommand("com.example.app"));
    }

    @Test
    public void servicesDumpRejectsShellMetacharacters() {
        assertThrows(IllegalArgumentException.class, () ->
                ProcessAnalyzer.buildServicesDumpCommand("com.example.app;id"));
    }
}
