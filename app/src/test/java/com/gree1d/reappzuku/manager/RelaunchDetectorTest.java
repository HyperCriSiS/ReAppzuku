package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Set;

import org.junit.Test;

public class RelaunchDetectorTest {

    @Test
    public void deduplicatesMultipleProcessesFromSamePackage() {
        String ps = "com.example.alpha\n"
                + "com.example.alpha:push\n"
                + "com.example.beta:remote\n";

        Set<String> result = RelaunchDetector.findRelaunchedPackages(
                Arrays.asList("com.example.alpha", "com.example.beta"), ps);

        assertEquals(2, result.size());
        assertTrue(result.contains("com.example.alpha"));
        assertTrue(result.contains("com.example.beta"));
    }

    @Test
    public void ignoresPackagesThatWereNotKilled() {
        String ps = "com.example.other\ncom.example.alphaish\n";

        Set<String> result = RelaunchDetector.findRelaunchedPackages(
                Arrays.asList("com.example.alpha"), ps);

        assertTrue(result.isEmpty());
    }

    @Test
    public void emptyProcessSnapshotProducesNoRelaunches() {
        assertTrue(RelaunchDetector.findRelaunchedPackages(
                Arrays.asList("com.example.alpha"), "").isEmpty());
        assertTrue(RelaunchDetector.findRelaunchedPackages(
                Arrays.asList("com.example.alpha"), null).isEmpty());
    }
}
