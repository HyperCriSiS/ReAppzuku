package com.gree1d.reappzuku.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PackageAddedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
                || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            return;
        }

        Uri data = intent.getData();
        String packageName = data != null ? data.getSchemeSpecificPart() : null;
        if (!PackageNameValidator.isValid(packageName)) return;

        PendingResult pendingResult = goAsync();
        App app = (App) context.getApplicationContext();
        ExecutorService shared = app.getSharedExecutor();
        if (shared != null) {
            shared.execute(() -> {
                try {
                    NewAppSetupCoordinator.handlePackageAdded(context, packageName);
                } finally {
                    pendingResult.finish();
                }
            });
            return;
        }

        ExecutorService fallback = Executors.newSingleThreadExecutor();
        fallback.execute(() -> {
            try {
                NewAppSetupCoordinator.handlePackageAdded(context, packageName);
            } finally {
                pendingResult.finish();
                fallback.shutdown();
            }
        });
    }
}
