package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gree1d.reappzuku.db.AppPolicy;

import org.junit.Test;

public class ProtectionDumpsTest {
    private static final String PKG = "com.example.app";
    private static final String SEP = "==reappzuku-dump==\n";
    private static final String OTHER_SERVICE = "ServiceRecord{com.other/.S}\n";

    private static ProtectionDumps dumps(String media, String widget, String services) {
        return new ProtectionDumps(media + SEP + widget + SEP + services + SEP + SEP + SEP);
    }

    private static final ProtectionDumps ALL_SIGNALS = dumps(
            "session package=" + PKG + "\n",
            "provider=" + PKG + "/.Widget\n",
            OTHER_SERVICE + "ServiceRecord{" + PKG + "/.Sync}\n isForeground=true\n");

    @Test
    public void appWithoutPolicyGetsEveryProtection() {
        assertEquals("active widget", ALL_SIGNALS.dumpReasonFor(PKG, null));
        assertNull(ALL_SIGNALS.dumpReasonFor("com.unrelated", null));
    }

    @Test
    public void eachPolicySwitchMapsToItsOwnDump() {
        AppPolicy policy = new AppPolicy(PKG);
        policy.protectWidgets = false;
        assertEquals("media session", ALL_SIGNALS.dumpReasonFor(PKG, policy));
        policy.protectMedia = false;
        assertEquals("foreground service", ALL_SIGNALS.dumpReasonFor(PKG, policy));
        policy.protectForegroundServices = false;
        assertNull(ALL_SIGNALS.dumpReasonFor(PKG, policy));
    }

    @Test
    public void failedCollectionSparesEveryApp() {
        assertEquals("protection state unknown", new ProtectionDumps(null).dumpReasonFor(PKG, null));
        assertEquals("protection state unknown", new ProtectionDumps("").dumpReasonFor(PKG, null));
        assertEquals("protection state unknown",
                dumps("", "", "").dumpReasonFor(PKG, null));
    }

    @Test
    public void sparedPackagesAreRememberedForALimitedTime() {
        SparedPackages spared = new SparedPackages();
        assertFalse(spared.isSpared(PKG, 1_000L));
        spared.spare(PKG, 1_000L);
        assertTrue(spared.isSpared(PKG, 1_000L + SparedPackages.TTL_MS - 1));
        assertFalse(spared.isSpared(PKG, 1_000L + SparedPackages.TTL_MS));
        assertFalse(spared.isSpared("com.unrelated", 1_000L));
    }
}
