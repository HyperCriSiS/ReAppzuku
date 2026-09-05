from pathlib import Path

SLEEP = Path('app/src/main/java/com/gree1d/reappzuku/manager/SleepModeManager.java')
SMART = Path('app/src/main/java/com/gree1d/reappzuku/manager/SmartLifecycleManager.java')
SCHED = Path('app/src/main/java/com/gree1d/reappzuku/manager/RestrictionsScheduler.java')


def once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected 1 match, got {n}')
    return text.replace(old, new, 1)


def patch_sleep(text):
    pairs = [
        ('import com.gree1d.reappzuku.core.ShellManager;\n',
         'import com.gree1d.reappzuku.core.ShellManager;\nimport com.gree1d.reappzuku.core.PrivilegedShell;\n'),
        ('    private final ShellManager shellManager;\n',
         '    private final ShellManager shellManager;\n    private final PrivilegedShell privilegedShell;\n'),
        ('        this.shellManager = shellManager;\n        this.sharedpreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);\n',
         '        this.shellManager = shellManager;\n        this.privilegedShell = new PrivilegedShell(shellManager);\n        this.sharedpreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);\n'),
        ('''        String command = method == FreezeMethod.SUSPEND\n                ? "pm suspend --user 0 " + packageName\n                : "pm disable-user --user 0 " + packageName;\n        boolean ok = shellManager.runShellCommandBlocking(command);\n''',
         '''        PrivilegedShell.PackageStateAction action = method == FreezeMethod.SUSPEND\n                ? PrivilegedShell.PackageStateAction.SUSPEND\n                : PrivilegedShell.PackageStateAction.DISABLE_USER;\n        boolean ok = privilegedShell.applyPackageStateBlocking(packageName, action);\n'''),
        ('''        String command = method == FreezeMethod.SUSPEND\n                ? "pm unsuspend --user 0 " + packageName\n                : "pm enable " + packageName;\n        boolean ok = shellManager.runShellCommandBlocking(command);\n''',
         '''        PrivilegedShell.PackageStateAction action = method == FreezeMethod.SUSPEND\n                ? PrivilegedShell.PackageStateAction.UNSUSPEND\n                : PrivilegedShell.PackageStateAction.ENABLE;\n        boolean ok = privilegedShell.applyPackageStateBlocking(packageName, action);\n'''),
    ]
    for i, (old, new) in enumerate(pairs, 1):
        text = once(text, old, new, f'sleep {i}')
    log_old = 'method=" + method + ", command=" + command);'
    if text.count(log_old) != 2:
        raise SystemExit(f'sleep logs: expected 2 matches, got {text.count(log_old)}')
    text = text.replace(log_old, 'method=" + method + ", action=" + action);')
    return text


def patch_smart(text):
    pairs = [
        ('import com.gree1d.reappzuku.core.ProtectedApps;\n',
         'import com.gree1d.reappzuku.core.ProtectedApps;\nimport com.gree1d.reappzuku.core.PrivilegedShell;\n'),
        ('    private final ShellManager shellManager;\n',
         '    private final ShellManager shellManager;\n    private final PrivilegedShell privilegedShell;\n'),
        ('        this.shellManager = shellManager;\n        this.prefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);\n',
         '        this.shellManager = shellManager;\n        this.privilegedShell = new PrivilegedShell(shellManager);\n        this.prefs = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);\n'),
        ('        boolean ok = shellManager.runShellCommandBlocking("am set-standby-bucket " + pkg + " rare");\n',
         '        boolean ok = privilegedShell.setStandbyBucketBlocking(\n                pkg, PrivilegedShell.StandbyBucket.RARE);\n'),
        ('        boolean ok = shellManager.runShellCommandBlocking("am force-stop " + pkg);\n',
         '        boolean ok = privilegedShell.forceStopPackageBlocking(pkg);\n'),
    ]
    for i, (old, new) in enumerate(pairs, 1):
        text = once(text, old, new, f'smart {i}')
    return text


