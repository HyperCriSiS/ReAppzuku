package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.db.AppDatabase;

import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Disposable-emulator-only negative SAF boundaries. No real user documents,
 * Uri grants or persisted app state are modified by these tests.
 *
 * Does NOT assert successful user selection or persisted-grant acquisition
 * through real DocumentsUI. Those require separate interactive acceptance.
 */
public final class Api24Api37SafUriBoundaryInstrumentationTest {
    private static final String PRIVATE_AUTHORITY =
            "com.gree1d.reappzuku.test.privatebackup";

    @Test
    public void rejectedUnselectedContentUriNeverImportsOrExportsBackup() throws Exception {
        requireCi();
        Context context = stableContext();
        ContentResolver resolver = context.getContentResolver();
        BackupFileStore store = new BackupFileStore(resolver);
        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(PRIVATE_AUTHORITY).appendPath("not-selected.json").build();

        assertNotNull("Test APK private provider must exist", InstrumentationRegistry
                .getInstrumentation().getContext().getPackageManager()
                .resolveContentProvider(PRIVATE_AUTHORITY, 0));
        StateSnapshot before = snapshot(context);

        try {
            store.read(uri);
            fail("A foreign, unexported URI must not be readable without a grant");
        } catch (SecurityException expected) {
            // Denied by Android before any backup JSON can be passed to restore.
        }

        try {
            store.write(uri, "{\"backup_version\":7}");
            fail("A foreign, unexported URI must not be writable without a grant");
        } catch (SecurityException expected) {
            // Never writes a backup to a provider without permission.
        }

        assertStateUnchanged(before, context);
    }

    @Test
    public void unsupportedSchemesAreRejectedBeforeProviderOrFileOpen() throws Exception {
        requireCi();
        Context context = stableContext();
        BackupFileStore store = new BackupFileStore(context.getContentResolver());
        StateSnapshot before = snapshot(context);
        for (Uri uri : new Uri[] {
                Uri.parse("file:///data/local/tmp/do-not-read.json"),
                Uri.parse("https://example.invalid/backup.json"),
                Uri.parse("content:///missing-authority")
        }) {
            try {
                store.read(uri);
                fail("Unsupported URI scheme/authority was accepted: " + uri.getScheme());
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("content URI"));
            }
            try {
                store.write(uri, "{}");
                fail("Unsupported backup export URI accepted");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("content URI"));
            }
        }
        assertStateUnchanged(before, context);
    }

    @Test
    public void ungrantedUriCannotAcquireOrReleasePersistablePermissions()
            throws Exception {
        requireCi();
        Context context = stableContext();
        ContentResolver resolver = context.getContentResolver();
        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(PRIVATE_AUTHORITY).appendPath("not-granted.json").build();
        List<String> before = persistedGrantSnapshot(resolver);

        try {
            resolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fail("Unselected URI must not get persisted read access");
        } catch (SecurityException expected) {
            // Android refuses an unoffered persisted grant.
        }

        try {
            resolver.releasePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            fail("No persisted grant exists to release for the foreign URI");
        } catch (SecurityException expected) {
            // The caller is not entitled to revoke a nonexistent grant.
        }
        assertEquals("Failed grant operations must not change persisted grants",
                before, persistedGrantSnapshot(resolver));
    }

    @Test
    public void normalOneShotBackupReadDoesNotCreatePersistedPermissions()
            throws Exception {
        requireCi();
        Context context = stableContext();
        ContentResolver resolver = context.getContentResolver();
        List<String> before = persistedGrantSnapshot(resolver);
        StateSnapshot state = snapshot(context);

        Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(FaultyBackupStreamProvider.AUTHORITY)
                .appendPath("complete")
                .appendQueryParameter("exit_on_back", "true")
                .build();
        String backup = new BackupFileStore(resolver).read(uri);
        assertTrue("Synthetic provider must produce full backup data", backup.length() > 8192);
        assertEquals(BackupCodec.CURRENT_VERSION, new BackupCodec().decode(backup).version);

        assertEquals("Reading a content URI must not retain an unrequested SAF grant",
                before, persistedGrantSnapshot(resolver));
        assertStateUnchanged(state, context);
    }

    private static Context stableContext() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        App app = (App) context.getApplicationContext();
        assertNotNull(app.getSharedExecutor());
        app.getSharedExecutor().submit(() -> {}).get(60, TimeUnit.SECONDS);
        return context;
    }

    private static void requireCi() {
        Assume.assumeTrue("Only the dedicated disposable-emulator lane may run SAF probes",
                "verify".equals(InstrumentationRegistry.getArguments()
                        .getString("ci_saf_uri_boundary")));
    }

    private static List<String> persistedGrantSnapshot(ContentResolver resolver) {
        List<String> grants = new ArrayList<>();
        for (UriPermission grant : resolver.getPersistedUriPermissions()) {
            grants.add(grant.getUri() + ":read=" + grant.isReadPermission()
                    + ":write=" + grant.isWritePermission());
        }
        Collections.sort(grants);
        return grants;
    }

    private static StateSnapshot snapshot(Context context) {
        Map<String, Object> prefs = new HashMap<>();
        SharedPreferences shared = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> item : shared.getAll().entrySet()) {
            // Concurrent periodic package inventory is not portable backup data.
            if (PreferenceKeys.KEY_NEW_APP_KNOWN_PACKAGES.equals(item.getKey())) continue;
            Object value = item.getValue();
            prefs.put(item.getKey(),
                    value instanceof Set ? new HashSet<>((Set<?>) value) : value);
        }
        AppDatabase db = AppDatabase.getInstance(context);
        return new StateSnapshot(prefs, db.appPolicyDao().getAll().size(),
                db.policyPresetDao().getAll().size());
    }

    private static void assertStateUnchanged(StateSnapshot expected, Context context) {
        StateSnapshot actual = snapshot(context);
        assertEquals("Rejected or one-shot URI use changed app preferences",
                expected.preferences, actual.preferences);
        assertEquals("No Room app policies may be imported", expected.policies, actual.policies);
        assertEquals("No Room presets may be imported", expected.presets, actual.presets);
    }

    private static final class StateSnapshot {
        final Map<String, Object> preferences;
        final int policies;
        final int presets;
        StateSnapshot(Map<String, Object> preferences, int policies, int presets) {
            this.preferences = preferences;
            this.policies = policies;
            this.presets = presets;
        }
    }
}
