package com.gree1d.reappzuku.utils.triggers.analyzers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.App;
import com.gree1d.reappzuku.core.ShellBackendState;
import com.gree1d.reappzuku.core.ShellManager;
import com.gree1d.reappzuku.service.ShappkyService;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProcessDumpParserRuntimeInstrumentationTest {
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

        Intent serviceIntent = new Intent(targetContext, ShappkyService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            targetContext.startForegroundService(serviceIntent);
        } else {
            targetContext.startService(serviceIntent);
        }

        try {
            long serviceDeadline = System.currentTimeMillis() + 10_000L;
            while (!ShappkyService.isRunning() && System.currentTimeMillis() < serviceDeadline) {
                Thread.sleep(100L);
            }
            assertTrue("ShappkyService did not become visible as running",
                    ShappkyService.isRunning());

            String serviceDump = shellManager.runShellCommandAndGetFullOutput(
                    "dumpsys activity services " + packageName);
            assertNotNull("dumpsys activity services returned null", serviceDump);
            assertTrue("dumpsys activity services returned empty output",
                    !serviceDump.trim().isEmpty());

            boolean foundPackageService = false;
            boolean foundShappkyService = false;
            for (String line : serviceDump.split("\\r?\\n")) {
                if (!ProcessDumpParser.isServiceRecordForPackage(line, packageName)) {
                    continue;
                }
                foundPackageService = true;
                String shortName = ProcessDumpParser.extractServiceShortName(line, packageName);
                if (shortName != null && shortName.endsWith("ShappkyService")) {
                    foundShappkyService = true;
                    break;
                }
            }

            assertTrue("No API 36 ServiceRecord was parsed for " + packageName,
                    foundPackageService);
            assertTrue("ShappkyService API 36 ServiceRecord was not parsed",
                    foundShappkyService);
        } finally {
            targetContext.stopService(serviceIntent);
        }
    }
}
