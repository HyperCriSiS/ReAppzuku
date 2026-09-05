package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test-only setup used by the guarded real Shizuku first-run runtime probe. */
@RunWith(AndroidJUnit4.class)
public class FirstRunShizukuProbeSetupInstrumentationTest {

    @Test
    public void enableDebugLoggingForFirstRunProbe() {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("true".equals(args.getString("firstRunProbeSetup")));

        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences debugPrefs = context.getSharedPreferences("AppDebugPrefs", Context.MODE_PRIVATE);
        boolean committed = debugPrefs.edit()
                .clear()
                .putBoolean("debug_enabled", true)
                .putBoolean("debug_cat_CORE", true)
                .putBoolean("debug_cat_MAIN_PAGE", true)
                .commit();
        assertTrue("first-run probe debug preferences must be committed synchronously", committed);
    }
}
