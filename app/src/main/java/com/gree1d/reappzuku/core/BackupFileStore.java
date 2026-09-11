package com.gree1d.reappzuku.core;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Small boundary around user-selected backup URIs.
 *
 * <p>The decoder already rejects oversized payloads, but an untrusted content URI must also be
 * bounded while it is being read so a provider cannot force the app to materialize an arbitrarily
 * large String before validation.</p>
 */
public final class BackupFileStore {
    private static final int BUFFER_CHARS = 8192;

    private final ContentResolver resolver;

    public BackupFileStore(ContentResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    public void write(Uri uri, String payload) throws IOException {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(payload, "payload");
        requireWithinLimit(payload.length());

        try (OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new FileNotFoundException("Backup output stream is unavailable");
            }
            output.write(payload.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    public String read(Uri uri) throws IOException {
        Objects.requireNonNull(uri, "uri");
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) {
                throw new FileNotFoundException("Backup input stream is unavailable");
            }
            return readBounded(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
    }

    static String readBounded(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[BUFFER_CHARS];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            requireWithinLimit(result.length() + read);
            result.append(buffer, 0, read);
        }
        return result.toString();
    }

    private static void requireWithinLimit(int characters) throws BackupTooLargeException {
        if (characters > BackupCodec.MAX_BACKUP_CHARS) {
            throw new BackupTooLargeException(
                    "Backup exceeds the " + BackupCodec.MAX_BACKUP_CHARS + " character limit");
        }
    }

    public static final class BackupTooLargeException extends IOException {
        public BackupTooLargeException(String message) {
            super(message);
        }
    }
}
