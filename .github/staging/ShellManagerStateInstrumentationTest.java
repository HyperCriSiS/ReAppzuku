package com.gree1d.reappzuku.core;

import static org.junit.Assert.assertEquals;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.gree1d.reappzuku.core.shell.IShellCallback;
import com.gree1d.reappzuku.core.shell.IShellService;
import com.gree1d.reappzuku.core.shell.ProcessMemoryInfo;
import com.gree1d.reappzuku.core.shell.ShellExecResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import rikka.shizuku.Shizuku;

public class ShellManagerStateInstrumentationTest {
    private Context context;
    private ExecutorService executor;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        executor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() {
        executor.shutdownNow();
    }

    @Test
    public void binderLateDenyThenGrantReachesReady() {
        FakeShizukuBridge bridge = new FakeShizukuBridge(context);
        ShellManager manager = new ShellManager(
                context, new Handler(Looper.getMainLooper()), executor, bridge);

        assertEquals(ShellBackendState.SHIZUKU_UNAVAILABLE, manager.getBackendState());

        bridge.setBinderAvailable(true);
        bridge.fireBinderReceived();
        assertEquals(ShellBackendState.SHIZUKU_PERMISSION_REQUIRED, manager.getBackendState());

        manager.checkShellPermissions();
        assertEquals(1, bridge.permissionRequestCount);
        assertEquals(ShellBackendState.SHIZUKU_PERMISSION_PENDING, manager.getBackendState());

        bridge.firePermissionResult(PackageManager.PERMISSION_DENIED);
        assertEquals(ShellBackendState.SHIZUKU_PERMISSION_REQUIRED, manager.getBackendState());

        manager.checkShellPermissions();
        assertEquals(2, bridge.permissionRequestCount);
        bridge.firePermissionResult(PackageManager.PERMISSION_GRANTED);
        assertEquals(ShellBackendState.SHIZUKU_BINDING, manager.getBackendState());

        bridge.connectUserService();
        assertEquals(ShellBackendState.SHIZUKU_READY, manager.getBackendState());
    }

    @Test
    public void permissionGrantStillBindsAfterActivityListenerIsRemoved() {
        FakeShizukuBridge bridge = new FakeShizukuBridge(context);
        bridge.setBinderAvailable(true);
        ShellManager manager = new ShellManager(
                context, new Handler(Looper.getMainLooper()), executor, bridge);
        AtomicInteger activityCallbacks = new AtomicInteger();
        Shizuku.OnRequestPermissionResultListener activityListener =
                (requestCode, grantResult) -> activityCallbacks.incrementAndGet();
        manager.setShizukuPermissionListener(activityListener);

        manager.checkShellPermissions();
        manager.removeShizukuPermissionListener(activityListener);
        bridge.firePermissionResult(PackageManager.PERMISSION_GRANTED);

        assertEquals(0, activityCallbacks.get());
        assertEquals(ShellBackendState.SHIZUKU_BINDING, manager.getBackendState());
        bridge.connectUserService();
        assertEquals(ShellBackendState.SHIZUKU_READY, manager.getBackendState());
    }

    @Test
    public void binderDeathThenReturnRebindsUserService() {
        FakeShizukuBridge bridge = new FakeShizukuBridge(context);
        bridge.setBinderAvailable(true);
        bridge.permission = PackageManager.PERMISSION_GRANTED;
        ShellManager manager = new ShellManager(
                context, new Handler(Looper.getMainLooper()), executor, bridge);
        assertEquals(ShellBackendState.SHIZUKU_BINDING, manager.getBackendState());
        bridge.connectUserService();
        assertEquals(ShellBackendState.SHIZUKU_READY, manager.getBackendState());
        assertEquals(1, bridge.bindCount);

        bridge.setBinderAvailable(false);
        bridge.fireBinderDead();
        assertEquals(ShellBackendState.SHIZUKU_LOST, manager.getBackendState());

        bridge.setBinderAvailable(true);
        bridge.fireBinderReceived();
        assertEquals(ShellBackendState.SHIZUKU_BINDING, manager.getBackendState());
        assertEquals(2, bridge.bindCount);
        bridge.connectUserService();
        assertEquals(ShellBackendState.SHIZUKU_READY, manager.getBackendState());
    }

