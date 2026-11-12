package com.gree1d.reappzuku.manager;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.WorkerThread;

import com.gree1d.reappzuku.core.PreferenceKeys;
import com.gree1d.reappzuku.core.PrivilegedShell;
import com.gree1d.reappzuku.core.ShellManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Undoes what earlier versions did when they lifted a background restriction
 * or applied a new level: they set every operation they manage to "allow"
 * instead of back to its default. On Android 14 and later that exempted the
 * app from power restrictions and from suspension and allowed it notification
 * access and user-initiated jobs, none of which it had before.
 *
 * An app the user installed cannot hold the power exemption on its own, so
 * that operation marks the affected apps. Runs once.
 */
public final class WronglyAllowedOpsRepair {
    static final String KEY_DONE = "wrongly_allowed_ops_repaired";

    static final String MARKER_OP = "SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS";
    /** Set back to default on a marked app. Usage access and exact alarms are left: the user may have granted them. */
    static final String[] OPS = {
        MARKER_OP,
        "SYSTEM_EXEMPT_FROM_SUSPENSION",
        "ACCESS_NOTIFICATIONS",
        "RUN_USER_INITIATED_JOBS"
    };

    private WronglyAllowedOpsRepair() {
    }

    /**
     * Whether a device or profile owner manages the device. An owner can
     * exempt apps on purpose, so nothing is touched then.
     */
    private static boolean managed(Context context) {
        DevicePolicyManager policy =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (policy == null) return true;
        List<ComponentName> admins = policy.getActiveAdmins();
        if (admins == null) return false;
        for (ComponentName admin : admins) {
            if (policy.isDeviceOwnerApp(admin.getPackageName())
                    || policy.isProfileOwnerApp(admin.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /** The package names in the output of "cmd appops query-op", one per line. */
    static List<String> packages(String queryOutput) {
        List<String> result = new ArrayList<>();
        if (queryOutput == null) return result;
        for (String line : queryOutput.split("\n")) {
            String name = line.trim();
            if (name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) result.add(name);
        }
        return result;
    }

    @WorkerThread
    public static void runOnce(Context context, ShellManager shellManager) {
        SharedPreferences prefs = context.getSharedPreferences(
                PreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false)) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The marking operation does not exist there, and the exemptions neither.
            prefs.edit().putBoolean(KEY_DONE, true).apply();
            return;
        }
        if (shellManager == null || !shellManager.isAnyShellReady()) return;
        if (managed(context)) {
            prefs.edit().putBoolean(KEY_DONE, true).apply();
            return;
        }
        String marked = shellManager.runShellCommandAndGetFullOutput(
                "cmd appops query-op --user current " + MARKER_OP + " allow");
        // No answer at all: asked again next time. A plain empty list is an answer.
        if (marked == null || marked.startsWith("ERROR:")) return;

        PackageManager packageManager = context.getPackageManager();
        PrivilegedShell shell = new PrivilegedShell(shellManager);
        boolean complete = true;
        for (String packageName : packages(marked)) {
            try {
                ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
                // The system gives this exemption to some of its own apps.
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            for (String op : OPS) {
                if (!shell.setAppOp(packageName, op, PrivilegedShell.AppOpMode.DEFAULT).succeeded()) {
                    complete = false;
                }
            }
            com.gree1d.reappzuku.utils.BackgroundRestrictionLog.log(context, packageName, "repair",
                    "wrongly-allowed-ops-reset", "exemptions set by an earlier version removed");
        }
        if (complete) prefs.edit().putBoolean(KEY_DONE, true).apply();
    }
}
