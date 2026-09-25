# AI Session State

Updated: 2026-09-25

## Current task

**UX: unify app management settings and surface app relaunches**

Branch: `ux/app-management-relaunches`

### Scope

- Keep ReAppzuku's own runtime/service behavior separate from per-app management.
- Present Auto-Kill and the former Advanced Tools surface under one coherent **App management** card.
- Keep **Restrictions & lifecycle** as a visible subsection inside App management rather than a separate top-level card.
- Complete the already-existing `AppStats.relaunchCount` / `lastRelaunchTime` telemetry by feeding it from every successful kill path that ReAppzuku owns.
- Deduplicate multi-process apps so one observation increments one relaunch per package.
- Add a dedicated **Restarted after kill** list in Statistics and report detected relaunches without claiming that ReAppzuku can prove the restart initiator.

### Validation target

- `testDebugUnitTest`
- `lintDebug`
- `assembleDebugAndroidTest`
- debug APK build
- CodeQL
- Android 17 / API 37 runtime lane

### Notes

- No preference keys change; existing user settings remain compatible.
- No Room schema migration is required because relaunch fields and DAO aggregation already exist in schema 11.
- Shizuku can observe and act with shell privileges, but it cannot guarantee that every system/OEM/privileged app remains stopped permanently. `am force-stop` is stronger than `am kill`; restrictions/suspend/disable can be stronger but have different behavior and risk.
