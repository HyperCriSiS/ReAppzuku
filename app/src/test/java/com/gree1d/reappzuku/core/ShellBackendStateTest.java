package com.gree1d.reappzuku.core;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShellBackendStateTest {
    @Test public void rootWins() {
        assertEquals(ShellBackendState.ROOT_READY,
                ShellBackendState.resolve(true, false, false, false, false, false, false));
    }

    @Test public void unavailableBeforeBinderWasEverSeen() {
        assertEquals(ShellBackendState.SHIZUKU_UNAVAILABLE,
                ShellBackendState.resolve(false, false, false, false, false, false, false));
    }

    @Test public void binderLossIsDistinctFromNeverAvailable() {
        assertEquals(ShellBackendState.SHIZUKU_LOST,
                ShellBackendState.resolve(false, false, true, false, false, false, false));
    }

    @Test public void permissionStatesAreExplicit() {
        assertEquals(ShellBackendState.SHIZUKU_PERMISSION_REQUIRED,
                ShellBackendState.resolve(false, true, true, false, false, false, false));
        assertEquals(ShellBackendState.SHIZUKU_PERMISSION_PENDING,
                ShellBackendState.resolve(false, true, true, false, true, false, false));
    }

    @Test public void permissionDoesNotMeanReady() {
        assertEquals(ShellBackendState.SHIZUKU_GRANTED,
                ShellBackendState.resolve(false, true, true, true, false, false, false));
        assertEquals(ShellBackendState.SHIZUKU_BINDING,
                ShellBackendState.resolve(false, true, true, true, false, true, false));
        assertEquals(ShellBackendState.SHIZUKU_READY,
                ShellBackendState.resolve(false, true, true, true, false, false, true));
    }

    @Test public void onlyActualBackendsAreReady() {
        assertTrue(ShellBackendState.ROOT_READY.isReady());
        assertTrue(ShellBackendState.SHIZUKU_READY.isReady());
        assertFalse(ShellBackendState.SHIZUKU_GRANTED.isReady());
        assertFalse(ShellBackendState.SHIZUKU_BINDING.isReady());
        assertTrue(ShellBackendState.SHIZUKU_BINDING.isWaiting());
    }
}
