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
        SharedPreferences.Editor editor = debugPrefs.edit().clear().putBoolean("debug_enabled", true);

        // Keep the probe independent from the concrete category list: AppDebugManager
        // stores category switches as debug_cat_<ENUM_NAME>. Enable every enum declared
        // by the manager so future category additions cannot silently blind this probe.
        for (Class<?> nestedClass : AppDebugManager.class.getDeclaredClasses()) {
            if (!nestedClass.isEnum()) {
                continue;
            }
            Object[] constants = nestedClass.getEnumConstants();
            if (constants == null) {
                continue;
            }
            for (Object constant : constants) {
                if (constant instanceof Enum<?>) {
                    editor.putBoolean("debug_cat_" + ((Enum<?>) constant).name(), true);
                }
            }
        }

        // Explicitly retain the two categories used by the current first-run path.
        editor.putBoolean("debug_cat_CORE", true);
        editor.putBoolean("debug_cat_MAIN_PAGE", true);
        assertTrue("first-run probe debug preferences must be committed synchronously", editor.commit());
    }
}
