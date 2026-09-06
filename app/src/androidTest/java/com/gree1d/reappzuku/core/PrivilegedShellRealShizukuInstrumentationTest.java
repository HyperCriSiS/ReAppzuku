package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Explicit real-device/emulator probe for representative mutating PrivilegedShell command families.
 *
 * <p>The workflow must install a disposable package named {@link #PROBE_PACKAGE} before enabling
 * this test with {@code -e privilegedShellProbe true}. Normal instrumentation runs skip it.</p>
 */
public class PrivilegedShellRealShizukuInstrumentationTest {
    public static final String PROBE_PACKAGE = "com.gree1d.reappzuku.probetarget";

    private Context context;
    private ExecutorService executor;
    private ShellManager manager;
    private PrivilegedShell shell;

    @Before
    public void setUp() throws Exception {
        assumeTrue("privileged shell probe not requested",
                "true".equals(InstrumentationRegistry.getArguments()
                        .getString("privilegedShellProbe")));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        executor = Executors.newSingleThreadExecutor();
        manager = new ShellManager(context, new Handler(Looper.getMainLooper()), executor);

        assertTrue("real Shizuku Binder is unavailable", Shizuku.pingBinder());
        assertEquals("real Shizuku permission is not granted",
                android.content.pm.PackageManager.PERMISSION_GRANTED,
                Shizuku.checkSelfPermission());
        assertEquals("real Shizuku UserService did not become ready",
                ShellBackendState.SHIZUKU_READY,
                awaitReady(20_000L));
        assertTrue("disposable probe package is not installed",
                command("pm path " + PROBE_PACKAGE).succeeded());
        shell = new PrivilegedShell(manager);
    }

    @After
    public void tearDown() {
        if (shell != null) {
            // Best-effort rollback if an assertion aborts the probe midway.
            shell.setAppOp(PROBE_PACKAGE, "RUN_IN_BACKGROUND", PrivilegedShell.AppOpMode.DEFAULT);
            shell.setStandbyBucket(PROBE_PACKAGE, PrivilegedShell.StandbyBucket.ACTIVE);
            shell.updateDeviceIdleWhitelist(PROBE_PACKAGE,
                    PrivilegedShell.DeviceIdleWhitelistAction.REMOVE);
            shell.applyPackageStateBlocking(PROBE_PACKAGE,
                    PrivilegedShell.PackageStateAction.UNSUSPEND);
            shell.applyPackageStateBlocking(PROBE_PACKAGE,
                    PrivilegedShell.PackageStateAction.ENABLE);
        }
        if (manager != null) {
            manager.unbindUserService();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void representativeMutatingFamiliesExecuteAndRollbackThroughRealShizuku() {
        ShellManager.ShellResult appOp = shell.setAppOp(
                PROBE_PACKAGE,
                "RUN_IN_BACKGROUND",
                PrivilegedShell.AppOpMode.IGNORE);
        assertSucceeded("app-op mutation", appOp);
        assertTrue("app-op mutation was not observable",
                output("cmd appops get --user current " + PROBE_PACKAGE + " RUN_IN_BACKGROUND")
                        .toLowerCase(Locale.ROOT).contains("ignore"));
        assertSucceeded("app-op rollback", shell.setAppOp(
                PROBE_PACKAGE,
                "RUN_IN_BACKGROUND",
                PrivilegedShell.AppOpMode.DEFAULT));

        assertSucceeded("standby-bucket mutation", shell.setStandbyBucket(
                PROBE_PACKAGE,
                PrivilegedShell.StandbyBucket.RARE));
        assertTrue("standby-bucket mutation was not observable",
                output("am get-standby-bucket " + PROBE_PACKAGE).contains("40"));
        assertSucceeded("standby-bucket rollback", shell.setStandbyBucket(
                PROBE_PACKAGE,
                PrivilegedShell.StandbyBucket.ACTIVE));

        assertSucceeded("device-idle whitelist add", shell.updateDeviceIdleWhitelist(
                PROBE_PACKAGE,
                PrivilegedShell.DeviceIdleWhitelistAction.ADD));
        assertTrue("device-idle whitelist add was not observable",
                output("cmd deviceidle whitelist").contains(PROBE_PACKAGE));
        assertSucceeded("device-idle whitelist remove", shell.updateDeviceIdleWhitelist(
                PROBE_PACKAGE,
                PrivilegedShell.DeviceIdleWhitelistAction.REMOVE));
        assertFalse("device-idle whitelist rollback was not observable",
                output("cmd deviceidle whitelist").contains(PROBE_PACKAGE));

        assertTrue("package suspend command failed", shell.applyPackageStateBlocking(
                PROBE_PACKAGE,
                PrivilegedShell.PackageStateAction.SUSPEND));
        assertTrue("package suspension was not observable",
                output("dumpsys package " + PROBE_PACKAGE + " | grep -m1 'suspended='")
                        .contains("suspended=true"));
        assertTrue("package unsuspend command failed", shell.applyPackageStateBlocking(
                PROBE_PACKAGE,
                PrivilegedShell.PackageStateAction.UNSUSPEND));
        assertTrue("package unsuspend rollback was not observable",
                output("dumpsys package " + PROBE_PACKAGE + " | grep -m1 'suspended='")
                        .contains("suspended=false"));

        assertTrue("disable-user command failed", shell.applyPackageStateBlocking(
                PROBE_PACKAGE,
                PrivilegedShell.PackageStateAction.DISABLE_USER));
        assertTrue("disabled package was not observable",
                output("pm list packages -d " + PROBE_PACKAGE).contains(PROBE_PACKAGE));
        assertTrue("package enable rollback failed", shell.applyPackageStateBlocking(
                PROBE_PACKAGE,
                PrivilegedShell.PackageStateAction.ENABLE));
        assertTrue("enabled package rollback was not observable",
                output("pm list packages -e " + PROBE_PACKAGE).contains(PROBE_PACKAGE));

        assertSucceeded("force-stop family", shell.forceStopPackage(PROBE_PACKAGE));

        ShellManager.ShellResult component = shell.launchComponent(
                PROBE_PACKAGE + "/com.gree1d.reappzuku.core.BootReceiver",
                PrivilegedShell.ComponentAction.BROADCAST);
        assertSucceeded("explicit component broadcast", component);
    }

    private ShellBackendState awaitReady(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ShellBackendState last = manager.getBackendState();
        while (System.currentTimeMillis() < deadline) {
            last = manager.awaitAnyShellReadyBlocking();
            if (last == ShellBackendState.SHIZUKU_READY) {
                return last;
            }
            Thread.sleep(150L);
        }
        return last;
    }

    private ShellManager.ShellResult command(String command) {
        return manager.runShellCommandForResult(command);
    }

    private String output(String command) {
        ShellManager.ShellResult result = command(command);
        assertSucceeded(command, result);
        return result.output();
    }

    private static void assertSucceeded(String operation, ShellManager.ShellResult result) {
        assertTrue(operation + " failed with exit=" + result.exitCode()
                        + " output=" + result.output(),
                result.succeeded());
    }
}
