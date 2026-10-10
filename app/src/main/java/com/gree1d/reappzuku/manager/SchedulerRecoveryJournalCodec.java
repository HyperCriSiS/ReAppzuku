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
 * Strict versioned, bounded, canonical recovery snapshot. No Android or shell APIs.
 * SHA-256 detects corrupted/torn bytes; it is not an authenticity signature.
 * Nonportable runtime journal: never import it from user backup content.
 */
public final class SchedulerRecoveryJournalCodec {
    private static final int MAGIC = 0x525A4A31; // RZJ1
    public static final int VERSION = 1;
    public static final int MAX_RECORDS = 128;
    public static final int MAX_BYTES = 32 * 1024;
    private static final int DIGEST_BYTES = 32;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SchedulerRecoveryJournalCodec() {}

    public static String encode(Map<String, SchedulerRecoveryTransaction.Record> records) {
        if (records == null || records.size() > MAX_RECORDS) {
            throw new IllegalArgumentException("invalid journal count");
        }
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(records.size());
            for (Map.Entry<String, SchedulerRecoveryTransaction.Record> entry
                    : new TreeMap<>(records).entrySet()) {
                SchedulerRecoveryTransaction.Record record = entry.getValue();
                if (record == null || !entry.getKey().equals(record.packageName)
                        || record.version != SchedulerRecoveryTransaction.FORMAT_VERSION) {
                    throw new IllegalArgumentException("invalid recovery entry");
                }
                out.writeUTF(record.packageName);
                out.writeByte(record.phase.ordinal());
                out.writeLong(record.sequence);
                out.writeInt(record.original.appOpsMask);
                out.writeInt(record.original.standbyBucket);
                out.writeBoolean(record.original.deviceIdleWhitelisted);
                out.writeBoolean(record.original.suspended);
                out.writeBoolean(record.original.enabled);
                out.writeByte(record.owners.size());
                for (Long owner : new TreeSet<>(record.owners)) out.writeLong(owner);
                if (raw.size() + DIGEST_BYTES > MAX_BYTES) {
                    throw new IllegalArgumentException("journal is too large");
                }
            }
            out.flush();
            byte[] bytes = raw.toByteArray();
            if (bytes.length + DIGEST_BYTES > MAX_BYTES) {
                throw new IllegalArgumentException("journal is too large");
            }
            byte[] digest = sha256(bytes);
            byte[] sealed = new byte[bytes.length + DIGEST_BYTES];
            System.arraycopy(bytes, 0, sealed, 0, bytes.length);
            System.arraycopy(digest, 0, sealed, bytes.length, DIGEST_BYTES);
            return hex(sealed);
        } catch (IOException impossibleForByteArray) {
            throw new IllegalStateException(impossibleForByteArray);
        }
    }

    public static Map<String, SchedulerRecoveryTransaction.Record> decode(String encoded) {
        if (encoded == null) return Collections.emptyMap(); // brand-new journal only
        if (encoded.length() < (12 + DIGEST_BYTES) * 2
                || encoded.length() > MAX_BYTES * 2 || (encoded.length() & 1) != 0) {
            throw new IllegalArgumentException("invalid journal byte length");
        }
        byte[] data = unhex(encoded);
        int length = data.length - DIGEST_BYTES;
        if (length < 12) throw new IllegalArgumentException("truncated journal");
        byte[] body = new byte[length];
        System.arraycopy(data, 0, body, 0, length);
        byte[] expected = sha256(body);
        byte[] actual = new byte[DIGEST_BYTES];
        System.arraycopy(data, length, actual, 0, DIGEST_BYTES);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("journal digest mismatch");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                throw new IllegalArgumentException("unknown journal format");
            }
            int count = in.readInt();
            if (count < 0 || count > MAX_RECORDS) {
                throw new IllegalArgumentException("invalid record count");
            }
            Map<String, SchedulerRecoveryTransaction.Record> records = new HashMap<>();
            for (int i = 0; i < count; i++) {
                String pkg = in.readUTF();
                int phase = in.readUnsignedByte();
                if (phase >= SchedulerRecoveryTransaction.Phase.values().length) {
                    throw new IllegalArgumentException("invalid recovery phase");
                }
                long sequence = in.readLong();
                int mask = in.readInt();
                int bucket = in.readInt();
                boolean whitelist = in.readBoolean();
                boolean suspended = in.readBoolean();
                boolean enabled = in.readBoolean();
                int ownerCount = in.readUnsignedByte();
                if (ownerCount > SchedulerRecoveryTransaction.MAX_OWNERS) {
                    throw new IllegalArgumentException("owner list too long");
                }
                java.util.Set<Long> owners = new java.util.HashSet<>();
                for (int j = 0; j < ownerCount; j++) {
                    if (!owners.add(in.readLong())) {
                        throw new IllegalArgumentException("duplicate schedule owner");
                    }
                }
                SchedulerRecoveryTransaction.Record record = SchedulerRecoveryTransaction.rehydrate(
                        SchedulerRecoveryTransaction.FORMAT_VERSION, pkg,
                        new SchedulerRecoveryTransaction.OriginalRestrictions(
                                mask, bucket, whitelist, suspended, enabled),
                        owners, SchedulerRecoveryTransaction.Phase.values()[phase], sequence);
                if (records.put(pkg, record) != null) {
                    throw new IllegalArgumentException("duplicate package record");
                }
            }
            if (in.available() != 0) throw new IllegalArgumentException("trailing journal bytes");
            return Collections.unmodifiableMap(records);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("truncated/invalid journal", invalid);
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
        char[] characters = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            characters[2 * i] = HEX[(bytes[i] >>> 4) & 15];
            characters[2 * i + 1] = HEX[bytes[i] & 15];
        }
        return new String(characters);
    }

    private static byte[] unhex(String encoded) {
        byte[] bytes = new byte[encoded.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(encoded.charAt(2 * i), 16);
            int low = Character.digit(encoded.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("invalid journal hex");
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
