package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.utils.triggers.analyzers.DozeOpsAnalyzer;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class DozeExemptionTest {

    // From an Android 16 device.
    private static final String WHITELIST = String.join("\n",
            "system-excidle,com.android.providers.calendar,10071",
            "system-excidle,com.android.shell,2000",
            "system,com.android.providers.calendar,10071",
            "system,com.android.shell,2000",
            "user,com.machiav3lli.backup,10171",
            "user,com.aurora.store,10170");

    @Test
    public void onlyAListedAppIsExempt() {
        assertEquals(DozeOpsAnalyzer.EXEMPT_USER,
                DozeOpsAnalyzer.exemption(WHITELIST, "com.aurora.store"));
        assertEquals(DozeOpsAnalyzer.EXEMPT_SYSTEM,
                DozeOpsAnalyzer.exemption(WHITELIST, "com.android.shell"));
        assertEquals(DozeOpsAnalyzer.EXEMPT_NONE,
                DozeOpsAnalyzer.exemption(WHITELIST, "de.example.app"));
        // A package name that is only part of a listed one is not listed.
        assertEquals(DozeOpsAnalyzer.EXEMPT_NONE,
                DozeOpsAnalyzer.exemption(WHITELIST, "com.aurora"));
        assertEquals(DozeOpsAnalyzer.EXEMPT_NONE, DozeOpsAnalyzer.exemption(null, "a"));
        assertEquals(DozeOpsAnalyzer.EXEMPT_NONE, DozeOpsAnalyzer.exemption("", "a"));
    }

    @Test
    public void theUsersOwnExemptionCountsBeforeTheSystemOne() {
        assertEquals(DozeOpsAnalyzer.EXEMPT_USER, DozeOpsAnalyzer.exemption(String.join("\n",
                "system,com.example.app,10001",
                "user,com.example.app,10001\r"), "com.example.app"));
    }

    @Test
    public void anAppWithoutABackgroundRestrictionIsNotCalledExempt() throws Exception {
        String analyzer = read(
                "app/src/main/java/com/gree1d/reappzuku/utils/triggers/analyzers/DozeOpsAnalyzer.java");
        assertTrue(analyzer.contains("\"dumpsys deviceidle whitelist\""));
        // "No operations. Default mode: allow" contains "allow": that check named every
        // unrestricted app exempt from battery optimization.
        assertFalse(analyzer.contains("ops.contains(\"allow\")"));
        // The grep kept the section headers of the dump and dropped the package lines below them.
        assertFalse(analyzer.contains("grep -E 'whitelist|except|power-save|restricted|exemption'"));
    }

    private static String read(String relative) throws Exception {
        for (Path candidate : Arrays.asList(Paths.get(""), Paths.get(".."), Paths.get("..", ".."))) {
            if (Files.isDirectory(candidate.resolve("app/src/main/java"))) {
                return Files.readString(candidate.resolve(relative)).replace("\r\n", "\n");
            }
        }
        throw new java.io.IOException("Could not locate repository root");
    }
}
