package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class BackupFileStoreTest {

    @Test
    public void boundedReaderPreservesPayloadExactly() throws Exception {
        String payload = "{\"unicode\":\"Grüße 世界\",\"lines\":\"a\\nb\"}";
        assertEquals(payload, BackupFileStore.readBounded(new StringReader(payload)));
    }

    @Test
    public void boundedReaderAcceptsExactCharacterLimit() throws Exception {
        Reader reader = new RepeatingReader(BackupCodec.MAX_BACKUP_CHARS);
        assertEquals(BackupCodec.MAX_BACKUP_CHARS, BackupFileStore.readBounded(reader).length());
    }

    @Test
    public void boundedReaderRejectsLimitPlusOneBeforeMaterializingRemainder() {
        Reader reader = new RepeatingReader(BackupCodec.MAX_BACKUP_CHARS + 1);
        assertThrows(BackupFileStore.BackupTooLargeException.class,
                () -> BackupFileStore.readBounded(reader));
    }

    @Test
    public void boundedReaderPropagatesProviderIoFailure() {
        Reader reader = new Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("provider failed");
            }

            @Override
            public void close() {}
        };
        IOException error = assertThrows(IOException.class,
                () -> BackupFileStore.readBounded(reader));
        assertEquals("provider failed", error.getMessage());
    }

    private static final class RepeatingReader extends Reader {
        private int remaining;

        private RepeatingReader(int remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read(char[] cbuf, int off, int len) {
            if (remaining == 0) return -1;
            int count = Math.min(remaining, len);
            java.util.Arrays.fill(cbuf, off, off + count, 'x');
            remaining -= count;
            return count;
        }

        @Override
        public void close() {}
    }
}
