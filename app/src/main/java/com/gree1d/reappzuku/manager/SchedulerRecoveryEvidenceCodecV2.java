package com.gree1d.reappzuku.manager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Separate, deterministic RZJ2 format; never decode or overwrite the v1 journal.
 * The SHA-256 footer checks corruption, NOT authenticity, rollback or power loss.
 * No executable commands, trusted restore flags, or legacy bitmask conversion.
 */
public final class SchedulerRecoveryEvidenceCodecV2 {
    private static final int MAGIC = 0x525A4A32; // "RZJ2"
    public static final int VERSION = 2;
    public static final int MAX_RECORDS = 128;
    public static final int MAX_BYTES = 32 * 1024;
    private static final int SHA_BYTES = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SchedulerRecoveryEvidenceCodecV2() {}

    public static String encode(Map<String, SchedulerRecoveryEvidenceV2> records) {
        if (records == null || records.size() > MAX_RECORDS) {
            throw new IllegalArgumentException("invalid v2 record count");
        }
        try {
            ByteArrayOutputStream memory = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(memory);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(records.size());
            for (Map.Entry<String, SchedulerRecoveryEvidenceV2> entry
                    : new TreeMap<>(records).entrySet()) {
                SchedulerRecoveryEvidenceV2 row = entry.getValue();
                if (row == null || !entry.getKey().equals(row.packageName)) {
                    throw new IllegalArgumentException("invalid v2 package record");
                }
                out.writeUTF(row.packageName);
                out.writeInt(row.uid);
                out.writeByte(row.enabledSetting);
                out.writeBoolean(row.suspended);
                out.writeByte(row.standbyBucket);
                out.writeByte(row.whitelistKind.ordinal());
                out.writeLong(row.capturedAtMillis);
                out.writeLong(row.sequence);
                out.writeByte(row.phase.ordinal());
                out.writeByte(row.owners.size());
                for (Long owner : new TreeSet<>(row.owners)) out.writeLong(owner);
                out.writeByte(row.appOps.size());
                for (Map.Entry<String, SchedulerRestrictionObservation.AppOpMode> op
                        : row.appOps.entrySet()) {
                    out.writeUTF(op.getKey());
                    out.writeByte(op.getValue().ordinal());
                }
                if (memory.size() + SHA_BYTES > MAX_BYTES) {
                    throw new IllegalArgumentException("v2 evidence exceeds size limit");
                }
            }
            out.flush();
            byte[] body = memory.toByteArray();
            if (body.length + SHA_BYTES > MAX_BYTES) {
                throw new IllegalArgumentException("v2 evidence exceeds size limit");
            }
            byte[] digest = sha256(body);
            byte[] sealed = new byte[body.length + SHA_BYTES];
            System.arraycopy(body, 0, sealed, 0, body.length);
            System.arraycopy(digest, 0, sealed, body.length, SHA_BYTES);
            return hex(sealed);
        } catch (IOException impossible) {
            throw new IllegalStateException("memory serialization error", impossible);
        }
    }

    public static Map<String, SchedulerRecoveryEvidenceV2> decode(String encoded) {
        if (encoded == null) return Collections.emptyMap(); // never-written v2 only
        if (encoded.length() < (12 + SHA_BYTES) * 2
                || encoded.length() > MAX_BYTES * 2
                || (encoded.length() & 1) != 0) {
            throw new IllegalArgumentException("invalid v2 journal length");
        }
        byte[] sealed = unhex(encoded);
        int bodyLength = sealed.length - SHA_BYTES;
        if (bodyLength < 12) throw new IllegalArgumentException("truncated v2 journal");
        byte[] body = new byte[bodyLength];
        System.arraycopy(sealed, 0, body, 0, bodyLength);
        byte[] digest = sha256(body);
        byte[] actual = new byte[SHA_BYTES];
        System.arraycopy(sealed, bodyLength, actual, 0, SHA_BYTES);
        if (!MessageDigest.isEqual(digest, actual)) {
            throw new IllegalArgumentException("v2 digest mismatch");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IllegalArgumentException("unsupported v2 format");
            }
            int count = in.readInt();
            if (count < 0 || count > MAX_RECORDS) {
                throw new IllegalArgumentException("invalid v2 row count");
            }
            Map<String, SchedulerRecoveryEvidenceV2> rows = new HashMap<>();
            for (int i = 0; i < count; i++) {
                String pkg = in.readUTF();
                int uid = in.readInt();
                int enabled = in.readUnsignedByte();
                boolean suspended = in.readBoolean();
                int bucket = in.readUnsignedByte();
                int whitelist = in.readUnsignedByte();
                if (whitelist >= SchedulerRestrictionObservation.WhitelistKind.values().length) {
                    throw new IllegalArgumentException("invalid whitelist source");
                }
                long capturedAt = in.readLong();
                long sequence = in.readLong();
                int phase = in.readUnsignedByte();
                if (phase >= SchedulerRecoveryEvidenceV2.Phase.values().length) {
                    throw new IllegalArgumentException("invalid evidence phase");
                }
                int ownerCount = in.readUnsignedByte();
                if (ownerCount < 1 || ownerCount > SchedulerRecoveryEvidenceV2.MAX_OWNERS) {
                    throw new IllegalArgumentException("invalid evidence owners");
                }
                java.util.Set<Long> owners = new java.util.HashSet<>();
                for (int j = 0; j < ownerCount; j++) {
                    if (!owners.add(in.readLong())) {
                        throw new IllegalArgumentException("duplicate owner");
                    }
                }
                int opCount = in.readUnsignedByte();
                if (opCount > SchedulerRecoveryEvidenceV2.MAX_APP_OPS) {
                    throw new IllegalArgumentException("too many AppOps");
                }
                Map<String, SchedulerRestrictionObservation.AppOpMode> ops = new HashMap<>();
                for (int j = 0; j < opCount; j++) {
                    String name = in.readUTF();
                    int mode = in.readUnsignedByte();
                    if (mode >= SchedulerRestrictionObservation.AppOpMode.values().length
                            || ops.put(name, SchedulerRestrictionObservation.AppOpMode.values()[mode]) != null) {
                        throw new IllegalArgumentException("duplicate/invalid AppOp mode");
                    }
                }
                SchedulerRecoveryEvidenceV2 row = SchedulerRecoveryEvidenceV2.rehydrate(
                        pkg, uid, enabled, suspended, bucket,
                        SchedulerRestrictionObservation.WhitelistKind.values()[whitelist],
                        ops, owners, capturedAt, sequence,
                        SchedulerRecoveryEvidenceV2.Phase.values()[phase]);
                if (rows.put(pkg, row) != null) {
                    throw new IllegalArgumentException("duplicate v2 package");
                }
            }
            if (in.available() != 0) {
                throw new IllegalArgumentException("trailing journal content");
            }
            // Reject valid-digest but noncanonical (unordered/uppercase) images.
            if (!encode(rows).equals(encoded)) {
                throw new IllegalArgumentException("noncanonical v2 journal");
            }
            return Collections.unmodifiableMap(rows);
        } catch (IOException truncated) {
            throw new IllegalArgumentException("invalid v2 evidence stream", truncated);
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[2 * i] = HEX[(bytes[i] >>> 4) & 15];
            result[2 * i + 1] = HEX[bytes[i] & 15];
        }
        return new String(result);
    }

    private static byte[] unhex(String encoded) {
        byte[] result = new byte[encoded.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(encoded.charAt(i * 2), 16);
            int lo = Character.digit(encoded.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("invalid v2 hex");
            result[i] = (byte) ((hi << 4) | lo);
        }
        return result;
    }
}
