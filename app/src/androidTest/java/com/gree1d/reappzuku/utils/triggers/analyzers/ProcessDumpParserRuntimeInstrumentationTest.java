package com.gree1d.reappzuku.utils.triggers.analyzers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.core.ShellBackendState;
import com.gree1d.reappzuku.core.ShellManager;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProcessDumpParserRuntimeInstrumentationTest {
    private static final String TAG = "ReAppzukuProcessDump";
    private Context targetContext;

    @Before
    public void setUp() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Guarded runtime probe only",
                "true".equals(arguments.getString("processDumpRuntimeProbe")));
        Assume.assumeTrue("API 36 runtime evidence only", Build.VERSION.SDK_INT == 36);
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void realApi36ProcessAndServiceDumpsMatchParser() throws Exception {
        App app = (App) targetContext.getApplicationContext();
        ShellManager shellManager = app.getShellManager();
        assertNotNull("Application ShellManager was not initialized", shellManager);

        ShellBackendState state = shellManager.getBackendState();
        long shellDeadline = System.currentTimeMillis() + 20_000L;
        while (state != ShellBackendState.SHIZUKU_READY
                && state != ShellBackendState.ROOT_READY
                && System.currentTimeMillis() < shellDeadline) {
            state = shellManager.awaitAnyShellReadyBlocking();
            if (state.isReady()) {
                break;
            }
            Thread.sleep(150L);
        }
        assertTrue("No real privileged shell backend became ready: " + state, state.isReady());

        String packageName = targetContext.getPackageName();
        String processDump = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys activity processes");
        assertNotNull("dumpsys activity processes returned null", processDump);
        assertTrue("dumpsys activity processes returned empty output",
                !processDump.trim().isEmpty());

        ProcessDumpParser.ProcessStateSnapshot processState =
                ProcessDumpParser.parseProcessState(processDump, packageName);
        assertNotNull("Current API 36 process record was not parsed for " + packageName,
                processState);
        assertTrue("Parsed process record contained neither adj nor proc state",
                processState.adj != Integer.MAX_VALUE || processState.procState != null);
        Log.i(TAG, "API36_PROCESS_RECORD_PARSED");

        String unfilteredServices = shellManager.runShellCommandAndGetFullOutput(
                "dumpsys activity services");
        assertNotNull("unfiltered dumpsys activity services returned null", unfilteredServices);
        assertTrue("unfiltered dumpsys activity services returned empty output",
                !unfilteredServices.trim().isEmpty());

        String observedServicePackage = firstServiceRecordPackage(unfilteredServices);
        assertNotNull("API 36 exposed no parseable active ServiceRecord", observedServicePackage);

        String filteredServiceDump = shellManager.runShellCommandAndGetFullOutput(
                ProcessAnalyzer.buildServicesDumpCommand(observedServicePackage));
        assertNotNull("package-filtered dumpsys activity services returned null", filteredServiceDump);
        assertTrue("package-filtered dumpsys activity services returned empty output for "
                        + observedServicePackage,
                !filteredServiceDump.trim().isEmpty());
        assertTrue("No exact API 36 ServiceRecord survived package filtering for "
                        + observedServicePackage,
                containsServiceRecordForPackage(filteredServiceDump, observedServicePackage));
        Log.i(TAG, "API36_PACKAGE_FILTERED_SERVICE_RECORD_PARSED package="
                + observedServicePackage);
    }

    private static String firstServiceRecordPackage(String dump) {
        for (String line : dump.split("\\r?\\n")) {
            String packageName = ProcessDumpParser.extractServiceRecordPackage(line);
            if (packageName != null) {
                return packageName;
            }
        }
        return null;
    }

    private static boolean containsServiceRecordForPackage(String dump, String packageName) {
        for (String line : dump.split("\\r?\\n")) {
            if (ProcessDumpParser.isServiceRecordForPackage(line, packageName)) {
                return true;
            }
        }
        return false;
    }
}
