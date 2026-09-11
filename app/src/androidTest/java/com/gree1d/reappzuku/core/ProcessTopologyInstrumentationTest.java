package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProcessTopologyInstrumentationTest {
    private Context targetContext;
    private PackageManager packageManager;
    private ComponentName wakeReceiver;

    @Before
    public void setUp() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Guarded runtime probe only",
                "true".equals(arguments.getString("processTopologyProbe")));

        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        packageManager = targetContext.getPackageManager();
        wakeReceiver = new ComponentName(targetContext, ShizukuWakeReceiver.class);
    }

    @Test
    public void disableWakeReceiverForProviderOnlyProbe() {
        setAndAssert(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
    }

    @Test
    public void enableWakeReceiverForAutomaticWakeProbe() {
        setAndAssert(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    private void setAndAssert(int desiredState) {
        packageManager.setComponentEnabledSetting(
                wakeReceiver,
                desiredState,
                PackageManager.DONT_KILL_APP);
        assertEquals(desiredState, packageManager.getComponentEnabledSetting(wakeReceiver));
    }
}
