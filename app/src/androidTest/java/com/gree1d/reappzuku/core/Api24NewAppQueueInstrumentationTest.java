package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * API-24-compatible, non-privileged persistence checks. Never clears app preferences
 * or the Room database; each test restores only the keys it touches.
 */
public class Api24NewAppQueueInstrumentationTest {
    private static final String FIRST = "com.reappzuku.api24queueprobe.first";
    private static final String SECOND = "com.reappzuku.api24queueprobe.second";

    @Test
    public void pendingQueueIsDurableDeduplicatedAndScoped() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        Set<String> original = new HashSet<>(prefs.getStringSet(
                PreferenceKeys.KEY_NEW_APP_SETUP_QUEUE, java.util.Collections.emptySet()));
        try {
            NewAppSetupStore.removePending(context, FIRST);
            NewAppSetupStore.removePending(context, SECOND);
            NewAppSetupStore.addPending(context, FIRST);
            NewAppSetupStore.addPending(context, FIRST);
            NewAppSetupStore.addPending(context, SECOND);
            NewAppSetupStore.addPending(context, "invalid package name");

            assertTrue(NewAppSetupStore.isPending(context, FIRST));
            assertTrue(NewAppSetupStore.isPending(context, SECOND));
            assertFalse(NewAppSetupStore.isPending(context, "invalid package name"));
            List<String> pending = NewAppSetupStore.getPending(context);
            assertEquals(1, java.util.Collections.frequency(pending, FIRST));
            assertEquals(1, java.util.Collections.frequency(pending, SECOND));
            assertEquals(original.size() + (original.contains(FIRST) ? 0 : 1)
                    + (original.contains(SECOND) ? 0 : 1), pending.size());

            // Read through a fresh SharedPreferences access, not an in-memory queue copy.
            assertTrue(context.getSharedPreferences(PreferenceKeys.PREFERENCES_NAME,
                    Context.MODE_PRIVATE).getStringSet(
                    PreferenceKeys.KEY_NEW_APP_SETUP_QUEUE,
                    java.util.Collections.emptySet()).contains(FIRST));

            NewAppSetupStore.removePending(context, FIRST);
            assertFalse(NewAppSetupStore.isPending(context, FIRST));
            assertTrue(NewAppSetupStore.isPending(context, SECOND));
        } finally {
            prefs.edit().putStringSet(PreferenceKeys.KEY_NEW_APP_SETUP_QUEUE, original).commit();
        }
    }

    @Test
    public void missingSetupModeDefaultsToAskWithoutChangingPreferences() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        String key = PreferenceKeys.KEY_NEW_APP_SETUP_MODE;
        boolean hadKey = prefs.contains(key);
        int oldValue = prefs.getInt(key, NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL);
        try {
            assertTrue(prefs.edit().remove(key).commit());
            assertEquals(NewAppSetupPolicy.MODE_ASK_AFTER_INSTALL,
                    NewAppSetupStore.getMode(context));
            assertFalse(prefs.contains(key));
        } finally {
            SharedPreferences.Editor editor = prefs.edit();
            if (hadKey) editor.putInt(key, oldValue);
            else editor.remove(key);
            editor.commit();
        }
    }
}
