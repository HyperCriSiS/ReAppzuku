package com.gree1d.reappzuku.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PackageTextMatcherTest {
    @Test
    public void exactPackageMatchesAndroidDumpDelimiters() {
        String pkg = "com.example.app";
        assertTrue(PackageTextMatcher.containsExactPackage("package=" + pkg, pkg));
        assertTrue(PackageTextMatcher.containsExactPackage(pkg + "/.MainActivity", pkg));
        assertTrue(PackageTextMatcher.containsExactPackage(pkg + ":remote", pkg));
        assertTrue(PackageTextMatcher.containsExactPackage("{" + pkg + "}", pkg));
    }

    @Test
    public void prefixNeighborPackagesDoNotMatch() {
        String pkg = "com.example.app";
        assertFalse(PackageTextMatcher.containsExactPackage("com.example.app2/.Service", pkg));
        assertFalse(PackageTextMatcher.containsExactPackage("com.example.app.extra/.Service", pkg));
        assertFalse(PackageTextMatcher.containsExactPackage("xcom.example.app/.Service", pkg));
    }

    @Test
    public void markerSearchUsesOnlyExactPackageOccurrences() {
        String pkg = "com.example.app";
        String falseNeighbor = "ServiceRecord{ com.example.app.extra/.Sync }\n"
                + "  isForeground=true\n";
        assertFalse(PackageTextMatcher.containsMarkerNearPackage(
                falseNeighbor, pkg, "isForeground=true", 200));

        String exact = "ServiceRecord{ " + pkg + "/.Sync }\n"
                + "  isForeground=true\n";
        assertTrue(PackageTextMatcher.containsMarkerNearPackage(
                exact, pkg, "isForeground=true", 200));
    }

    @Test
    public void markerSearchDoesNotBorrowMarkerFromUnrelatedPackage() {
        String pkg = "com.example.app";
        String text = pkg + "/.Sync\n"
                + "x".repeat(2200)
                + "VPN owner=com.other.vpn";
        assertFalse(PackageTextMatcher.containsMarkerNearPackage(
                text, pkg, "VPN", 1800));
    }

    @Test
    public void markerSearchHonorsRadiusAndInputContracts() {
        String pkg = "com.example.app";
        String text = pkg + " " + "x".repeat(80) + " isForeground=true";
        assertFalse(PackageTextMatcher.containsMarkerNearPackage(
                text, pkg, "isForeground=true", 20));
        assertTrue(PackageTextMatcher.containsMarkerNearPackage(
                text, pkg, "isForeground=true", 120));
        assertThrows(IllegalArgumentException.class,
                () -> PackageTextMatcher.containsMarkerNearPackage(
                        text, pkg, "isForeground=true", -1));
        assertThrows(IllegalArgumentException.class,
                () -> PackageTextMatcher.containsExactPackage(
                        text, "com.example.app;id"));
    }
}
