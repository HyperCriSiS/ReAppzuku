package com.gree1d.reappzuku.core;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import com.google.android.material.color.DynamicColors;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ShellManager shellManager;

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
}