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
    public void boundTestServiceProducesParseableRealServiceRecord() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context testContext = instrumentation.getContext();
        String testPackage = testContext.getPackageName();
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

        Intent intent = new Intent(testContext, ServiceRecordProbeService.class);
        boolean bound = testContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        assertTrue("Test service bind was rejected", bound);
        try {
            assertTrue("Test service did not connect", connected.await(10, TimeUnit.SECONDS));
            ComponentName component = connectedComponent.get();
            assertNotNull("Connected test service component missing", component);

            String dump = executeShellCommand(instrumentation, "dumpsys activity services");
            assertTrue("ActivityManager services dump was empty", !dump.trim().isEmpty());

            String expectedShortName = component.getClassName();
            if (expectedShortName.startsWith(testPackage + ".")) {
                expectedShortName = expectedShortName.substring(testPackage.length() + 1);
            }

            boolean parsed = false;
            for (String line : dump.split("\\r?\\n")) {
                if (!ProcessDumpParser.isServiceRecordForPackage(line, testPackage)) {
                    continue;
                }
                if (expectedShortName.equals(
                        ProcessDumpParser.extractServiceShortName(line, testPackage))) {
                    parsed = true;
                    break;
                }
            }
            assertTrue("Real ServiceRecord was not parsed for " + component.flattenToShortString(), parsed);
            Log.i(TAG, "API_SERVICE_RECORD_PARSED " + component.flattenToShortString());
        } finally {
            testContext.unbindService(connection);
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
