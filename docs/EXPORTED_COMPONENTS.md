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
| `BootReceiver` | Receive system boot completion | Receiver checks exactly `Intent.ACTION_BOOT_COMPLETED`; no external extras affect target/actions | Restores explicitly persisted automation state | Keep exported for system broadcast; action validation remains mandatory |
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
- `BootReceiver` ignores null/non-`BOOT_COMPLETED` actions.
- `AppzukuWidgetReceiver` delegates only to the read-only Glance widget data path.

## Remaining hardening

The principal-boundary review is complete for the current manifest. Remaining privileged-surface work is at the command boundary itself: package validation, typed privileged operations where practical, and parser fixtures for OEM/Android variations.
