# Exported Component Security Review

Audit baseline: `ondemand-shizuku`, 2026-09-01.

Purpose: every `android:exported="true"` component must have an explicit principal boundary. Export is not considered safe merely because Android requires it for a platform integration.

| Component | Why exported | Caller boundary | Privileged effect | Decision |
|---|---|---|---|---|
| `MainActivity` | Launcher / Leanback launcher entry point | User-facing launcher intent; activity does not consume external privilege-bearing extras | None on launch beyond normal app UI | Keep exported |
| `KillShortcutActivity` | Static and pinned launcher shortcuts | Dynamic RAM shortcut requires install-specific 256-bit token; static/legacy/external launches require explicit confirmation | Foreground force-stop / RAM cleanup through privileged backend | Keep exported with application-layer authorization |
| `AppLaunchAccessibilityService` | Android Accessibility framework binding | `android.permission.BIND_ACCESSIBILITY_SERVICE` | Foreground package observation only | Keep exported; platform signature permission is the boundary |
| `ShappkyQuickTile` | Android Quick Settings tile discovery/binding | `android.permission.BIND_QUICK_SETTINGS_TILE` | User-initiated privileged foreground-app action | Keep exported; platform signature permission is the boundary |
| `ShappkyBackgroundKillTile` | Android Quick Settings tile discovery/binding | `android.permission.BIND_QUICK_SETTINGS_TILE` | User-initiated background cleanup | Keep exported; platform signature permission is the boundary |
| `BootReceiver` | Receive protected boot, time-set and timezone-changed system broadcasts | Receiver accepts only `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED`; unrelated actions are ignored, external extras cannot choose a package or command | Boot restores persisted automation/worker state; clock changes only rebuild local-wall-clock restriction and Automation Schedule alarms and reconcile active schedules | Keep exported for protected platform broadcasts; revalidate action contract on each change |
| `PackageAddedReceiver` | Receive the platform's package-installed broadcast | Receiver accepts exactly `Intent.ACTION_PACKAGE_ADDED`, ignores `EXTRA_REPLACING`, requires the `package:` data scheme and validates the package identifier; Android protects this system broadcast | Records a canonical new-app policy/setup decision only; Ask/Leave are explicitly Unmanaged, and managed policy is applied only when the user previously selected a default preset | Keep exported for system broadcast; no Activity launch and no raw extra is passed to a privileged shell command |
| `AppzukuWidgetReceiver` | AppWidget host/provider integration | AppWidget framework intents | Reads `/proc/meminfo` and local statistics only; no Shizuku/root action | Keep exported; no privileged command surface |
| `rikka.shizuku.ShizukuProvider` | Required Shizuku binder bootstrap contract | `android.permission.INTERACT_ACROSS_USERS_FULL` plus Shizuku's provider protocol | Binder/bootstrap only; no arbitrary app command endpoint | Keep exported as required by Shizuku integration |

## Invariants

1. No exported component may turn untrusted extras directly into shell commands, package names or app-op arguments.
2. Platform-bound services must retain their platform binding permissions.
3. Exported user entry points that can lead to privileged operations require either a platform-trusted caller boundary or explicit user authorization.
4. System receivers must validate the received action and ignore unrelated broadcasts.
5. Widget/update entry points remain read-only with respect to privileged device state.
6. Any new `exported=true` manifest entry requires an update to this document and a CHECK_MATRIX review before release.

## Evidence

- `ShortcutAuthTest` covers valid/invalid token comparison for dynamic privileged shortcuts.
- Unit/build gates compile all exported entry points.
- Android platform contracts protect Accessibility and Quick Settings services via their required binding permissions.
- The permanent API-37 security lane builds a separate `com.reappzuku.securityprobe` APK with no requested permissions or shared UID, verifies its UID differs from ReAppzuku, and requires that it receives no Binder from the Accessibility service or either Quick Tile. Run `34668594681` passed this external-principal probe.
- The same external probe requires a foreign explicit `BOOT_COMPLETED` injection to be rejected; run `34668594681` passed.
- `BootReceiver` ignores null/unrelated actions. `TIME_SET` and `TIMEZONE_CHANGED` are Android-protected broadcasts and manifest implicit-broadcast exceptions; they rebuild alarm times without entering the boot-only worker path. Clock-change delivery is verified separately from boot idempotency; foreign principals cannot send the protected actions through ordinary app broadcasts.
- `PackageAddedReceiver` accepts only fresh `PACKAGE_ADDED` events, ignores replacement/update broadcasts, validates the package name, and delegates asynchronously without launching UI over the foreground app.
- `AppzukuWidgetReceiver` delegates only to the read-only Glance widget data path.

- Android 7/API24 `37873206394` and Android 17/API37 `37873211270` passed the read-only `ExportedComponentManifestInstrumentationTest` against installed, merged package metadata: non-exported internal activities/receivers (including intentionally disabled Shizuku wake), exported platform/widget/shortcut entry points, Accessibility/Quick Tile binding permissions, and the Shizuku provider authority/read/write permissions. Existing separate-UID API37 abuse probing also passed; this metadata test is not itself a foreign-principal attack attempt.

## Remaining hardening

The principal-boundary review is complete for the current manifest. Remaining privileged-surface work is at the command boundary itself: package validation, typed privileged operations where practical, and parser fixtures for OEM/Android variations.