    private static final class FakeShizukuBridge implements ShizukuBridge {
        private final Context context;
        private final List<Shizuku.OnRequestPermissionResultListener> permissionListeners = new ArrayList<>();
        private final List<Shizuku.OnBinderReceivedListener> receivedListeners = new ArrayList<>();
        private final List<Shizuku.OnBinderDeadListener> deadListeners = new ArrayList<>();
        private boolean binderAvailable;
        private int permission = PackageManager.PERMISSION_DENIED;
        private int permissionRequestCount;
        private int bindCount;
        private ServiceConnection pendingConnection;

        FakeShizukuBridge(Context context) {
            this.context = context;
        }

        void setBinderAvailable(boolean available) {
            binderAvailable = available;
        }

        void fireBinderReceived() {
            for (Shizuku.OnBinderReceivedListener listener : new ArrayList<>(receivedListeners)) {
                listener.onBinderReceived();
            }
        }

        void fireBinderDead() {
            pendingConnection = null;
            for (Shizuku.OnBinderDeadListener listener : new ArrayList<>(deadListeners)) {
                listener.onBinderDead();
            }
        }

        void firePermissionResult(int result) {
            permission = result;
            for (Shizuku.OnRequestPermissionResultListener listener : new ArrayList<>(permissionListeners)) {
                listener.onRequestPermissionResult(0, result);
            }
        }

        void connectUserService() {
            if (pendingConnection == null) {
                throw new AssertionError("no pending UserService bind");
            }
            IShellService.Stub service = new IShellService.Stub() {
                @Override
                public ShellExecResult execute(String command) {
                    return new ShellExecResult(true, 0, "");
                }

                @Override
                public void executeWithCallback(String command, IShellCallback callback) {
                }

                @Override
                public ProcessMemoryInfo[] getProcessMemoryInfo(int[] pids) {
                    return new ProcessMemoryInfo[0];
                }

                @Override
                public void destroy() {
                }
            };
            ServiceConnection connection = pendingConnection;
            pendingConnection = null;
            connection.onServiceConnected(
                    new ComponentName(context.getPackageName(), "FakeShellService"),
                    service.asBinder());
        }

        @Override
        public boolean pingBinder() {
            return binderAvailable;
        }

        @Override
        public int checkSelfPermission() {
            return permission;
        }

        @Override
        public void requestPermission(int requestCode) {
            permissionRequestCount++;
        }

        @Override
        public void addRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener listener) {
            permissionListeners.add(listener);
        }

        @Override
        public void removeRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener listener) {
            permissionListeners.remove(listener);
        }

        @Override
        public void addBinderReceivedListenerSticky(Shizuku.OnBinderReceivedListener listener) {
            receivedListeners.add(listener);
            if (binderAvailable) {
                listener.onBinderReceived();
            }
        }

        @Override
        public void removeBinderReceivedListener(Shizuku.OnBinderReceivedListener listener) {
            receivedListeners.remove(listener);
        }

        @Override
        public void addBinderDeadListener(Shizuku.OnBinderDeadListener listener) {
            deadListeners.add(listener);
        }

        @Override
        public void removeBinderDeadListener(Shizuku.OnBinderDeadListener listener) {
            deadListeners.remove(listener);
        }

        @Override
        public void bindUserService(Shizuku.UserServiceArgs args, ServiceConnection connection) {
            bindCount++;
            pendingConnection = connection;
        }

        @Override
        public void unbindUserService(
                Shizuku.UserServiceArgs args, ServiceConnection connection, boolean remove) {
            if (pendingConnection == connection) {
                pendingConnection = null;
            }
        }
    }
}
