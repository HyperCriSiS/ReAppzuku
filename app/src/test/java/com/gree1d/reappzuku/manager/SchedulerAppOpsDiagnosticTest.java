package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import org.junit.Test;

public final class SchedulerAppOpsDiagnosticTest {
    private static final String OP = "RUN_IN_BACKGROUND";

    @Test public void uidOverrideBeatsConflictingExplicitPackageMode() {
        SchedulerAppOpsDiagnostic.Result read = SchedulerAppOpsDiagnostic.parse(
                "Uid mode: RUN_IN_BACKGROUND: ignore\n"
                        + "RUN_IN_BACKGROUND: allow; time=+2m ago", OP);
        assertNotNull(read);
        assertEquals("allow", read.explicitPackageMode);
        assertEquals("ignore", read.explicitUidMode);
        assertEquals("ignore", read.observedEffectiveMode);
        assertTrue(read.uidOverridesPackage);
        assertNull("v2 cannot encode UID-vs-package provenance",
                SchedulerAppOpsDiagnostic.packageOnly(
                        "Uid mode: RUN_IN_BACKGROUND: ignore\nRUN_IN_BACKGROUND: allow", OP));
    }

    @Test public void allExactModesRemainDistinctAndPackageOnlyIsConservative() {
        for (String value : new String[]{"allow", "default", "ignore", "foreground",
                "errored", "deny", "ask"}) {
            SchedulerAppOpsDiagnostic.Result result =
                    SchedulerAppOpsDiagnostic.parse(OP + ": " + value, OP);
            assertNotNull(value, result);
            assertEquals(value, result.explicitPackageMode);
            assertNull(result.explicitUidMode);
            assertEquals(value, SchedulerAppOpsDiagnostic.packageOnly(OP + ": " + value, OP));
        }
        SchedulerAppOpsDiagnostic.Result uidOnly =
                SchedulerAppOpsDiagnostic.parse("Uid mode: " + OP + ": default", OP);
        assertNotNull(uidOnly);
        assertEquals("default", uidOnly.observedEffectiveMode);
        assertNull(uidOnly.explicitPackageMode);
        assertNull(SchedulerAppOpsDiagnostic.packageOnly("Uid mode: " + OP + ": default", OP));
    }

    @Test public void incompleteDuplicateOrHostileOutputNeverProducesAnEffectiveMode() {
        String[] invalid = {
                "No operations.\nDefault mode: allow",
                "Default mode: ignore",
                "Uid mode: allow\nRUN_IN_BACKGROUND: allow",
                "Uid mode: RUN_IN_BACKGROUND: ignore\nUid mode: RUN_IN_BACKGROUND: allow",
                "RUN_IN_BACKGROUND: allow\nRUN_IN_BACKGROUND: ignore",
                "RUN_IN_BACKGROUND: allow\nError: permission denied",
                "RUN_IN_BACKGROUND: ROOT",
                "Uid mode: WAKE_LOCK: allow\nRUN_IN_BACKGROUND: default",
                "RUN_IN_BACKGROUND: allow (running)",
                "RUN_IN_BACKGROUND: allow; untrusted=warning",
                "RUN_IN_BACKGROUND: allow; time=",
                "RUN_IN_BACKGROUND: allow; Error: permission denied"
        };
        for (String text : invalid) {
            assertNull(text, SchedulerAppOpsDiagnostic.parse(text, OP));
            assertNull(text, SchedulerAppOpsDiagnostic.packageOnly(text, OP));
        }
        assertNull(SchedulerAppOpsDiagnostic.parse(OP + ": allow", "OP;bad"));
        assertNull(SchedulerAppOpsDiagnostic.parse("", OP));
        assertNull(SchedulerAppOpsDiagnostic.parse(null, OP));
    }
}