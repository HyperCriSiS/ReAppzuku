from pathlib import Path

AUTO = Path('app/src/main/java/com/gree1d/reappzuku/manager/AutoKillManager.java')
BG = Path('app/src/main/java/com/gree1d/reappzuku/manager/BackgroundAppManager.java')
EXPECTED_AUTO = '04d590409a882fa8802167662d634a1f0703431b'
EXPECTED_BG = 'f48df10d219d5b99a950e7a3ff0c1dc8685e66f5'


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 match, got {count}')
    return text.replace(old, new, 1)


def patch_auto(text):
    pairs = [
        ('import com.gree1d.reappzuku.core.PackageNameValidator;\n',
         'import com.gree1d.reappzuku.core.PackageNameValidator;\nimport com.gree1d.reappzuku.core.PrivilegedShell;\n'),
        ('    private final ShellManager shellManager;\n',
         '    private final ShellManager shellManager;\n    private final PrivilegedShell privilegedShell;\n'),
        ('        this.shellManager = shellManager;\n        this.currentAppsList = currentAppsList;\n',
         '        this.shellManager = shellManager;\n        this.privilegedShell = new PrivilegedShell(shellManager);\n        this.currentAppsList = currentAppsList;\n'),
        ('''                String killCommand = toKill.stream()\n                        .map(this::buildKillCommand)\n                        .collect(Collectors.joining("; "));\n\n                shellManager.runShellCommandAndGetFullOutput(killCommand);\n''',
         '''                privilegedShell.killPackagesAndGetFullOutput(\n                        toKill, PrivilegedShell.KillMode.fromAutoKillType(getAutoKillType()));\n'''),
        ('''        String command = packageNames.stream()\n                .map(this::buildKillCommand)\n                .collect(Collectors.joining("; "));\n\n        final long finalTotalKb = totalKb;\n        final List<String> packagesToLog = new ArrayList<>(packageNames);\n        final Map<String, Long> recoveredToLog = new HashMap<>(recoveredKbByPackage);\n        shellManager.runShellCommand(command, () -> {\n''',
         '''        final PrivilegedShell.KillMode killMode =\n                PrivilegedShell.KillMode.fromAutoKillType(getAutoKillType());\n\n        final long finalTotalKb = totalKb;\n        final List<String> packagesToLog = new ArrayList<>(packageNames);\n        final Map<String, Long> recoveredToLog = new HashMap<>(recoveredKbByPackage);\n        privilegedShell.killPackages(packageNames, killMode, () -> {\n'''),
        ('        shellManager.runShellCommand(buildKillCommand(packageToKill), () -> {\n',
         '        privilegedShell.killPackage(packageToKill,\n                PrivilegedShell.KillMode.fromAutoKillType(getAutoKillType()), () -> {\n'),
        ('''        if (packageName == null || packageName.isEmpty()) {\n            if (onComplete != null) {\n                handler.post(onComplete);\n            }\n            return;\n        }\n\n        String command = "pm uninstall " + packageName;\n        shellManager.runShellCommand(command, () -> {\n''',
         '''        if (!PackageNameValidator.isValid(packageName)) {\n            if (onComplete != null) {\n                handler.post(onComplete);\n            }\n            return;\n        }\n\n        privilegedShell.uninstallPackage(packageName, () -> {\n'''),
        ('''    private String buildKillCommand(String packageName) {\n        PackageNameValidator.requireValid(packageName);\n        String cmd = (getAutoKillType() == 1 ? "am kill " : "am force-stop ") + packageName;\n        AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: buildKillCommand: " + cmd + " (type=" + getAutoKillType() + ")");\n        return cmd;\n    }\n\n''', ''),
        ('''        String cmd = buildKillCommand(packageName);\n        AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: killPackageSync: " + cmd + " source=" + source);\n        shellManager.runShellCommandAndGetFullOutput(cmd);\n''',
         '''        PrivilegedShell.KillMode killMode =\n                PrivilegedShell.KillMode.fromAutoKillType(getAutoKillType());\n        AppDebugManager.d(Category.AUTO_KILL_BASE, "AutoKillManager: killPackageSync: pkg="\n                + packageName + " type=" + killMode + " source=" + source);\n        privilegedShell.killPackageAndGetFullOutput(packageName, killMode);\n'''),
        ('            shellManager.runShellCommandAndGetFullOutput("kill -9 " + String.join(" ", toKill));\n',
         '            privilegedShell.killPidsAndGetFullOutput(toKill);\n'),
    ]
    for i, (old, new) in enumerate(pairs, 1):
        text = replace_once(text, old, new, f'AutoKill replacement {i}')
    return text


