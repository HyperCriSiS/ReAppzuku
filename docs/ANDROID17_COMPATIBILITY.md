# Android 17 / API 37 compatibility plan

Status: toolchain migration and `compileSdk 37` are **PROVEN** with `targetSdk 36`. The API 37 runtime environment remains **RISK/BLOCKED** by repeatable PackageManager transport failure before repeatable instrumentation; runtime compatibility is therefore not `PROVEN`, and `targetSdk 37` remains blocked.

## Current build baseline

- `compileSdk 37`
- `targetSdk 36`
- `minSdk 24`
- Android Gradle Plugin `9.4.0`
- Gradle `9.6.0`
- JDK 17
- AGP built-in Kotlin with Kotlin/Compose compiler `2.3.21`

The original API-36/AGP-8 baseline was retained for the first Android 17 runtime investigation so operating-system regressions could be separated from build-tool migration regressions. That isolation is complete; the current build baseline above is now validated independently.

## Official toolchain constraint

API level 37 requires an API-37-capable Android Gradle Plugin. ReAppzuku now uses AGP `9.4.0`, Gradle `9.6.0` and JDK 17. AGP 9 built-in Kotlin is enabled; the former explicit `org.jetbrains.kotlin.android` plugin and temporary AGP-9 compatibility opt-outs have been removed.

The migration was intentionally staged and proven before raising `compileSdk`, so build-system failures remain distinguishable from Android-17 API/behavior failures.

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

The API 37 runtime evidence lane (temporarily dispatched through the already registered test workflow while the feature branch is not on the default branch) must:

1. use only read repository permission;
2. boot an API 37 emulator;
3. verify the runtime API is exactly 37;
4. install debug + instrumentation APKs;
5. enable `USE_NEW_MESSAGEQUEUE`;
6. run Android instrumentation successfully;
7. perform a launcher smoke test and fail on ReAppzuku crash-buffer entries;
8. upload bounded diagnostic evidence;
9. never publish a release.

A successful lane upgrades the runtime item to `PROVEN` and also supplies the missing emulator execution evidence for the Room migration test. A blocked preview environment must remain `RISK/BLOCKED`; infrastructure failures must not be relabelled as app compatibility failures or accepted as proof.

## Runtime evidence — 2026-09-04

The API 37 lane was exercised repeatedly against `system-images;android-37.0;google_apis;x86_64` with a stable-channel Android Emulator. The lane itself is now deterministic enough to separate app evidence from emulator failure:

- APK and AndroidTest APK compile successfully before emulator boot.
- KVM is available; API 37 boots to `sys.boot_completed=1`.
- User 0 reaches `RUNNING_UNLOCKED`; ReAppzuku is not a Direct-Boot app and credential-encrypted preferences are not accessed before that gate.
- `/data` is expanded to 8 GiB and has roughly 6.8 GiB free.
- PackageManager readiness is explicitly verified before installation.
- Run `33699862420` installed both APKs successfully and enabled `USE_NEW_MESSAGEQUEUE`; its instrumentation process then started too early while user 0 was still locked, which was corrected in later lane revisions.
- Runs `33700842763`, `33812502601`, and `33812905493` reached a healthy boot/unlocked/package-ready state but the preview system server returned `Failure calling service package: Broken pipe (32)` when the APK install transaction began.
- Run `33812905493` pushed the exact app APK successfully three times using non-streaming install; every PackageManager transaction then failed with the same `Broken pipe (32)` even after the service recovered between attempts. No `INSTALL_FAILED_*`, signature, parse, permission, or APK validation error was reported.

Conclusion: API 37 runtime compatibility is **not proven and not disproven**. The current blocker is the preview emulator/system-server PackageManager transport, with one earlier successful install showing that the APK itself is installable on the same API 37 image. Do not spend additional private Actions quota retrying the same image. Re-run this lane when an updated API 37 image/emulator combination or another repeatable API 37 execution environment is available.

After the bounded investigation, the normal least-privilege Validate -> Publish workflow was restored from the known-good assurance state in commit `031a8634db725bb93185d3e55819dc8b5165e96d`.

## Toolchain / compile SDK evidence — 2026-09-04

- Commit `9168ec54a79464d9d3e522ff8a82068020271854` migrated the build to AGP `9.4.0`, Gradle `9.6.0` and AGP built-in Kotlin with Kotlin/Compose compiler `2.3.21`, while keeping `compileSdk 36` / `targetSdk 36`.
- Run `33814172310` passed unit tests, lint, AndroidTest APK compilation, Room schema-11 validation and debug APK assembly. Publish was deliberately skipped.
- Commit `65a597b5eb8b5854f24f9d0ceb8c24c529f6ec78` raised only `compileSdk` to 37; `targetSdk` remained 36.
- Run `33815939003` passed the same complete validation chain under `compileSdk 37`. The resulting validation APK SHA-256 is `50f07dc229729b0df68c14d6550cf0f52c286297c8ae541fc62f57c18a5c9912`; publish was deliberately skipped.
- Therefore toolchain compatibility and API-37 compilation are `PROVEN`. Runtime compatibility remains independently `RISK/BLOCKED`, and `targetSdk 37` must not be enabled until those runtime probes pass.
