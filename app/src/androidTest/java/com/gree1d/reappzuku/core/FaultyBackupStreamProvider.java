package com.gree1d.reappzuku.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Disposable androidTest-APK-only provider. Serves only synthetic JSON through
 * read-only paths; never opens a real user file or any app-private configuration.
 */
public final class FaultyBackupStreamProvider extends ContentProvider {
    public static final String AUTHORITY = "com.gree1d.reappzuku.test.streamfault";

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values,
            String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        final String path = uri.getPath();
        if (!"r".equals(mode) || path == null
                || (!"/fail-after-bytes".equals(path)
                    && !"/truncated-eof".equals(path)
                    && !"/complete".equals(path))) {
            throw new FileNotFoundException("Only synthetic read-only test streams are available");
        }
        String flag = uri.getQueryParameter("exit_on_back");
        if (!"true".equals(flag) && !"false".equals(flag)) {
            throw new FileNotFoundException("Missing fixed boolean fixture selector");
        }
        final byte[] content;
        try {
            byte[] complete = syntheticBackup(Boolean.parseBoolean(flag));
            if ("/truncated-eof".equals(path)) {
                // Lose the closing brace after transmitting a large JSON body.
                content = new byte[complete.length - 1];
                System.arraycopy(complete, 0, content, 0, content.length);
            } else {
                content = complete;
            }
        } catch (Exception e) {
            throw new FileNotFoundException("Cannot produce test-only v7 JSON: " + e);
        }

        final boolean injectIoError = "/fail-after-bytes".equals(path);
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createReliablePipe();
        } catch (IOException e) {
            throw new FileNotFoundException("Cannot open test reliable pipe: " + e);
        }

        Thread writer = new Thread(() -> {
            ParcelFileDescriptor outputFd = pipe[1];
            try {
                FileOutputStream stream = new FileOutputStream(outputFd.getFileDescriptor());
                stream.write(content);
                stream.flush();
                if (injectIoError) {
                    outputFd.closeWithError("reappzuku injected failure after provider bytes");
                } else {
                    outputFd.close();
                }
            } catch (IOException failure) {
                try {
                    outputFd.closeWithError("reappzuku writer failed: " + failure);
                } catch (IOException ignored) {
                    // The reader will see the other endpoint close.
                }
            }
        }, "reappzuku-test-stream-provider");
        writer.setDaemon(true);
        writer.start();
        return pipe[0];
    }

    private static byte[] syntheticBackup(boolean exitOnBack) throws Exception {
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < 12000; i++) padding.append('p');
        JSONObject phase9 = new JSONObject()
                .put("app_policies", new JSONArray())
                .put("policy_presets", new JSONArray())
                .put("new_app_setup", new JSONObject()
                        .put("mode", NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL)
                        .put("default_preset_id", PolicyPresetSeeder.PRESET_BALANCED)
                        .put("pending", new JSONArray()));
        return new JSONObject()
                .put("backup_version", BackupCodec.CURRENT_VERSION)
                .put(PreferenceKeys.KEY_EXIT_ON_BACK, exitOnBack)
                .put("phase9", phase9)
                .put("test_padding", padding.toString())
                .toString()
                .getBytes(StandardCharsets.UTF_8);
    }
}
