package com.gree1d.reappzuku.core;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class BackupCodecTest {
    private final BackupCodec codec = new BackupCodec();

    @Test
    public void roundTripsCurrentEnvelope() throws Exception {
        JSONObject root = codec.newRoot();
        root.put("marker", "ok");

        BackupCodec.DecodedBackup decoded = codec.decode(codec.encode(root));

        assertEquals(BackupCodec.CURRENT_VERSION, decoded.version);
        assertFalse(decoded.legacy);
        assertEquals("ok", decoded.root.getString("marker"));
    }

    @Test
    public void acceptsLegacyUnversionedEnvelope() throws Exception {
        BackupCodec.DecodedBackup decoded = codec.decode("{\"marker\":1}");
        assertTrue(decoded.legacy);
        assertEquals(-1, decoded.version);
    }

    @Test
    public void rejectsFutureVersion() throws Exception {
        try {
            codec.decode("{\"backup_version\":" + (BackupCodec.CURRENT_VERSION + 1) + "}");
            fail("future backup should be rejected");
        } catch (BackupCodec.DecodeException e) {
            assertEquals(BackupCodec.DecodeFailure.FUTURE_VERSION, e.reason);
            assertEquals(BackupCodec.CURRENT_VERSION + 1, e.detectedVersion);
        }
    }

    @Test
    public void rejectsMalformedJson() throws Exception {
        try {
            codec.decode("{not-json");
            fail("malformed backup should be rejected");
        } catch (BackupCodec.DecodeException e) {
            assertEquals(BackupCodec.DecodeFailure.MALFORMED, e.reason);
        }
    }

    @Test
    public void rejectsOversizedPayloadBeforeParsing() throws Exception {
        char[] chars = new char[BackupCodec.MAX_BACKUP_CHARS + 1];
        Arrays.fill(chars, 'x');
        try {
            codec.decode(new String(chars));
            fail("oversized backup should be rejected");
        } catch (BackupCodec.DecodeException e) {
            assertEquals(BackupCodec.DecodeFailure.PAYLOAD_SIZE, e.reason);
        }
    }
}
