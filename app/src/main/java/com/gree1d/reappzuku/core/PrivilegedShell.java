package com.gree1d.reappzuku.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Typed boundary for high-impact shell operations that act on packages or process IDs.
 *
 * Read-only diagnostics remain on {@link ShellManager}; mutating package operations should pass
 * through this facade so untrusted text cannot become shell syntax by string concatenation.
 */
public final class PrivilegedShell {
    private static final Pattern SAFE_APP_OP = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern SAFE_PID = Pattern.compile("^[1-9][0-9]{0,9}$");

    public enum KillMode {
        KILL("am kill "),
        FORCE_STOP("am force-stop ");

        private final String prefix;

        KillMode(String prefix) {
            this.prefix = prefix;
        }

        public static KillMode fromAutoKillType(int autoKillType) {
            return autoKillType == 1 ? KILL : FORCE_STOP;
        }
    }

    public enum AppOpMode {
        ALLOW("allow"),
        IGNORE("ignore"),
        DEFAULT("default");

        private final String shellValue;

        AppOpMode(String shellValue) {
            this.shellValue = shellValue;
        }

        public static AppOpMode fromShellValue(String value) {
            for (AppOpMode mode : values()) {
                if (mode.shellValue.equals(value)) return mode;
            }
            throw new IllegalArgumentException("Unsupported app-op mode");
        }
    }

    public enum StandbyBucket {
        ACTIVE("active"),
        RARE("40"),
        RESTRICTED("45");

        private final String shellValue;

        StandbyBucket(String shellValue) {
            this.shellValue = shellValue;
        }

        public static StandbyBucket fromLegacyValue(int bucket) {
            if (bucket == 40) return RARE;
            if (bucket == 45) return RESTRICTED;
            throw new IllegalArgumentException("Unsupported standby bucket");
        }
    }

    public enum DeviceIdleWhitelistAction {
        ADD("+"),
        REMOVE("-");

        private final String prefix;

        DeviceIdleWhitelistAction(String prefix) {
            this.prefix = prefix;
        }
    }

    public enum PackageStateAction {
        SUSPEND("pm suspend --user 0 "),
        UNSUSPEND("pm unsuspend --user 0 "),
        DISABLE_USER("pm disable-user --user 0 "),
        ENABLE("pm enable ");

        private final String prefix;

        PackageStateAction(String prefix) {
            this.prefix = prefix;
        }
    }

    public enum ComponentAction {
        START_ACTIVITY("am start -n "),
        START_FOREGROUND_SERVICE("am start-foreground-service -n "),
        BROADCAST("am broadcast -n ");

        private final String prefix;

        ComponentAction(String prefix) {
            this.prefix = prefix;
        }
    }

    private final ShellManager shellManager;

    public PrivilegedShell(ShellManager shellManager) {
        this.shellManager = Objects.requireNonNull(shellManager, "shellManager");
    }

    public void killPackage(String packageName, KillMode mode, Runnable onSuccess, Runnable onFailure) {
        shellManager.runShellCommand(buildKillCommand(packageName, mode), onSuccess, onFailure);
    }

    public void killPackages(Collection<String> packageNames, KillMode mode,
            Runnable onSuccess, Runnable onFailure) {
        shellManager.runShellCommand(buildKillBatchCommand(packageNames, mode), onSuccess, onFailure);
    }

    public String killPackageAndGetFullOutput(String packageName, KillMode mode) {
        return shellManager.runShellCommandAndGetFullOutput(buildKillCommand(packageName, mode));
    }

    public String killPackagesAndGetFullOutput(Collection<String> packageNames, KillMode mode) {
        return shellManager.runShellCommandAndGetFullOutput(buildKillBatchCommand(packageNames, mode));
    }

    public ShellManager.ShellResult stopPackage(String packageName, KillMode mode) {
        return shellManager.runShellCommandForResult(buildKillCommand(packageName, mode));
    }

    public ShellManager.ShellResult forceStopPackage(String packageName) {
        return stopPackage(packageName, KillMode.FORCE_STOP);
    }

    public boolean forceStopPackageBlocking(String packageName) {
        return shellManager.runShellCommandBlocking(buildKillCommand(packageName, KillMode.FORCE_STOP));
    }

    public boolean applyPackageStateBlocking(String packageName, PackageStateAction action) {
        return shellManager.runShellCommandBlocking(buildPackageStateCommand(packageName, action));
    }

    public void uninstallPackage(String packageName, Runnable onSuccess, Runnable onFailure) {
        shellManager.runShellCommand(buildUninstallCommand(packageName), onSuccess, onFailure);
    }

    public ShellManager.ShellResult setAppOp(String packageName, String op, AppOpMode mode) {
        return shellManager.runShellCommandForResult(buildAppOpCommand(packageName, op, mode));
    }

