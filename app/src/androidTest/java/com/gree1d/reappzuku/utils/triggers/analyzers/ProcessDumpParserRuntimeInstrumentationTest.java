package com.gree1d.reappzuku.utils.triggers.analyzers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
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
    private ExecutorService executor;
    private ShellManager shellManager;

    @Before
    public void setUp() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("Guarded runtime probe only",
                "true".equals(arguments.getString("processDumpRuntimeProbe")));
        Assume.assumeTrue("API 36 runtime evidence only", Build.VERSION.SDK_INT == 36);
        targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
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
    public void realApi36ProcessDumpMatchesParser() throws Exception {
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
}
