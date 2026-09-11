package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class PackageStateSourceTest {
    @Test
    public void parsesPidNameAndNormalizesRemoteProcesses() {
        List<PackageStateSource.ProcessSample> samples = PackageStateSource.parsePidName(
                "PID NAME\n"
                        + "123 com.example.app\n"
                        + "124 com.example.app:remote\n"
                        + "125 system_server\n"
                        + "bad com.example.bad\n");

        assertEquals(2, samples.size());
        assertEquals("com.example.app", samples.get(0).packageName);
        assertEquals(123, samples.get(0).pid);
        assertEquals(0L, samples.get(0).rssKb);
        assertEquals("com.example.app", samples.get(1).packageName);
        assertEquals(124, samples.get(1).pid);
    }

    @Test
    public void parsesRssAndRejectsMalformedOrNonPackageRows() {
        List<PackageStateSource.ProcessSample> samples = PackageStateSource.parsePidRssName(
                "PID RSS NAME\r\n"
                        + "201 4096 com.example.one\r\n"
                        + "202 2048 com.example.two:worker\r\n"
                        + "203 nope com.example.bad\r\n"
                        + "1 10 com.example.init\r\n"
                        + "204 -1 com.example.bad\r\n"
                        + "205 5 ERROR:broken\r\n");

        assertEquals(2, samples.size());
        assertEquals(4096L, samples.get(0).rssKb);
        assertEquals("com.example.two", samples.get(1).packageName);
        assertEquals(202, samples.get(1).pid);
        assertEquals(2048L, samples.get(1).rssKb);
    }

    @Test
    public void emptyAndNullOutputAreEmpty() {
        assertTrue(PackageStateSource.parsePidName(null).isEmpty());
        assertTrue(PackageStateSource.parsePidRssName("  \n").isEmpty());
    }
}
