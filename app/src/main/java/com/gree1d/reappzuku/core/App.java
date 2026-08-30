package com.gree1d.reappzuku.core;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Process;
import android.os.Looper;
import android.util.LruCache;
import com.google.android.material.color.DynamicColors;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private boolean shizukuProviderProcess;

    private Handler handler;
    private ExecutorService executor;
    private ExecutorService shellExecutor;
    private ShellManager shellManager;
    private LruCache<String, Bitmap> iconCache;

    private static final int ICON_CACHE_MAX_BYTES = 24 * 1024 * 1024;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        shizukuProviderProcess = (getPackageName() + ":shizuku").equals(getCurrentProcessName(base));

        // The Shizuku provider lives in its own minimal process. Enable the
        // provider library's built-in binder bridge before providers are created.
        ShizukuProvider.enableMultiProcessSupport(shizukuProviderProcess);
    }

    private static String getCurrentProcessName(Context context) {
        int pid = Process.myPid();
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return null;
        }
        for (ActivityManager.RunningAppProcessInfo process : activityManager.getRunningAppProcesses()) {
            if (process.pid == pid) {
                return process.processName;
            }
        }
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Shizuku starts only this provider process while distributing its Binder.
        // Keep it intentionally minimal so the real ReAppzuku application does
        // not initialize merely because Shizuku was started.
        if (shizukuProviderProcess) {
            return;
        }

        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();
        shellExecutor = Executors.newSingleThreadExecutor();
        iconCache = new LruCache<String, Bitmap>(ICON_CACHE_MAX_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };

        AppDebugManager.init(this);
        DynamicColors.applyToActivitiesIfAvailable(this);

        // The Binder is delivered to :shizuku. Pull it into the normal process
        // only when ReAppzuku itself is actually launched.
        ShizukuProvider.requestBinderForNonProviderProcess(this);

        shellManager = new ShellManager(this, handler, executor);

        // Handles both an already-running Shizuku instance and a later
        // Shizuku start/restart while ReAppzuku is open.
        Shizuku.addBinderReceivedListenerSticky(shellManager::bindUserService);
    }

    public ShellManager getShellManager() {
        return shellManager;
    }

    public Handler getSharedHandler() {
        return handler;
    }

    public ExecutorService getSharedExecutor() {
        return executor;
    }

    public ExecutorService getShellExecutor() {
        return shellExecutor;
    }

    public LruCache<String, Bitmap> getIconCache() {
        return iconCache;
    }
}
