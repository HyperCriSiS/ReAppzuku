package com.gree1d.reappzuku.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShortcutAuthTest {
    @Test
    public void constantTimeEquals_acceptsOnlyExactToken() {
        assertTrue(ShortcutAuth.constantTimeEquals("abc123", "abc123"));
        assertFalse(ShortcutAuth.constantTimeEquals("abc123", "abc124"));
        assertFalse(ShortcutAuth.constantTimeEquals("abc123", "abc1234"));
        assertFalse(ShortcutAuth.constantTimeEquals(null, "abc123"));
        assertFalse(ShortcutAuth.constantTimeEquals("abc123", null));
    }
}
