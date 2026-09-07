package com.gree1d.reappzuku.utils.triggers.analyzers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.ShellBackendState;
import com.gree1d.reappzuku.core.ShellManager;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class ProcessDumpParserRuntimeInstrumentationTest {
    private static final String TAG = "ReAppzukuProcessDump";
    private Context targetContext;
    private Context instrumentationContext;
    private ExecutorService executor;
    private ShellManager shellManager;

    @Before
    public void setUp() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Guarded runtime probe only",
                "true".equals(arguments.getString("processDumpRuntimeProbe")));
        Assume.assumeTrue("API 36 runtime evidence only", Build.VERSION.SDK_INT == 36);
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        instrumentationContext = InstrumentationRegistry.getInstrumentation().getContext();
        executor = Executors.newSingleThreadExecutor();
        shellManager = new ShellManager(targetContext, new Handler(Looper.getMainLooper()), executor);

        ShellBackendState state = awaitReady(20_000L);
        assertTrue("No real privileged shell backend became ready: " + state,
                state == ShellBackendState.SHIZUKU_READY || state == ShellBackendState.ROOT_READY);
    }

    @After
    public void tearDown() {
        if (shellManager != null) {
            shellManager.unbindUserService();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void realApi36ProcessAndServiceDumpsMatchParser() throws Exception {
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

        Intent serviceIntent = new Intent(instrumentationContext, ProcessDumpProbeService.class);
        ComponentName startedService = instrumentationContext.startService(serviceIntent);
        assertNotNull("Test-only service could not be started", startedService);

        String servicePackage = instrumentationContext.getPackageName();
        try {
            String filteredServiceDump = null;
            boolean foundProbeService = false;
            long serviceDeadline = System.currentTimeMillis() + 10_000L;
            while (System.currentTimeMillis() < serviceDeadline) {
                filteredServiceDump = shellManager.runShellCommandAndGetFullOutput(
                        ProcessAnalyzer.buildServicesDumpCommand(servicePackage));
                if (filteredServiceDump != null
                        && containsProbeServiceRecord(filteredServiceDump, servicePackage)) {
                    foundProbeService = true;
                    break;
                }
                Thread.sleep(100L);
            }

            assertNotNull("package-filtered dumpsys activity services returned null",
                    filteredServiceDump);
            assertTrue("package-filtered dumpsys activity services returned empty output for "
                            + servicePackage,
                    !filteredServiceDump.trim().isEmpty());
            assertTrue("No exact API 36 ServiceRecord survived package filtering for test service",
                    foundProbeService);
            Log.i(TAG, "API36_PACKAGE_FILTERED_SERVICE_RECORD_PARSED package=" + servicePackage);
        } finally {
            instrumentationContext.stopService(serviceIntent);
        }
    }

    private ShellBackendState awaitReady(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ShellBackendState last = shellManager.getBackendState();
        while (System.currentTimeMillis() < deadline) {
            last = shellManager.awaitAnyShellReadyBlocking();
            if (last == ShellBackendState.SHIZUKU_READY || last == ShellBackendState.ROOT_READY) {
                return last;
            }
            Thread.sleep(150L);
        }
        return last;
    }

    private static boolean containsProbeServiceRecord(String dump, String packageName) {
        for (String line : dump.split("\\r?\\n")) {
            if (!ProcessDumpParser.isServiceRecordForPackage(line, packageName)) {
                continue;
            }
            String shortName = ProcessDumpParser.extractServiceShortName(line, packageName);
            if (shortName != null && shortName.endsWith("ProcessDumpProbeService")) {
                return true;
            }
        }
        return false;
    }
}
