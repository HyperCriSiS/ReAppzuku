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
import com.gree1d.reappzuku.utils.BackgroundRestrictionLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Undoes what earlier versions did when they lifted a background restriction
 * or applied a new level: they set every operation they manage to "allow"
 * instead of back to its default. On Android 14 and later that exempted the
 * app from power restrictions and from suspension.
 *
 * An app the user installed cannot hold the power exemption on its own, so
 * that operation marks the affected apps. Runs once.
 */
public final class WronglyAllowedOpsRepair {
    static final String KEY_DONE = "wrongly_allowed_ops_repaired";

    static final String MARKER_OP = "SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS";
    /**
     * Set back to default on a marked app, each only where it is "allow". The
     * last two are allowed by default anyway; they are reset so that nothing
     * explicit is left behind. Usage access and exact alarms are not touched:
     * the user may have granted them.
     */
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

    /** One query for all operations; each answer follows a line "---OP---". */
    static String queryCommand() {
        StringBuilder command = new StringBuilder();
        for (String op : OPS) {
            command.append("echo ---").append(op).append("---; cmd appops query-op --user current ")
                    .append(op).append(" allow; ");
        }
        return command.toString();
    }

    /**
     * @return for each operation that was answered, the packages that have it
     *         at "allow". An operation without its "---OP---" line is absent.
     */
    static Map<String, List<String>> allowedByOp(String output) {
        Map<String, List<String>> result = new HashMap<>();
        if (output == null) return result;
        List<String> current = null;
        for (String line : output.split("\n")) {
            String text = line.trim();
            if (text.startsWith("---") && text.endsWith("---") && text.length() > 6) {
                current = new ArrayList<>();
                result.put(text.substring(3, text.length() - 3), current);
            } else if (current != null
                    && text.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
                current.add(text);
            }
        }
        return result;
    }

    /**
     * @return the operations to reset for a marked package: those that are
     *         "allow" for it. One that a restriction of this app holds at
     *         "ignore" is not in its list and stays as it is.
     */
    static List<String> opsToReset(Map<String, List<String>> allowedByOp, String packageName) {
        List<String> marked = allowedByOp.get(MARKER_OP);
        if (marked == null || !marked.contains(packageName)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String op : OPS) {
            List<String> allowed = allowedByOp.get(op);
            if (allowed != null && allowed.contains(packageName)) result.add(op);
        }
        return result;
    }

    // Two callers on different threads: one repair at a time.
    @WorkerThread
    public static synchronized void runOnce(Context context, ShellManager shellManager) {
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
        Map<String, List<String>> allowedByOp =
                allowedByOp(shellManager.runShellCommandAndGetFullOutput(queryCommand()));
        List<String> marked = allowedByOp.get(MARKER_OP);
        // No answer for the marker: asked again next time. An empty list is an answer.
        if (marked == null) return;

        PackageManager packageManager = context.getPackageManager();
        PrivilegedShell shell = new PrivilegedShell(shellManager);
        boolean complete = true;
        for (String packageName : marked) {
            try {
                ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
                // The system gives this exemption to some of its own apps.
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            } catch (PackageManager.NameNotFoundException e) {
                continue;
            }
            int reset = 0;
            for (String op : opsToReset(allowedByOp, packageName)) {
                if (shell.setAppOp(packageName, op, PrivilegedShell.AppOpMode.DEFAULT).succeeded()) {
                    reset++;
                } else {
                    complete = false;
                }
            }
            if (reset > 0) {
                BackgroundRestrictionLog.log(context, packageName, "repair",
                        "wrongly-allowed-ops-reset", "exemptions set by an earlier version removed");
            }
        }
        if (complete) prefs.edit().putBoolean(KEY_DONE, true).apply();
    }
}
