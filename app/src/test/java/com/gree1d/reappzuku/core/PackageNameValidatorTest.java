package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PackageNameValidatorTest {

    @Test
    public void acceptsNormalAndroidPackageNames() {
        assertTrue(PackageNameValidator.isValid("com.example.app"));
        assertTrue(PackageNameValidator.isValid("org.fdroid.fdroid"));
        assertTrue(PackageNameValidator.isValid("com.example.app_1"));
        assertTrue(PackageNameValidator.isValid("com.123.app"));
    }

    @Test
    public void rejectsShellSyntaxWhitespaceAndMalformedNames() {
        assertFalse(PackageNameValidator.isValid(null));
        assertFalse(PackageNameValidator.isValid(""));
        assertFalse(PackageNameValidator.isValid("com.example;reboot"));
        assertFalse(PackageNameValidator.isValid("com.example app"));
        assertFalse(PackageNameValidator.isValid("com.example\nreboot"));
        assertFalse(PackageNameValidator.isValid("../foo"));
        assertFalse(PackageNameValidator.isValid("com.example$(id)"));
        assertFalse(PackageNameValidator.isValid("com..example"));
        assertFalse(PackageNameValidator.isValid("com.example/evil"));
        assertFalse(PackageNameValidator.isValid("com.example "));
        assertFalse(PackageNameValidator.isValid("singleSegment"));
    }

    @Test
    public void requireValidFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> PackageNameValidator.requireValid("com.example;reboot"));
    }
}
