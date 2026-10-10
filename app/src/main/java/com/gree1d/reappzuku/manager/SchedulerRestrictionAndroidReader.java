package com.gree1d.reappzuku.manager;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.gree1d.reappzuku.core.PackageNameValidator;
import com.gree1d.reappzuku.core.ShellManager;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opt-in read-only source for observing a possible original restriction state.
 * NOT invoked by RestrictionsScheduler, not written to the current journal and
 * incapable of privileged mutation. If a platform reports an ambiguous/missing
 * value this source returns null, so the observation fails closed.
 */
public final class SchedulerRestrictionAndroidReader
        implements SchedulerRestrictionObservation.Source {
    private static final int MAX_SHELL_OUTPUT = 8192;
    private static final Pattern WHITELIST_ROW =
            Pattern.compile("^(system|user),([A-Za-z0-9_.]+),([0-9]+)$");
    private final PackageManager packageManager;
    private final ShellManager shell;

    public SchedulerRestrictionAndroidReader(Context context, ShellManager shell) {
        if (context == null || shell == null) {
            throw new IllegalArgumentException("missing observation dependencies");
        }
        this.packageManager = context.getApplicationContext().getPackageManager();
        this.shell = shell;
    }

    /**
     * Only build a diagnostic snapshot when the privileged read backend is
     * already genuinely ready. This does not bind/start Shizuku or root.
     */
    public SchedulerRestrictionObservation.Result observe(String packageName) {
        if (!shell.isAnyShellReady()) {
            return SchedulerRestrictionObservation.notReady();
        }
        List<String> supported = new ArrayList<>();
        for (int i = 0; i < BackgroundAppManager.ALL_OPS.length; i++) {
            if (BackgroundAppManager.isOpSupported(i)) {
                supported.add(BackgroundAppManager.ALL_OPS[i]);
            }
        }
        return SchedulerRestrictionObservation.capture(
                packageName, supported.toArray(new String[0]), this);
    }

    @Override public Integer uid(String packageName) {
        if (!PackageNameValidator.isValid(packageName)) return null;
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return info.uid > 0 ? info.uid : null;
        } catch (PackageManager.NameNotFoundException | SecurityException failure) {
            return null;
        }
    }

    @Override public Integer enabledSetting(String packageName) {
        if (!PackageNameValidator.isValid(packageName)) return null;
        try {
            return packageManager.getApplicationEnabledSetting(packageName);
        } catch (IllegalArgumentException | SecurityException failure) {
            return null;
        }
    }

    @Override public Boolean suspended(String packageName) {
        // PackageManager.isPackageSuspended is API 29+. Android 7–9 must
        // remain unknown instead of guessing false or relying on hidden APIs.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        if (!PackageNameValidator.isValid(packageName)) return null;
        try {
            // minSdk 24 provides isPackageSuspended; it can throw if invisible.
            return packageManager.isPackageSuspended(packageName);
        } catch (IllegalArgumentException | PackageManager.NameNotFoundException
                 | SecurityException failure) {
            return null;
        }
    }

    @Override public Integer standbyBucket(String packageName) {
        String stdout = read("am get-standby-bucket " + PackageNameValidator.requireValid(packageName));
        if (stdout == null || !stdout.matches("[0-9]{1,2}")) return null;
        try {
            return Integer.parseInt(stdout);
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    @Override public SchedulerRestrictionObservation.WhitelistKind whitelistKind(
            String packageName) {
        String stdout = read("cmd deviceidle whitelist");
        return parseWhitelist(stdout, PackageNameValidator.requireValid(packageName));
    }

    @Override public String appOpMode(String packageName, String appOpName) {
        if (appOpName == null || !appOpName.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return null;
        }
        String stdout = read("cmd appops get --user current "
                + PackageNameValidator.requireValid(packageName) + " " + appOpName);
        return parseAppOp(stdout, appOpName);
    }

    private String read(String command) {
        ShellManager.ShellResult result = shell.runShellCommandForResult(command);
        if (result == null || !result.succeeded()) return null;
        String out = result.output();
        if (out == null || out.length() == 0 || out.length() > MAX_SHELL_OUTPUT) return null;
        return out;
    }

    /**
     * Only explicit modes count. A success exit status with "No operations."
     * cannot distinguish implicit default from unavailable package/UID data.
     */
    static String parseAppOp(String output, String appOpName) {
        if (output == null || output.length() > MAX_SHELL_OUTPUT
                || appOpName == null || !appOpName.matches("[A-Z][A-Z0-9_]{0,63}")) {
            return null;
        }
        String found = null;
        String prefix = appOpName + ":";
        for (String line : output.split("\\r?\\n")) {
            String item = line.trim();
            if (!item.startsWith(prefix)) continue;
            String mode = item.substring(prefix.length()).trim();
            int details = mode.indexOf(';');
            if (details >= 0) mode = mode.substring(0, details).trim();
            if (!mode.matches("[a-z]+") || found != null) return null;
            found = mode;
        }
        return found;
    }

    static SchedulerRestrictionObservation.WhitelistKind parseWhitelist(
            String output, String packageName) {
        if (!PackageNameValidator.isValid(packageName)
                || output == null || output.length() == 0 || output.length() > MAX_SHELL_OUTPUT) {
            return null;
        }
        boolean user = false;
        boolean system = false;
        boolean sawRow = false;
        for (String line : output.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("system,") && !trimmed.startsWith("user,")) continue;
            Matcher match = WHITELIST_ROW.matcher(trimmed);
            if (!match.matches()) return null;
            sawRow = true;
            if (packageName.equals(match.group(2))) {
                if (match.group(1).equals("user")) user = true;
                else system = true;
            }
        }
        // Empty output, headers, or malformed rows are never proof of absence.
        if (!sawRow) return null;
        if (user && system) return SchedulerRestrictionObservation.WhitelistKind.BOTH;
        if (user) return SchedulerRestrictionObservation.WhitelistKind.USER;
        if (system) return SchedulerRestrictionObservation.WhitelistKind.SYSTEM;
        return SchedulerRestrictionObservation.WhitelistKind.NONE;
    }
}