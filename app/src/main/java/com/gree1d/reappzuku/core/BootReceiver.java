package com.gree1d.reappzuku.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.content.ContextCompat;

import com.gree1d.reappzuku.core.AppDebugManager;
import com.gree1d.reappzuku.core.AppDebugManager.Category;

import com.gree1d.reappzuku.service.ShappkyService;
import com.gree1d.reappzuku.manager.RestrictionsScheduler;
import com.gree1d.reappzuku.manager.PresetManager;
import com.gree1d.reappzuku.service.AutoKillWorker;
import com.gree1d.reappzuku.service.SmartLifecycleWorker;
import static com.gree1d.reappzuku.core.PreferenceKeys.KEY_AUTO_KILL_ENABLED;
import static com.gree1d.reappzuku.core.PreferenceKeys.PREFERENCES_NAME;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            AppDebugManager.w(Category.CORE, "BootReceiver: onReceive: action is null, skipping");
            return;
        }

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {

            RestrictionsScheduler.scheduleNextStatic(context);
            new PresetManager(context).restoreAfterBoot();

            SharedPreferences prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
            boolean autoKillEnabled = prefs.getBoolean(KEY_AUTO_KILL_ENABLED, false);
            if (autoKillEnabled) {
                Intent serviceIntent = new Intent(context, ShappkyService.class);
                ContextCompat.startForegroundService(context, serviceIntent);
                AutoKillWorker.schedule(context, "Periodic Kill");
                AppDebugManager.d(Category.CORE, "BootReceiver: Boot complete (" + action + "): background service started, worker scheduled");
            } else {
                AutoKillWorker.cancel(context);
                AppDebugManager.d(Category.CORE, "BootReceiver: Boot complete (" + action + "): no background service requested");
            }

            SmartLifecycleWorker.scheduleAfterBoot(context);
        }
    }
}