def patch_sched(text):
    pairs = [
        ('import com.gree1d.reappzuku.core.ExactAlarmCapability;\n',
         'import com.gree1d.reappzuku.core.ExactAlarmCapability;\nimport com.gree1d.reappzuku.core.PrivilegedShell;\n'),
        ('    private final ShellManager         shellManager;\n',
         '    private final ShellManager         shellManager;\n    private final PrivilegedShell       privilegedShell;\n'),
        ('        this.shellManager         = shellManager;\n        this.backgroundAppManager = backgroundAppManager;\n',
         '        this.shellManager         = shellManager;\n        this.privilegedShell       = new PrivilegedShell(shellManager);\n        this.backgroundAppManager = backgroundAppManager;\n'),
        ('''                    if (method == SleepModeManager.FreezeMethod.SUSPEND) {\n                        shellManager.runShellCommandBlocking("pm unsuspend --user 0 " + pkg);\n                    } else {\n                        shellManager.runShellCommandBlocking("pm enable " + pkg);\n                    }\n''',
         '''                    PrivilegedShell.PackageStateAction action =\n                            method == SleepModeManager.FreezeMethod.SUSPEND\n                                    ? PrivilegedShell.PackageStateAction.UNSUSPEND\n                                    : PrivilegedShell.PackageStateAction.ENABLE;\n                    privilegedShell.applyPackageStateBlocking(pkg, action);\n'''),
        ('''            String cmd = "am set-standby-bucket " + packageName + " active";\n            shellManager.runShellCommandForResult(cmd);\n''',
         '            privilegedShell.setStandbyBucket(packageName, PrivilegedShell.StandbyBucket.ACTIVE);\n'),
        ('        shellManager.runShellCommandForResult("am set-standby-bucket " + packageName + " " + bucket);\n',
         '        privilegedShell.setStandbyBucket(\n                packageName, PrivilegedShell.StandbyBucket.fromLegacyValue(bucket));\n'),
        ('''        String cmd = (forceStop ? "am force-stop " : "am kill ") + packageName;\n        shellManager.runShellCommandForResult(cmd);\n        AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: stopApp: " + cmd);\n''',
         '''        PrivilegedShell.KillMode mode = forceStop\n                ? PrivilegedShell.KillMode.FORCE_STOP\n                : PrivilegedShell.KillMode.KILL;\n        privilegedShell.stopPackage(packageName, mode);\n        AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER,\n                "RestrictionsScheduler: stopApp: package=" + packageName + " mode=" + mode);\n'''),
        ('''        String cmd;\n        switch (type) {\n            case ON_ACTIVATE_ACTIVITY:\n                cmd = "am start -n " + componentName;\n                break;\n            case ON_ACTIVATE_SERVICE:\n                cmd = "am start-foreground-service -n " + componentName;\n                break;\n            case ON_ACTIVATE_RECEIVER:\n                cmd = "am broadcast -n " + componentName;\n                break;\n            default:\n                AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: launchComponent: unknown type " + type);\n                return;\n        }\n        ShellManager.ShellResult r = shellManager.runShellCommandForResult(cmd);\n        if (r.succeeded()) {\n            AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: launchComponent: ok — " + cmd);\n        } else {\n            AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: launchComponent: failed (exit=" + r.exitCode() + ") — " + cmd);\n        }\n''',
         '''        PrivilegedShell.ComponentAction action;\n        switch (type) {\n            case ON_ACTIVATE_ACTIVITY:\n                action = PrivilegedShell.ComponentAction.START_ACTIVITY;\n                break;\n            case ON_ACTIVATE_SERVICE:\n                action = PrivilegedShell.ComponentAction.START_FOREGROUND_SERVICE;\n                break;\n            case ON_ACTIVATE_RECEIVER:\n                action = PrivilegedShell.ComponentAction.BROADCAST;\n                break;\n            default:\n                AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER, "RestrictionsScheduler: launchComponent: unknown type " + type);\n                return;\n        }\n        try {\n            ShellManager.ShellResult r = privilegedShell.launchComponent(componentName, action);\n            if (r.succeeded()) {\n                AppDebugManager.d(Category.RESTRICTIONS_SCHEDULER,\n                        "RestrictionsScheduler: launchComponent: ok component=" + componentName + " action=" + action);\n            } else {\n                AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER,\n                        "RestrictionsScheduler: launchComponent: failed (exit=" + r.exitCode()\n                                + ") component=" + componentName + " action=" + action);\n            }\n        } catch (IllegalArgumentException e) {\n            AppDebugManager.w(Category.RESTRICTIONS_SCHEDULER,\n                    "RestrictionsScheduler: launchComponent rejected component=" + componentName, e);\n        }\n'''),
    ]
    for i, (old, new) in enumerate(pairs, 1):
        text = once(text, old, new, f'scheduler {i}')
    return text

SLEEP.write_text(patch_sleep(SLEEP.read_text()))
SMART.write_text(patch_smart(SMART.read_text()))
SCHED.write_text(patch_sched(SCHED.read_text()))
