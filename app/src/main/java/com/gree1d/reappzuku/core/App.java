package com.gree1d.reappzuku.core;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import com.google.android.material.color.DynamicColors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService shellExecutor = Executors.newSingleThreadExecutor();
    private ShellManager shellManager;

    private static final int ICON_CACHE_MAX_BYTES = 24 * 1024 * 1024;
    private final LruCache<String, Bitmap> iconCache = new LruCache<String, Bitmap>(ICON_CACHE_MAX_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        AppDebugManager.init(this);
        DynamicColors.applyToActivitiesIfAvailable(this);

        shellManager = new ShellManager(this, handler, executor);
        shellManager.bindUserService();
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