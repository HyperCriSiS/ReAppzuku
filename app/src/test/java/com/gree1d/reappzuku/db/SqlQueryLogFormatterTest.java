package com.gree1d.reappzuku.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SqlQueryLogFormatterTest {
    @Test
    public void bindValuesAreRedactedButCountIsRetained() {
        String privatePackage = "com.example.private.bank";
        String privateValue = "sensitive-state-token";

        String log = SqlQueryLogFormatter.format(
                "SELECT * FROM app_stats WHERE packageName = ? AND lastKillSource = ?",
                Arrays.asList(privatePackage, privateValue));

        assertTrue(log.contains("bindArgCount=2"));
        assertTrue(log.contains("values=<redacted>"));
        assertFalse(log.contains(privatePackage));
        assertFalse(log.contains(privateValue));
    }

    @Test
    public void emptyAndNullBindListsRemainNonSensitive() {
        String empty = SqlQueryLogFormatter.format("SELECT 1", Collections.emptyList());
        String absent = SqlQueryLogFormatter.format("SELECT 1", null);

        assertTrue(empty.contains("bindArgCount=0"));
        assertTrue(absent.contains("bindArgCount=0"));
        assertTrue(empty.endsWith("values=<redacted>"));
        assertTrue(absent.endsWith("values=<redacted>"));
    }
}
