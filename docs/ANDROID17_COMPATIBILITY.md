# Android 17 / API 37 compatibility plan

Status: staged before any `compileSdk` or `targetSdk` migration.

## Current build baseline

- `compileSdk 36`
- `targetSdk 36`
- `minSdk 24`
- Android Gradle Plugin `8.10.0`
- Gradle `8.11.1`
- JDK 17
- Kotlin Android / Compose plugin `2.0.21`

This baseline is intentionally retained for the first Android 17 runtime lane so operating-system
regressions can be separated from build-tool migration regressions.

## Official toolchain constraint

API level 37 requires Android Gradle Plugin 9.1.1 or newer. AGP 9.1 requires Gradle 9.3.1 and
JDK 17. Therefore `compileSdk 37` is not treated as a one-line version bump from the current
AGP 8.10 build.

AGP 9 also enables built-in Kotlin support by default. The existing explicit
`org.jetbrains.kotlin.android` setup must be migrated or deliberately opted out; this is a separate
build-system change and will not be mixed into the first runtime test.

## Migration order

1. **Runtime compatibility first, target remains 36**
   - Boot a real Android 17/API 37 emulator.
   - Build/install the existing app.
   - Enable Android 17's lock-free MessageQueue compatibility change with `am compat`.
   - Execute the Room v2 -> v11 instrumentation migration test.
   - Launch the normal app entry point and fail on process crashes.

2. **Toolchain migration, target remains 36**
   - Move to an API-37-capable AGP (minimum 9.1.1).
   - Move Gradle to the required compatible version (minimum 9.3.1 for AGP 9.1).
   - Migrate the Kotlin/Compose build configuration for AGP 9 built-in Kotlin.
   - Keep normal unit/lint/androidTest/build gates green.

3. **Raise `compileSdk` to 37, target remains 36**
   - Compile against API 37.
   - Resolve new compile/lint findings without enabling target-37 behavior.
   - Re-run API 37 runtime lane.

4. **Raise `targetSdk` to 37**
   - Re-run the target-37 behavior checklist.
   - Only then publish a target-37 test artifact.

## Android 17 risk inventory

### Behavior affecting all apps

Android 17 adds process memory limits based on device RAM. ReAppzuku must be observed under the API
37 emulator for process death/recovery behavior even before changing `targetSdk`.

### Behavior gated by target 37

- Lock-free `MessageQueue`.
  - Direct source scan currently finds no ReAppzuku reflection against `MessageQueue`.
  - The API 37 lane explicitly enables `USE_NEW_MESSAGEQUEUE` before the target bump.
- `RemoteViews` combined Bitmap/Icon memory limit.
  - ReAppzuku's Glance widget is predominantly text/progress UI; nevertheless widget behavior is
    retained in the target-37 manual test checklist.
- Reflection mutation of `static final` fields is prohibited.
  - No such ReAppzuku source pattern has been identified so far.
- Local-network access receives a target-37 permission model.
  - No direct LAN client functionality has been identified in current ReAppzuku source, so
    `ACCESS_LOCAL_NETWORK` is not added speculatively.

## Existing high-risk platform integration to re-test

- Shizuku binder/UserService lifecycle and its special process context.
- Root/Shizuku backend loss and rebinding.
- Accessibility foreground tracking.
- Foreground service desired-state recovery.
- WorkManager AutoKill/Smart Lifecycle scheduling.
- Preset and Restrictions Scheduler alarms, including exact-alarm denial.
- `dumpsys`/shell parsers used for protection decisions.
- Widget update path.
- Room migration v2 -> v11.

## Evidence contract for the API 37 lane

The manual workflow `.github/workflows/android17-runtime.yml` must:

1. use only read repository permission;
2. boot an API 37 emulator;
3. verify the runtime API is exactly 37;
4. install debug + instrumentation APKs;
5. enable `USE_NEW_MESSAGEQUEUE`;
6. run Android instrumentation successfully;
7. perform a launcher smoke test and fail on ReAppzuku crash-buffer entries;
8. upload bounded diagnostic evidence;
9. never publish a release.

A successful lane upgrades the first Phase-7 roadmap item from staged to proven runtime evidence,
and also supplies the missing emulator execution evidence for the Room migration test.