    public ShellManager.ShellResult setStandbyBucket(String packageName, StandbyBucket bucket) {
        return shellManager.runShellCommandForResult(buildStandbyBucketCommand(packageName, bucket));
    }

    public ShellManager.ShellResult updateDeviceIdleWhitelist(
            String packageName, DeviceIdleWhitelistAction action) {
        return shellManager.runShellCommandForResult(
                buildDeviceIdleWhitelistCommand(packageName, action));
    }

    public boolean setStandbyBucketBlocking(String packageName, StandbyBucket bucket) {
        return shellManager.runShellCommandBlocking(buildStandbyBucketCommand(packageName, bucket));
    }

    public ShellManager.ShellResult launchComponent(String componentName, ComponentAction action) {
        return shellManager.runShellCommandForResult(buildComponentCommand(componentName, action));
    }

    public String killPidsAndGetFullOutput(Collection<String> pids) {
        return shellManager.runShellCommandAndGetFullOutput(buildKillPidsCommand(pids));
    }

    static String buildKillCommand(String packageName, KillMode mode) {
        String safePackage = PackageNameValidator.requireValid(packageName);
        return Objects.requireNonNull(mode, "mode").prefix + safePackage;
    }

    static String buildKillBatchCommand(Collection<String> packageNames, KillMode mode) {
        if (packageNames == null || packageNames.isEmpty()) {
            throw new IllegalArgumentException("At least one package is required");
        }
        List<String> commands = new ArrayList<>();
        for (String packageName : packageNames) {
            commands.add(buildKillCommand(packageName, mode));
        }
        return String.join("; ", commands);
    }

    static String buildUninstallCommand(String packageName) {
        return "pm uninstall " + PackageNameValidator.requireValid(packageName);
    }

    static String buildPackageStateCommand(String packageName, PackageStateAction action) {
        String safePackage = PackageNameValidator.requireValid(packageName);
        return Objects.requireNonNull(action, "action").prefix + safePackage;
    }

    static String buildAppOpCommand(String packageName, String op, AppOpMode mode) {
        String safePackage = PackageNameValidator.requireValid(packageName);
        String safeOp = requireSafeAppOp(op);
        AppOpMode safeMode = Objects.requireNonNull(mode, "mode");
        return "cmd appops set --user current " + safePackage + " " + safeOp + " "
                + safeMode.shellValue;
    }

    static String buildStandbyBucketCommand(String packageName, StandbyBucket bucket) {
        String safePackage = PackageNameValidator.requireValid(packageName);
        StandbyBucket safeBucket = Objects.requireNonNull(bucket, "bucket");
        return "am set-standby-bucket " + safePackage + " " + safeBucket.shellValue;
    }

    static String buildDeviceIdleWhitelistCommand(
            String packageName, DeviceIdleWhitelistAction action) {
        String safePackage = PackageNameValidator.requireValid(packageName);
        DeviceIdleWhitelistAction safeAction = Objects.requireNonNull(action, "action");
        return "cmd deviceidle whitelist " + safeAction.prefix + safePackage;
    }

    static String buildComponentCommand(String componentName, ComponentAction action) {
        String safeComponent = requireSafeComponent(componentName);
        return Objects.requireNonNull(action, "action").prefix + safeComponent;
    }

    static String buildKillPidsCommand(Collection<String> pids) {
        if (pids == null || pids.isEmpty()) {
            throw new IllegalArgumentException("At least one PID is required");
        }
        List<String> safePids = new ArrayList<>();
        for (String pid : pids) {
            if (pid == null || !SAFE_PID.matcher(pid).matches()) {
                throw new IllegalArgumentException("Invalid PID");
            }
            try {
                long parsed = Long.parseLong(pid);
                if (parsed <= 1 || parsed > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("Invalid PID");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid PID", e);
            }
            safePids.add(pid);
        }
        return "kill -9 " + String.join(" ", safePids);
    }

    private static String requireSafeComponent(String componentName) {
        if (componentName == null || !componentName.equals(componentName.trim())) {
            throw new IllegalArgumentException("Invalid component name");
        }
        int slash = componentName.indexOf('/');
        if (slash <= 0 || slash != componentName.lastIndexOf('/')
                || slash == componentName.length() - 1) {
            throw new IllegalArgumentException("Invalid component name");
        }
        String packageName = componentName.substring(0, slash);
        String className = componentName.substring(slash + 1);
        PackageNameValidator.requireValid(packageName);
        if (!className.matches("\\.?[A-Za-z0-9_.$]+")) {
            throw new IllegalArgumentException("Invalid component name");
        }
        return componentName;
    }

    private static String requireSafeAppOp(String op) {
        if (op == null || !SAFE_APP_OP.matcher(op).matches()) {
            throw new IllegalArgumentException("Invalid app-op name");
        }
        return op;
    }
}
