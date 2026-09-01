package com.gree1d.reappzuku.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Authentication for dynamically generated shortcut intents.
 *
 * Exported launcher activities cannot trust the Intent action alone because any app can forge it.
 * Dynamic/pinned shortcuts can carry an install-specific secret that is never present in static XML.
 */
public final class ShortcutAuth {
    public static final String ACTION_RAM_KILL_SECURE =
            "com.gree1d.reappzuku.action.RAM_KILL_SECURE";

    private static final String PREFS = "shortcut_auth";
    private static final String KEY_TOKEN = "token_v1";
    private static final String EXTRA_TOKEN =
            "com.gree1d.reappzuku.extra.SHORTCUT_TOKEN";

    private ShortcutAuth() {}

    public static void authorize(Context context, Intent intent) {
        intent.putExtra(EXTRA_TOKEN, getOrCreateToken(context));
    }

    public static boolean isAuthorized(Context context, Intent intent) {
        if (intent == null) return false;
        String supplied = intent.getStringExtra(EXTRA_TOKEN);
        if (supplied == null) return false;
        String expected = getOrCreateToken(context);
        return constantTimeEquals(expected, supplied);
    }

    static boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static synchronized String getOrCreateToken(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_TOKEN, null);
        if (token != null && !token.isEmpty()) return token;

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        token = android.util.Base64.encodeToString(
                bytes, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
        if (!prefs.edit().putString(KEY_TOKEN, token).commit()) {
            throw new IllegalStateException("Failed to persist shortcut authentication token");
        }
        return token;
    }
}
