package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.db.AppDatabase;

import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Explicitly gated CI-only provider-stream failure tests; no product storage
 * is cleared, and no partial read result is passed to a restore transaction.
 */
public final class Api24Api37BackupV7StreamingFaultInstrumentationTest {
    private static final String AUTHORITY = FaultyBackupStreamProvider.AUTHORITY;

    @Test
    public void providerDeliversBytesThenSignalsIOExceptionWithoutImport() throws Exception {
        requireCi();
        Context context = target();
        assertNotNull(InstrumentationRegistry.getInstrumentation().getContext()
                .getPackageManager().resolveContentProvider(AUTHORITY, 0));
        Snapshot before = snapshotState(context);
        Uri uri = uri("fail-after-bytes", oppositeExitOnBack(context));

        int received = 0;
        byte[] buffer = new byte[4096];
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            assertNotNull(input);
            try {
                int amount;
                while ((amount = input.read(buffer)) != -1) {
                    received += amount;
                }
                fail("Reliable pipe must propagate provider failure after its bytes");
            } catch (IOException expected) {
                assertTrue("The provider must transmit bytes before failing",
                        received > 8192);
            }
        }

        BackupFileStore store = new BackupFileStore(context.getContentResolver());
        try {
            store.read(uri);
            fail("BackupFileStore must not return a partial or apparently valid document");
        } catch (IOException expected) {
            // The caller cannot invoke restoreBackupJson without a complete String.
        }
        assertEquals(before.preferences, snapshotPreferences(context));
        assertEquals(before.policies, policyCount(context));
        assertEquals(before.presets, presetCount(context));
    }

    @Test
    public void truncatedCleanEofIsRejectedWithoutChangingExistingState() throws Exception {
        requireCi();
        Context context = target();
        Snapshot before = snapshotState(context);
        String received = new BackupFileStore(context.getContentResolver())
                .read(uri("truncated-eof", oppositeExitOnBack(context)));
        assertTrue("Fixture must actually contain partial bytes", received.length() > 8192);
        assertFalse(new BackupManager(context).restoreBackupJson(received));
        assertEquals(before.preferences, snapshotPreferences(context));
        assertEquals(before.policies, policyCount(context));
        assertEquals(before.presets, presetCount(context));
    }

    @Test
    public void completeSyntheticProviderStreamStillDecodesWithoutImport()
            throws Exception {
        requireCi();
        Context context = target();
        Snapshot before = snapshotState(context);
        String complete = new BackupFileStore(context.getContentResolver())
                .read(uri("complete", oppositeExitOnBack(context)));
        assertTrue(complete.length() > 8192);
        assertEquals(BackupCodec.CURRENT_VERSION, new BackupCodec().decode(complete).version);
        assertEquals(before.preferences, snapshotPreferences(context));
        assertEquals(before.policies, policyCount(context));
        assertEquals(before.presets, presetCount(context));
    }

    private static boolean oppositeExitOnBack(Context c) {
        Object old = c.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getAll().get(PreferenceKeys.KEY_EXIT_ON_BACK);
        return !(old instanceof Boolean && (Boolean) old);
    }

    private static Uri uri(String mode, boolean exitOnBack) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(mode).appendQueryParameter("exit_on_back",
                        Boolean.toString(exitOnBack)).build();
    }

    private static Context target() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static int policyCount(Context c) {
        return AppDatabase.getInstance(c).appPolicyDao().getAll().size();
    }

    private static int presetCount(Context c) {
        return AppDatabase.getInstance(c).policyPresetDao().getAll().size();
    }

    private static Snapshot snapshotState(Context c) {
        return new Snapshot(snapshotPreferences(c), policyCount(c), presetCount(c));
    }

    private static Map<String, Object> snapshotPreferences(Context c) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<String, ?> entry : c.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE).getAll().entrySet()) {
            // A concurrent WorkManager inventory pass can update this nonportable cache.
            // Compare every other preference, including absent keys and backup settings.
            if (PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES.equals(entry.getKey())) continue;
            Object value = entry.getValue();
            copy.put(entry.getKey(),
                    value instanceof Set ? new HashSet<>((Set<?>) value) : value);
        }
        return copy;
    }

    private static void requireCi() {
        Assume.assumeTrue("Only dedicated disposable emulator CI may open faulty streams",
                "verify".equals(InstrumentationRegistry.getArguments()
                        .getString("ci_backup_v7_stream")));
    }

    private static final class Snapshot {
        final Map<String, Object> preferences;
        final int policies;
        final int presets;
        Snapshot(Map<String, Object> preferences, int policies, int presets) {
            this.preferences = preferences;
            this.policies = policies;
            this.presets = presets;
        }
    }
}
