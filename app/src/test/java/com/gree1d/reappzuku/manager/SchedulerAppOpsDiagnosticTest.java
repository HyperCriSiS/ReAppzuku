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

    @Test public void realAospUidAndPackageRowShapeRemainsLossless() {
        String raw = "Uid mode: WAKE_LOCK: ignore\nWAKE_LOCK: allow";
        SchedulerAppOpsDiagnostic.Result result =
                SchedulerAppOpsDiagnostic.parse(raw, "WAKE_LOCK");
        assertNotNull(result);
        assertTrue(result.uidOverridesPackage);
        assertEquals("ignore", result.explicitUidMode);
        assertEquals("allow", result.explicitPackageMode);
        assertEquals("ignore", result.observedEffectiveMode);
        assertNull(SchedulerAppOpsDiagnostic.packageOnly(raw, "WAKE_LOCK"));
        // An absent explicitly set value is not the same as explicit DEFAULT.
        assertNull(SchedulerAppOpsDiagnostic.parse(
                "No operations.\nDefault mode: allow", "WAKE_LOCK"));
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

    @Test public void commandWarningsAndUnrelatedRowsCannotBeSilentlyDiscarded() {
        String[] malformed = {
                "RUN_IN_BACKGROUND: allow\nWarning: operation not supported",
                "Warning: permission denied\nRUN_IN_BACKGROUND: allow",
                "RUN_IN_BACKGROUND: allow\nPermission denied",
                "RUN_IN_BACKGROUND: allow\nWAKE_LOCK: ignore",
                "RUN_IN_BACKGROUND: allow\nUid mode: WAKE_LOCK: ignore",
                "RUN_IN_BACKGROUND: allow\nNo operations.",
                "RUN_IN_BACKGROUND: allow\nDefault mode: allow",
                "RUN_IN_BACKGROUND: allow\n",
                "RUN_IN_BACKGROUND: allow\n\nRUN_IN_BACKGROUND: ignore",
                "Uid mode: RUN_IN_BACKGROUND: ignore\nuid:10088 not installed",
                "RUN_IN_BACKGROUND: allow\njava.lang.SecurityException: permission denied"
        };
        for (String raw : malformed) {
            // Java's String.split discards trailing blank lines; a lone terminal
            // newline is harmless output formatting, not a second record.
            if (raw.equals("RUN_IN_BACKGROUND: allow\n")) {
                assertNotNull(SchedulerAppOpsDiagnostic.parse(raw, OP));
                continue;
            }
            assertNull(raw, SchedulerAppOpsDiagnostic.parse(raw, OP));
            assertNull(raw, SchedulerAppOpsDiagnostic.packageOnly(raw, OP));
        }
    }

    @Test public void absenceIsNotExplicitAllowOrDefaultAndCannotAuthorizeLegacyMode() {
        for (String absent : new String[]{
                "No operations.", "No operations.\nDefault mode: allow",
                "Default mode: allow", "No operations.\nDefault mode: default"
        }) {
            assertNull(SchedulerAppOpsDiagnostic.parse(absent, "WAKE_LOCK"));
            assertNull(SchedulerAppOpsDiagnostic.packageOnly(absent, "WAKE_LOCK"));
        }
        for (String explicit : new String[]{"allow", "default"}) {
            SchedulerAppOpsDiagnostic.Result result =
                    SchedulerAppOpsDiagnostic.parse("WAKE_LOCK: " + explicit, "WAKE_LOCK");
            assertNotNull(result);
            assertEquals(explicit, result.explicitPackageMode);
        }
        assertNull(SchedulerAppOpsDiagnostic.packageOnly(
                "Uid mode: WAKE_LOCK: ignore\nWAKE_LOCK: allow", "WAKE_LOCK"));
        assertNull(SchedulerAppOpsDiagnostic.parse(
                "WAKE_LOCK: allow\nError: No UID for 1000000 in user 0", "WAKE_LOCK"));
    }
}
