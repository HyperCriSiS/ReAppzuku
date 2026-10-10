package com.gree1d.reappzuku.manager;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SchedulerRecoveryJournalCodecTest {
    private static SchedulerRecoveryTransaction.Record record(String pkg, long owner) {
        return SchedulerRecoveryTransaction.prepare(
                pkg, Collections.singleton(owner),
                new SchedulerRecoveryTransaction.OriginalRestrictions(
                        11, 45, true, false, true), 1);
    }

    private static Map<String, SchedulerRecoveryTransaction.Record> entries() {
        Map<String, SchedulerRecoveryTransaction.Record> map = new HashMap<>();
        map.put("com.example.second", record("com.example.second", 7));
        map.put("com.example.first", record("com.example.first", 4).addOwner(8));
        return map;
    }

    @Test public void canonicalEncodingIsIndependentOfMapInsertionOrder() {
        Map<String, SchedulerRecoveryTransaction.Record> map = entries();
        String first = SchedulerRecoveryJournalCodec.encode(map);
        Map<String, SchedulerRecoveryTransaction.Record> reversed = new java.util.LinkedHashMap<>();
        reversed.put("com.example.first", map.get("com.example.first"));
        reversed.put("com.example.second", map.get("com.example.second"));
        assertEquals(first, SchedulerRecoveryJournalCodec.encode(reversed));
    }

    @Test public void roundTripPreservesCapturedValuesAndOwners() {
        Map<String, SchedulerRecoveryTransaction.Record> recovered =
                SchedulerRecoveryJournalCodec.decode(SchedulerRecoveryJournalCodec.encode(entries()));
        assertEquals(2, recovered.size());
        SchedulerRecoveryTransaction.Record row = recovered.get("com.example.first");
        assertEquals(SchedulerRecoveryTransaction.FORMAT_VERSION, row.version);
        assertEquals(SchedulerRecoveryTransaction.Phase.PREPARED, row.phase);
        assertEquals(new HashSet<>(Arrays.asList(4L, 8L)), row.owners);
        assertEquals(11, row.original.appOpsMask);
        assertEquals(45, row.original.standbyBucket);
        assertTrue(row.original.deviceIdleWhitelisted);
        assertFalse(row.original.suspended);
        assertTrue(row.original.enabled);
        assertEquals(2, row.sequence);
        assertThrows(UnsupportedOperationException.class, recovered::clear);
        assertThrows(UnsupportedOperationException.class, row.owners::clear);
    }

    @Test public void emptyUnwrittenJournalAndWrittenEmptySnapshotAreDistinct() {
        assertTrue(SchedulerRecoveryJournalCodec.decode(null).isEmpty());
        assertTrue(SchedulerRecoveryJournalCodec.decode(SchedulerRecoveryJournalCodec.encode(
                Collections.emptyMap())).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryJournalCodec.decode(""));
    }

    @Test public void digestMismatchFailsClosed() {
        String clean = SchedulerRecoveryJournalCodec.encode(entries());
        String damaged = clean.substring(0, 20) + (clean.charAt(20) == 'a' ? 'b' : 'a')
                + clean.substring(21);
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryJournalCodec.decode(damaged));
    }

    @Test public void invalidHexAndTruncatedInputsFailClosed() {
        String clean = SchedulerRecoveryJournalCodec.encode(entries());
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryJournalCodec.decode(clean.substring(0, clean.length() - 2)));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryJournalCodec.decode(clean + "g"));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryJournalCodec.decode("!0" + clean.substring(2)));
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryJournalCodec.decode("aa"));
    }

    @Test public void sizeAndRecordCountLimitsFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                SchedulerRecoveryJournalCodec.decode(
                        String.join("", Collections.nCopies(SchedulerRecoveryJournalCodec.MAX_BYTES * 2 + 1, "0"))));
        Map<String, SchedulerRecoveryTransaction.Record> map = new HashMap<>();
        for (int i = 0; i < SchedulerRecoveryJournalCodec.MAX_RECORDS + 1; i++) {
            String pkg = "com.example.pkg" + i;
            map.put(pkg, record(pkg, 1L));
        }
        assertThrows(IllegalArgumentException.class, () -> SchedulerRecoveryJournalCodec.encode(map));
    }

    @Test public void mismatchedPackageKeyIsRejected() {
        Map<String, SchedulerRecoveryTransaction.Record> map = new HashMap<>();
        map.put("com.example.wrong", record("com.example.other", 1L));
        assertThrows(IllegalArgumentException.class, () -> SchedulerRecoveryJournalCodec.encode(map));
    }

    @Test public void unknownVersionAndTrailingContentFailClosedEvenWithValidDigest() throws Exception {
        String original = SchedulerRecoveryJournalCodec.encode(entries());
        byte[] body = body(original);
        body[7] = 2; // VERSION field
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryJournalCodec.decode(seal(body)));
        byte[] trailing = Arrays.copyOf(body(original), body(original).length + 1);
        trailing[trailing.length - 1] = 1;
        assertThrows(IllegalArgumentException.class,
                () -> SchedulerRecoveryJournalCodec.decode(seal(trailing)));
    }

    @Test public void duplicatePackageAndDuplicateOwnerFailEvenWithValidDigest() throws Exception {
        byte[] packageDuplicate = synthetic(new String[]{"com.example.same", "com.example.same"},
                new long[][]{{1L}, {2L}});
        assertThrows(IllegalArgumentException.class, () ->
                SchedulerRecoveryJournalCodec.decode(seal(packageDuplicate)));
        byte[] ownerDuplicate = synthetic(new String[]{"com.example.same"},
                new long[][]{{1L, 1L}});
        assertThrows(IllegalArgumentException.class, () ->
                SchedulerRecoveryJournalCodec.decode(seal(ownerDuplicate)));
    }

    @Test public void corruptStateAndAbsentOwnerAreRejectedEvenWithValidDigest() throws Exception {
        byte[] invalidPhase = synthetic(new String[]{"com.example.one"}, new long[][]{{1L}});
        // Byte after magic/version/count + modified UTF length and 15 ASCII package chars
        int phaseOffset = 12 + 2 + "com.example.one".length();
        invalidPhase[phaseOffset] = (byte) 127;
        assertThrows(IllegalArgumentException.class, () ->
                SchedulerRecoveryJournalCodec.decode(seal(invalidPhase)));
        byte[] withoutOwner = synthetic(new String[]{"com.example.one"}, new long[][]{{}});
        assertThrows(IllegalArgumentException.class, () ->
                SchedulerRecoveryJournalCodec.decode(seal(withoutOwner)));
    }

    @Test public void actualPhaseAndSequenceSurviveRoundTrip() {
        SchedulerRecoveryTransaction.Record initial = record("com.example.one", 1L);
        final SchedulerRecoveryTransaction.Record[] committed = {null};
        SchedulerRecoveryTransaction.beginLift(initial, value -> {
            committed[0] = value;
            return true;
        }, () -> true);
        SchedulerRecoveryTransaction.Record active = committed[0];
        SchedulerRecoveryTransaction.Record restore = active.removeOwner(1);
        String text = SchedulerRecoveryJournalCodec.encode(
                Collections.singletonMap(restore.packageName, restore));
        SchedulerRecoveryTransaction.Record reloaded = SchedulerRecoveryJournalCodec.decode(text)
                .get(restore.packageName);
        assertEquals(SchedulerRecoveryTransaction.Phase.RESTORE_REQUIRED, reloaded.phase);
        assertEquals(restore.sequence, reloaded.sequence);
        assertTrue(reloaded.owners.isEmpty());
    }

    private static byte[] body(String hex) {
        byte[] raw = new byte[hex.length() / 2 - 32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return raw;
    }

    private static String seal(byte[] raw) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
        byte[] full = Arrays.copyOf(raw, raw.length + digest.length);
        System.arraycopy(digest, 0, full, raw.length, digest.length);
        char[] alphabet = "0123456789abcdef".toCharArray();
        char[] encoded = new char[full.length * 2];
        for (int i = 0; i < full.length; i++) {
            encoded[2 * i] = alphabet[(full[i] >>> 4) & 15];
            encoded[2 * i + 1] = alphabet[full[i] & 15];
        }
        return new String(encoded);
    }

    private static byte[] synthetic(String[] packages, long[][] owners) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        out.writeInt(0x525A4A31);
        out.writeInt(1);
        out.writeInt(packages.length);
        for (int i = 0; i < packages.length; i++) {
            out.writeUTF(packages[i]);
            out.writeByte(SchedulerRecoveryTransaction.Phase.PREPARED.ordinal());
            out.writeLong(1L);
            out.writeInt(1);
            out.writeInt(40);
            out.writeBoolean(false);
            out.writeBoolean(false);
            out.writeBoolean(true);
            out.writeByte(owners[i].length);
            for (long owner : owners[i]) out.writeLong(owner);
        }
        out.flush();
        return b.toByteArray();
    }
}
