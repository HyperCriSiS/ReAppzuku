package com.gree1d.reappzuku.core;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.gree1d.reappzuku.R;
import com.gree1d.reappzuku.ui.AppPolicyEditorActivity;

public final class NewAppSetupNotifier {
    private static final String CHANNEL_ID = "new_app_setup";

    private NewAppSetupNotifier() {}

    public static void notifyNeedsSetup(Context context, String packageName) {
        if (!PackageNameValidator.isValid(packageName)) return;

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.new_app_setup_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, AppPolicyEditorActivity.class);
        intent.putExtra(AppPolicyEditorActivity.EXTRA_PACKAGE_NAME, packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                context, notificationId(packageName), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_force_stop)
                .setContentTitle(context.getString(R.string.new_app_setup_notification_title))
                .setContentText(context.getString(
                        R.string.new_app_setup_notification_text, appLabel(context, packageName)))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        manager.notify(notificationId(packageName), builder.build());
    }

    public static void cancel(Context context, String packageName) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && packageName != null) {
            manager.cancel(notificationId(packageName));
        }
    }

    private static int notificationId(String packageName) {
        return 0x4E410000 ^ packageName.hashCode();
    }

    private static String appLabel(Context context, String packageName) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
            CharSequence label = context.getPackageManager().getApplicationLabel(info);
            if (label != null && label.length() > 0) return label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return packageName;
    }
}