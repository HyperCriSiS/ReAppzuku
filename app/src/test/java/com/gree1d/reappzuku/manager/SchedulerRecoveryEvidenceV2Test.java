package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SchedulerRecoveryEvidenceV2Test {
    private static final String PKG = "com.example.original";
    private static final String[] OPS = {"WAKE_LOCK", "RUN_IN_BACKGROUND", "GET_USAGE_STATS"};

    private static final class Source implements SchedulerRestrictionObservation.Source {
        Map<String, String> modes = new HashMap<>();
        Integer uid = 10421;
        Integer setting = 0;
        Boolean suspended = false;
        Integer bucket = 45;
        SchedulerRestrictionObservation.WhitelistKind whitelist =
                SchedulerRestrictionObservation.WhitelistKind.BOTH;
        Source() {
            modes.put("WAKE_LOCK", "allow");
            modes.put("RUN_IN_BACKGROUND", "default");
            modes.put("GET_USAGE_STATS", "ignore");
        }
        @Override public Integer uid(String pkg) { return uid; }
        @Override public Integer enabledSetting(String pkg) { return setting; }
        @Override public Boolean suspended(String pkg) { return suspended; }
        @Override public Integer standbyBucket(String pkg) { return bucket; }
        @Override public SchedulerRestrictionObservation.WhitelistKind whitelistKind(String pkg) {
            return whitelist;
        }
        @Override public String appOpMode(String pkg, String op) { return modes.get(op); }
    }

    private static SchedulerRecoveryEvidenceV2 evidence() {
        return evidence(new Source());
    }

    private static SchedulerRecoveryEvidenceV2 evidence(Source source) {
        SchedulerRestrictionObservation.Result result =
                SchedulerRestrictionObservation.capture(PKG, OPS, source);
        assertTrue("fixture must be complete", result.complete());
        return SchedulerRecoveryEvidenceV2.fromObservation(
                result, new HashSet<>(Arrays.asList(7L, 12L)), 1781000000000L);
    }

    @Test public void everyCapturedFieldRoundTripsWithoutBitmaskLoss() {
        SchedulerRecoveryEvidenceV2 before = evidence();
        Map<String, SchedulerRecoveryEvidenceV2> original = new HashMap<>();
        original.put(PKG, before);
        String wire = SchedulerRecoveryEvidenceCodecV2.encode(original);
        SchedulerRecoveryEvidenceV2 after =
                SchedulerRecoveryEvidenceCodecV2.decode(wire).get(PKG);
        assertNotNull(after);
        assertEquals(2, SchedulerRecoveryEvidenceCodecV2.VERSION);
        assertEquals(10421, after.uid);
        assertEquals(0, after.enabledSetting);
        assertFalse(after.suspended);
        assertEquals(45, after.standbyBucket);
        assertEquals(SchedulerRestrictionObservation.WhitelistKind.BOTH, after.whitelistKind);
        assertEquals(SchedulerRestrictionObservation.AppOpMode.ALLOW, after.appOps.get("WAKE_LOCK"));
        assertEquals(SchedulerRestrictionObservation.AppOpMode.DEFAULT,
                after.appOps.get("RUN_IN_BACKGROUND"));
        assertEquals(SchedulerRestrictionObservation.AppOpMode.IGNORE,
                after.appOps.get("GET_USAGE_STATS"));
        assertEquals(new HashSet<>(Arrays.asList(7L, 12L)), after.owners);
        assertEquals(1781000000000L, after.capturedAtMillis);
        assertEquals(1L, after.sequence);
        assertEquals(SchedulerRecoveryEvidenceV2.Phase.OBSERVED, after.phase);
        assertThrows(UnsupportedOperationException.class, after.owners::clear);
        assertThrows(UnsupportedOperationException.class, after.appOps::clear);
    }

    @Test public void canonicalEncodingIgnoresMapAndOwnerInsertionOrder() {
        SchedulerRecoveryEvidenceV2 one = evidence();
        SchedulerRecoveryEvidenceV2 two = SchedulerRecoveryEvidenceV2.rehydrate(
                "com.example.second", 10501, 1, true, 10,
                SchedulerRestrictionObservation.WhitelistKind.SYSTEM,
                Collections.singletonMap("WAKE_LOCK", SchedulerRestrictionObservation.AppOpMode.FOREGROUND),
                Collections.singleton(19L), 1781000000000L, 1,
                SchedulerRecoveryEvidenceV2.Phase.OBSERVED);
        Map<String, SchedulerRecoveryEvidenceV2> a = new HashMap<>();
        a.put(PKG, one);
        a.put(two.packageName, two);
        Map<String, SchedulerRecoveryEvidenceV2> b = new java.util.LinkedHashMap<>();
        b.put(two.packageName, two);
        b.put(PKG, one);
        assertEquals(SchedulerRecoveryEvidenceCodecV2.encode(a),
                SchedulerRecoveryEvidenceCodecV2.encode(b));
    }

    @Test public void noPartialObservationCanBePromotedToEvidence() {
        Source source = new Source();
        source.modes.remove("RUN_IN_BACKGROUND");
        SchedulerRestrictionObservation.Result partial =
                SchedulerRestrictionObservation.capture(PKG, OPS, source);
        assertEquals(SchedulerRestrictionObservation.Status.UNREADABLE, partial.status);
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceV2.fromObservation(
                        partial, Collections.singleton(1L), 1781000000000L));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceV2.fromObservation(
                        SchedulerRestrictionObservation.notReady(),
                        Collections.singleton(1L), 1781000000000L));
    }

    @Test public void malformedOwnerTimestampModeAndRevisionAreRejected() {
        SchedulerRecoveryEvidenceV2 row = evidence();
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceV2.rehydrate(PKG, row.uid, 0, false, 40,
                        row.whitelistKind, row.appOps, Collections.singleton(-1L), 1L,
                        1, SchedulerRecoveryEvidenceV2.Phase.OBSERVED));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceV2.rehydrate(PKG, row.uid, 0, false, 40,
                        row.whitelistKind, row.appOps, row.owners, 0L,
                        1, SchedulerRecoveryEvidenceV2.Phase.OBSERVED));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceV2.rehydrate(PKG, row.uid, 0, false, 40,
                        row.whitelistKind, Collections.singletonMap("OP;INJECT",
                                SchedulerRestrictionObservation.AppOpMode.DEFAULT),
                        row.owners, 1L, 1, SchedulerRecoveryEvidenceV2.Phase.OBSERVED));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceV2.rehydrate(PKG, row.uid, 0, false, 40,
                        row.whitelistKind, row.appOps, row.owners, 1L,
                        2, SchedulerRecoveryEvidenceV2.Phase.OBSERVED));
    }

    @Test public void reviewOnlyTransitionDoesNotDestroyOriginalOrPermitRestart() {
        SchedulerRecoveryEvidenceV2 first = evidence();
        SchedulerRecoveryEvidenceV2 review = first.requireManualReview();
        assertEquals(SchedulerRecoveryEvidenceV2.Phase.REVIEW_REQUIRED, review.phase);
        assertEquals(2, review.sequence);
        assertTrue(SchedulerRecoveryEvidenceV2.sameOriginal(first, review));
        assertThrows(IllegalStateException.class, review::requireManualReview);
        Map<String, SchedulerRecoveryEvidenceV2> one = Collections.singletonMap(PKG, review);
        assertEquals(SchedulerRecoveryEvidenceV2.Phase.REVIEW_REQUIRED,
                SchedulerRecoveryEvidenceCodecV2.decode(
                        SchedulerRecoveryEvidenceCodecV2.encode(one)).get(PKG).phase);
    }

    @Test public void v1AndTamperedV2NeverParseAsValidEvidence() {
        SchedulerRecoveryEvidenceV2 row = evidence();
        String v1 = SchedulerRecoveryJournalCodec.encode(
                Collections.singletonMap(PKG, SchedulerRecoveryTransaction.prepare(
                        PKG, Collections.singleton(7L),
                        new SchedulerRecoveryTransaction.OriginalRestrictions(
                                3, 40, false, false, true), 1)));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.decode(v1));
        String v2 = SchedulerRecoveryEvidenceCodecV2.encode(Collections.singletonMap(PKG, row));
        String corrupted = v2.substring(0, 20)
                + (v2.charAt(20) == '0' ? '1' : '0') + v2.substring(21);
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.decode(corrupted));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.decode(v2.substring(0, v2.length() - 2)));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.decode(""));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.decode(v2 + "g"));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.decode("not hex"));
        assertTrue(SchedulerRecoveryEvidenceCodecV2.decode(null).isEmpty());
        assertTrue(SchedulerRecoveryEvidenceCodecV2.decode(
                SchedulerRecoveryEvidenceCodecV2.encode(Collections.emptyMap())).isEmpty());
    }

    @Test public void sizeLimitsAndInvalidPackageKeysCannotBeWritten() {
        Map<String, SchedulerRecoveryEvidenceV2> invalid = new HashMap<>();
        invalid.put("com.example.different", evidence());
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.encode(invalid));
        invalid.clear();
        SchedulerRecoveryEvidenceV2 row = evidence();
        for (int i = 0; i <= SchedulerRecoveryEvidenceCodecV2.MAX_RECORDS; i++) {
            String pkg = "com.example.p" + i;
            invalid.put(pkg, SchedulerRecoveryEvidenceV2.rehydrate(pkg, row.uid, 0, false, 45,
                    row.whitelistKind, row.appOps, row.owners,
                    1781000000000L, 1, SchedulerRecoveryEvidenceV2.Phase.OBSERVED));
        }
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.encode(invalid));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryEvidenceCodecV2.decode(String.join("",
                        Collections.nCopies(SchedulerRecoveryEvidenceCodecV2.MAX_BYTES * 2 + 1, "0"))));
    }
}
