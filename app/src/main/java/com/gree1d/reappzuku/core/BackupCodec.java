package com.gree1d.reappzuku.core;

import org.json.JSONException;
import org.json.JSONObject;

/** Bounded/versioned JSON envelope for ReAppzuku configuration backups. */
public final class BackupCodec {
    static final String KEY_BACKUP_VERSION = "backup_version";
    public static final int CURRENT_VERSION = 5;
    public static final int MAX_BACKUP_CHARS = 2 * 1024 * 1024;

    public enum DecodeFailure {
        PAYLOAD_SIZE,
        MALFORMED,
        FUTURE_VERSION
    }

    public static final class DecodeException extends Exception {
        public final DecodeFailure reason;
        public final int detectedVersion;

        private DecodeException(DecodeFailure reason, String message, int detectedVersion, Throwable cause) {
            super(message, cause);
            this.reason = reason;
            this.detectedVersion = detectedVersion;
        }
    }

    public static final class DecodedBackup {
        public final JSONObject root;
        public final int version;
        public final boolean legacy;

        private DecodedBackup(JSONObject root, int version) {
            this.root = root;
            this.version = version;
            this.legacy = version < 1;
        }
    }

    public JSONObject newRoot() throws JSONException {
        JSONObject root = new JSONObject();
        root.put(KEY_BACKUP_VERSION, CURRENT_VERSION);
        return root;
    }

    public String encode(JSONObject root) throws JSONException {
        if (root == null) throw new IllegalArgumentException("root == null");
        return root.toString(4);
    }

    public DecodedBackup decode(String json) throws DecodeException {
        if (json == null || json.isEmpty() || json.length() > MAX_BACKUP_CHARS) {
            throw new DecodeException(
                    DecodeFailure.PAYLOAD_SIZE,
                    "empty or oversized backup payload",
                    -1,
                    null);
        }

        final JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            throw new DecodeException(DecodeFailure.MALFORMED, "malformed backup JSON", -1, e);
        }

        int version = root.optInt(KEY_BACKUP_VERSION, -1);
        if (version > CURRENT_VERSION) {
            throw new DecodeException(
                    DecodeFailure.FUTURE_VERSION,
                    "unsupported future backup version: " + version,
                    version,
                    null);
        }
        return new DecodedBackup(root, version);
    }
}
