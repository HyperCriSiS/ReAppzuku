package com.gree1d.reappzuku.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ShortcutEntryPolicyTest {
    @Test
    public void secureActionRequiresInstallSpecificAuthorization() {
        assertEquals(
                ShortcutEntryPolicy.Decision.DIRECT_RAM_KILL,
                ShortcutEntryPolicy.decide(ShortcutAuth.ACTION_RAM_KILL_SECURE, true));
        assertEquals(
                ShortcutEntryPolicy.Decision.REJECT,
                ShortcutEntryPolicy.decide(ShortcutAuth.ACTION_RAM_KILL_SECURE, false));
    }

    @Test
    public void legacyRamKillAlwaysRequiresUserConfirmation() {
        assertEquals(
                ShortcutEntryPolicy.Decision.CONFIRM_RAM_KILL,
                ShortcutEntryPolicy.decide("WIDGET_KILL", false));
        assertEquals(
                ShortcutEntryPolicy.Decision.CONFIRM_RAM_KILL,
                ShortcutEntryPolicy.decide("WIDGET_KILL", true));
    }

    @Test
    public void unknownAndNullActionsCanOnlyReachConfirmedForegroundKill() {
        assertEquals(
                ShortcutEntryPolicy.Decision.CONFIRM_FOREGROUND_KILL,
                ShortcutEntryPolicy.decide("com.example.UNTRUSTED", false));
        assertEquals(
                ShortcutEntryPolicy.Decision.CONFIRM_FOREGROUND_KILL,
                ShortcutEntryPolicy.decide(null, false));
        assertEquals(
                ShortcutEntryPolicy.Decision.CONFIRM_FOREGROUND_KILL,
                ShortcutEntryPolicy.decide(IntentActionFixtures.ACTION_MAIN, false));
    }

    private static final class IntentActionFixtures {
        private static final String ACTION_MAIN = "android.intent.action.MAIN";
    }
}
