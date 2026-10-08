package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.gree1d.reappzuku.db.AppDatabase;
import com.gree1d.reappzuku.db.AppPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Dispatched in two separate instrumentation processes across adb install -r
 * of an increasing-version, same-signer debug APK. Skips normal suites.
 */
@RunWith(AndroidJUnit4.class)
public class EmulatorUpgradePersistenceTest {
    private static final String PROBE_PACKAGE = "com.example.reappzuku.upgrade.smoke";
    private static final String PROBE_MARKER = "api24_upgrade_persistence_marker";
    private static final String PROBE_VERSION = "api24_upgrade_previous_code";
    private static final String VALUE = "retained-across-in-place-update";

    private void requireStage(String expected) {
        assumeTrue(expected.equals(InstrumentationRegistry.getArguments()
                .getString("upgrade_smoke_stage", "")));
    }

    private Context app() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private int installedVersion(Context context) throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), 0);
        return (int) (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode);
    }

    @Test
    public void seedBeforeUpgrade() throws Exception {
        requireStage("seed");
        Context context = app();
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        int version = installedVersion(context);
        assertTrue(version > 0);
        assertTrue(prefs.edit()
                .putString(PROBE_MARKER, VALUE)
                .putInt(PROBE_VERSION, version)
                .commit());

        AppPolicy policy = new AppPolicy(PROBE_PACKAGE);
        policy.source = AppPolicy.SOURCE_EXPLICIT;
        policy.strategy = AppPolicy.STRATEGY_PROTECTED;
        policy.customized = true;
        policy.createdAt = 123L;
        policy.updatedAt = 123L;
        AppDatabase.getInstance(context).appPolicyDao().upsert(policy);
        assertNotNull(AppDatabase.getInstance(context)
                .appPolicyDao().getByPackage(PROBE_PACKAGE));
    }

    @Test
    public void verifyAfterUpgrade() throws Exception {
        requireStage("verify");
        Context context = app();
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        assertEquals(VALUE, prefs.getString(PROBE_MARKER, null));
        int previousCode = prefs.getInt(PROBE_VERSION, -1);
        assertTrue(previousCode > 0);
        assertTrue("The installed package must have a higher versionCode",
                installedVersion(context) > previousCode);
        AppPolicy restored = AppDatabase.getInstance(context)
                .appPolicyDao().getByPackage(PROBE_PACKAGE);
        assertNotNull("Explicit Room policy must survive package update", restored);
        assertEquals(AppPolicy.SOURCE_EXPLICIT, restored.source);
        assertEquals(AppPolicy.STRATEGY_PROTECTED, restored.strategy);
        assertEquals(123L, restored.createdAt);
    }
}
