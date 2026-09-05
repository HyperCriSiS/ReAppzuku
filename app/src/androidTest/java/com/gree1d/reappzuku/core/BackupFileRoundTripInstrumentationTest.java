package com.gree1d.reappzuku.core;

import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_EXIT_ON_BACK;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_PREVENT_SHIZUKU_AUTOSTART;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

/** Guarded API-36 probe for the real content:// backup file path plus semantic restore. */
public class BackupFileRoundTripInstrumentationTest {

    @Test
    public void mediaStoreContentUriRoundTripRestoresBackup() throws Exception {
        assumeTrue("backup file probe not requested",
                "true".equals(InstrumentationRegistry.getArguments().getString("backupFileProbe")));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ContentResolver resolver = context.getContentResolver();
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);

        // Keep all continuity automation off so BackgroundWorkPolicy does not intentionally
        // normalize the App Behavior values after restore.
        assertTrue(prefs.edit().clear()
                .putBoolean(KEY_EXIT_ON_BACK, true)
                .putBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, true)
                .commit());

        BackupManager backupManager = new BackupManager(context);
        String payload = backupManager.createBackupJson();
        assertNotNull(payload);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,
                "reappzuku-backup-probe-" + System.nanoTime() + ".json");
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/ReAppzukuTests");
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        assertNotNull("MediaStore did not create a content URI", uri);

        try {
            BackupFileStore store = new BackupFileStore(resolver);
            store.write(uri, payload);

            assertTrue(prefs.edit()
                    .putBoolean(KEY_EXIT_ON_BACK, false)
                    .putBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false)
                    .commit());
            assertFalse(prefs.getBoolean(KEY_EXIT_ON_BACK, false));
            assertFalse(prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false));

            String imported = store.read(uri);
            assertTrue("content URI payload must restore transactionally",
                    backupManager.restoreBackupJson(imported));
            assertTrue(prefs.getBoolean(KEY_EXIT_ON_BACK, false));
            assertTrue(prefs.getBoolean(KEY_PREVENT_SHIZUKU_AUTOSTART, false));
        } finally {
            resolver.delete(uri, null, null);
        }
    }
}
