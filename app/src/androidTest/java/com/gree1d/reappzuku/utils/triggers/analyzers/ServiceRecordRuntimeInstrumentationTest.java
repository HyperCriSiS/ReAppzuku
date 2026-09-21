package com.gree1d.reappzuku.utils.triggers.analyzers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class ServiceRecordRuntimeInstrumentationTest {
    private static final String TAG = "ReAppzukuServiceDump";

    @Test
    public void boundDebugServiceProducesParseableRealServiceRecord() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context targetContext = instrumentation.getTargetContext();
        String targetPackage = targetContext.getPackageName();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<ComponentName> connectedComponent = new AtomicReference<>();

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                connectedComponent.set(name);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        Intent intent = new Intent(targetContext, ServiceRecordProbeService.class);
        boolean bound = targetContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        assertTrue("Debug target service bind was rejected", bound);
        try {
            assertTrue("Debug target service did not connect", connected.await(10, TimeUnit.SECONDS));
            ComponentName component = connectedComponent.get();
            assertNotNull("Connected debug service component missing", component);

            String dump = executeShellCommand(instrumentation, "dumpsys activity services");
            assertTrue("ActivityManager services dump was empty", !dump.trim().isEmpty());

            String expectedShortName = component.getClassName();
            if (expectedShortName.startsWith(targetPackage + ".")) {
                expectedShortName = expectedShortName.substring(targetPackage.length() + 1);
            }

            boolean parsed = false;
            StringBuilder candidates = new StringBuilder();
            for (String line : dump.split("\\r?\\n")) {
                if ((line.contains("ServiceRecord") || line.contains(targetPackage))
                        && candidates.length() < 6000) {
                    candidates.append(line.trim()).append('\n');
                }
                if (!ProcessDumpParser.isServiceRecordForPackage(line, targetPackage)) {
                    continue;
                }
                if (expectedShortName.equals(
                        ProcessDumpParser.extractServiceShortName(line, targetPackage))) {
                    parsed = true;
                    Log.i(TAG, "API_SERVICE_RECORD_PARSED " + line.trim());
                    break;
                }
            }
            assertTrue(
                    "Real ServiceRecord was not parsed for "
                            + component.flattenToShortString()
                            + "\nRelevant ActivityManager lines:\n"
                            + candidates,
                    parsed);
        } finally {
            targetContext.unbindService(connection);
        }
    }

    private static String executeShellCommand(Instrumentation instrumentation, String command)
            throws Exception {
        ParcelFileDescriptor descriptor =
                instrumentation.getUiAutomation().executeShellCommand(command);
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ParcelFileDescriptor.AutoCloseInputStream(descriptor),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }
}