def patch_bg(text):
    singles = [
        ('import com.gree1d.reappzuku.core.PackageNameValidator;\n',
         'import com.gree1d.reappzuku.core.PackageNameValidator;\nimport com.gree1d.reappzuku.core.PrivilegedShell;\n'),
        ('    private static final String FORCE_STOP_COMMAND_PREFIX = "am force-stop ";\n', ''),
        ('    private final ShellManager shellManager;\n',
         '    private final ShellManager shellManager;\n    private final PrivilegedShell privilegedShell;\n'),
        ('        this.shellManager = shellManager;\n        this.iconCache = ((App) context.getApplicationContext()).getIconCache();\n',
         '        this.shellManager = shellManager;\n        this.privilegedShell = new PrivilegedShell(shellManager);\n        this.iconCache = ((App) context.getApplicationContext()).getIconCache();\n'),
        ('''    private String buildBackgroundRestrictionCommand(String packageName, String mode) {\n        return "cmd appops set --user current " + packageName + " " + BACKGROUND_RESTRICTION_OP + " " + mode;\n    }\n\n    private String buildHardRestrictionCommand(String packageName, String mode) {\n        return "cmd appops set --user current " + packageName + " " + FOREGROUND_RESTRICTION_OP + " " + mode;\n    }\n\n''', ''),
        ('''        boolean ok = shellManager.runShellCommandForResult(\n                "am set-standby-bucket " + packageName + " " + bucket)\n                .succeeded();\n''',
         '''        boolean ok = privilegedShell.setStandbyBucket(\n                packageName, PrivilegedShell.StandbyBucket.fromLegacyValue(bucket)).succeeded();\n'''),
        ('''        boolean ok = shellManager.runShellCommandForResult(\n                "am set-standby-bucket " + packageName + " active")\n                .succeeded();\n''',
         '''        boolean ok = privilegedShell.setStandbyBucket(\n                packageName, PrivilegedShell.StandbyBucket.ACTIVE).succeeded();\n'''),
        ('''        ShellManager.ShellResult result = shellManager.runShellCommandForResult(\n                "cmd deviceidle whitelist -" + packageName);\n''',
         '''        ShellManager.ShellResult result = privilegedShell.updateDeviceIdleWhitelist(\n                packageName, PrivilegedShell.DeviceIdleWhitelistAction.REMOVE);\n'''),
        ('        shellManager.runShellCommandForResult("cmd deviceidle whitelist +" + packageName);\n',
         '        privilegedShell.updateDeviceIdleWhitelist(\n                packageName, PrivilegedShell.DeviceIdleWhitelistAction.ADD);\n'),
        ('''ShellManager.ShellResult restrictResult = shellManager\n                            .runShellCommandForResult(buildBackgroundRestrictionCommand(packageName, "ignore"));''',
         '''ShellManager.ShellResult restrictResult = privilegedShell.setAppOp(\n                            packageName, BACKGROUND_RESTRICTION_OP, PrivilegedShell.AppOpMode.IGNORE);'''),
        ('''ShellManager.ShellResult result = shellManager\n                            .runShellCommandForResult(buildBackgroundRestrictionCommand(packageName, "ignore"));''',
         '''ShellManager.ShellResult result = privilegedShell.setAppOp(\n                            packageName, BACKGROUND_RESTRICTION_OP, PrivilegedShell.AppOpMode.IGNORE);'''),
        ('''boolean ok = shellManager.runShellCommandForResult(\n                        buildBackgroundRestrictionCommand(packageName, "default")).succeeded();''',
         '''boolean ok = privilegedShell.setAppOp(\n                        packageName, BACKGROUND_RESTRICTION_OP, PrivilegedShell.AppOpMode.DEFAULT).succeeded();'''),
        ('''boolean ok = shellManager.runShellCommandForResult(\n                        buildBackgroundRestrictionCommand(packageName, "ignore")).succeeded();''',
         '''boolean ok = privilegedShell.setAppOp(\n                        packageName, BACKGROUND_RESTRICTION_OP, PrivilegedShell.AppOpMode.IGNORE).succeeded();'''),
        ('''boolean succeeded = shellManager.runShellCommandForResult(\n                            "cmd appops set --user current " + pkg + " " + op + " ignore")\n                            .succeeded();''',
         '''boolean succeeded = privilegedShell.setAppOp(\n                            pkg, op, PrivilegedShell.AppOpMode.IGNORE).succeeded();'''),
    ]
    for i, (old, new) in enumerate(singles, 1):
        text = replace_once(text, old, new, f'Background replacement {i}')

    force = 'shellManager.runShellCommandForResult(FORCE_STOP_COMMAND_PREFIX + packageName);'
    if text.count(force) != 4:
        raise SystemExit(f'Background force-stop: expected 4, got {text.count(force)}')
    text = text.replace(force, 'privilegedShell.forceStopPackage(packageName);')

    allops = '''shellManager.runShellCommandForResult(\n                    "cmd appops set --user current " + packageName + " " + ALL_OPS[i] + " " + mode)'''
    if text.count(allops) != 3:
        raise SystemExit(f'Background appops loop: expected 3, got {text.count(allops)}')
    text = text.replace(allops, '''privilegedShell.setAppOp(packageName, ALL_OPS[i],\n                    PrivilegedShell.AppOpMode.fromShellValue(mode))''')
    return text

AUTO.write_text(patch_auto(AUTO.read_text()))
BG.write_text(patch_bg(BG.read_text()))
