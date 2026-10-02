package com.gree1d.reappzuku.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Low-latency package-install observer for the live normal process.
 *
 * PACKAGE_ADDED is not a manifest-safe implicit broadcast for modern target SDKs,
 * so App registers this receiver dynamically. NewAppDiscoveryWorker remains the
 * durable source of truth for installations missed while the process was absent.
 */
public final class PackageAddedReceiver extends BroadcastReceiver {

    public static PackageAddedReceiver register(Context context) {
        Context appContext = context.getApplicationContext();
        PackageAddedReceiver receiver = new PackageAddedReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // PACKAGE_ADDED is a protected system broadcast. RECEIVER_EXPORTED lets
            // the system sender cross our UID boundary; action/data validation remains
            // mandatory in onReceive().
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
        return receiver;
    }

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
                    NewAppDiscoveryWorker.handleObservedPackageAdded(context, packageName);
                } finally {
                    pendingResult.finish();
                }
            });
            return;
        }

        ExecutorService fallback = Executors.newSingleThreadExecutor();
        fallback.execute(() -> {
            try {
                NewAppDiscoveryWorker.handleObservedPackageAdded(context, packageName);
            } finally {
                pendingResult.finish();
                fallback.shutdown();
            }
        });
    }
}
