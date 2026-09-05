# ReAppzuku assurance roadmap

This roadmap converts the assurance matrix into a bounded engineering sequence. Work is ordered by blast radius: prevent silent privileged misbehavior first, then make persistence/recovery trustworthy, then harden interfaces and finally widen compatibility/UX coverage.

Status convention:
- `[x]` implemented in source;
- `[~]` implemented but repeatable live evidence is incomplete;
- `[ ]` not implemented / open.

## Phase 1 — Lifecycle, provenance and scheduling baseline

- [x] Model desired AutoKill service state separately from observed process state.
- [x] Prevent intentional service disable from being undone by restart paths.
- [x] Update checker points at the fork-owned repository and ignores the rolling `ondemand-test` tag.
- [x] Strict numeric release-version comparison.
- [x] Restore preset scheduling after reboot.
- [x] Centralize exact-alarm capability and fall back when exact alarms are unavailable.
- [x] Keep only `SCHEDULE_EXACT_ALARM`; remove unnecessary exact-alarm declaration.
- [x] Remove locked-boot path because configuration lives in credential-encrypted storage.
- [x] Remove destructive Room fallback.
- [x] Backup format v5 includes fork behavior settings and rejects future versions.
- [x] Accessibility configuration no longer requests unnecessary view-tree reporting.
- [x] First JVM regression tests and source-authoritative CI gates.

### Live evidence still required

- [ ] Fresh Shizuku permission flow: launch without grant, grant later, list loads only after backend is ready.
- [ ] AutoKill disable survives process death/restart.
- [ ] Reboot restores presets/restrictions without duplicate work.
- [ ] Exact-alarm denied path falls back correctly.
- [ ] Backup/restore round-trip on a real device, including App Behavior settings and preset state.
- [ ] Fork update provenance on a real installed test build.

## Phase 2 — Explicit shell readiness state machine

- [x] Explicit backend states distinguish unavailable, permission-required/pending/granted, binding, ready and lost.
- [x] ShellManager owns app-lifetime binder receive/death state and waiter release.
- [x] Operational app scans require a genuinely ready backend, not merely permission.
- [x] MainActivity waits/retries only for transitional states instead of mapping waiting to generic shell failure.
- [x] JVM transition tests cover root precedence, permission/readiness separation and lost state.
- [ ] Instrument binder-late, deny -> grant, Activity recreation and Shizuku restart/service-death recovery.
- [ ] Add event-driven UI recovery when Shizuku appears after MainActivity is already open if live testing shows the current callback chain is insufficient.

## Phase 3 — Transactional backup and restore

- [x] Snapshot main + both preset stores before writes.
- [x] Stage and validate all backup content before first durable commit.
- [x] Full preset-section restore clears omitted local preset slots; legacy backups without a preset section preserve them.
- [x] Active-preset runtime backup keys are cleared before imported settings become the new base.
- [x] Main + preset preference writes use synchronous commits during restore.
- [x] Runtime/alarm/service reconciliation happens only after durable writes succeed.
- [x] Failed writes/runtime reconciliation roll preferences back and best-effort restore old runtime state.
- [x] Reject oversized (>2 MiB), malformed and future-version backup payloads.
- [x] Add focused automated restore tests for corrupt/legacy/future/oversized/rollback paths.
- [ ] Live-test transactional rollback and active-preset restore.

### Transactional restore test coverage — 2026-09-04

- Android instrumentation coverage now exercises malformed JSON, unversioned legacy payloads, future-version rejection, the 2 MiB input bound, and restoration from captured main/preset rollback snapshots.
- The rollback helper is exercised against real Android `SharedPreferences` and `PresetManager` storage without changing production behavior.
- Runtime execution of these tests remains part of the Android evidence lane; source/AndroidTest compilation is the immediate CI gate.

## Phase 4 — Persistence and migration evidence

- [x] Explicitly exclude Android cloud backup and device-transfer restore; ReAppzuku's versioned backup remains the configuration contract.
- [x] Room schema export configured and current schema 11 generation verified in CI.
- [x] `MigrationTestHelper` instrumentation test for supported historical schema v2 -> v11 compiles.
- [~] Migration test validates preservation of existing `app_stats` data and final schema when executed on Android.
- [ ] Execute migration test on emulator/device in CI or release-validation lane.
- [x] Commit compiler-generated schema 11 and preserve every schema from now on. Historical schemas 1 and 3–10 were never preserved upstream and must not be fabricated.

## Phase 5 — Privileged surface hardening

- [x] Review identified exported shortcut Activity as a confused-deputy boundary.
- [x] Dynamic RAM shortcut uses an install-specific 256-bit authentication token.
- [x] Token comparison uses constant-time equality and has JVM regression coverage.
- [x] Static/legacy shortcut cannot silently invoke privilege; it requires explicit user confirmation.
- [x] Other explicit external launches can at most open a confirmation dialog before foreground-app force-stop.
- [x] Foreground shortcut action waits for a genuinely ready shell backend.
- [x] Review every remaining `exported=true` component and document its principal boundary in `EXPORTED_COMPONENTS.md`.
- [x] Route mutating package/component/PID operations through typed, validated `PrivilegedShell` commands and reject invalid persisted/input values before shell execution.
- [x] Fixture-test dumpsys/parser protection logic across Android/OEM variants.

### Privileged parser evidence — 2026-09-05

- `ProcessDumpParser` isolates the ActivityManager text formats consumed by `ProcessAnalyzer` from Android-dependent code.
- JVM fixtures cover AOSP PID-prefixed `ProcessRecord`, remote-process/OEM-style records, `curProcState`/`setProcState`, CRLF, exact package boundaries, binder package extraction, and short/full `ServiceRecord` forms including `$` class names.
- `ProtectedAppsTest` locks exact AOSP/Shizuku package protection plus current keyboard/launcher behavior and rejects prefix-neighbor false positives.
- One-time gate run `33940487030` passed unit tests, lint, AndroidTest compilation and debug APK build before committing the production integration as `57d8897c70a5b4c9565379cd72317f1abb7c9e59`.
- This is repeatable parser/source evidence, not a substitute for the still-open Android/OEM runtime probes.


### Privileged shell evidence — 2026-09-05

- `PrivilegedShell` owns validated kill/force-stop, uninstall, AppOps, standby bucket, DeviceIdle whitelist, suspend/unsuspend, enable/disable, PID kill and explicit component-launch commands.
- Runs `33940964413` and `33941247796` passed unit tests, lint, AndroidTest compilation and debug APK build before integrating the major manager paths.
- Final strict run `33946348081` passed the same gates and required a repository-wide mutating-shell audit to return `NONE` outside `PrivilegedShell`, including the Quick Tile and Restrictions Watchdog paths.
- Normal read-only validation run `33946501074` then passed on the cleaned permanent source state.
- These are source/JVM/CI invariants; live Shizuku/root execution probes remain separate Android evidence.

## Phase 6 — CI and supply chain

- [x] Preserve `concurrency.cancel-in-progress` for the live-test workflow.
- [x] Pin `actions/checkout` and `actions/setup-java` to immutable full commit SHAs.
- [x] Validation workflow is source-authoritative; no patch/generator step remains after migrations complete.
- [x] Split read-only validation from release publishing with job-level least-privilege tokens.
- [x] Run `lintDebug` without `updateLintBaseline`; baseline changes only through reviewed source commits.
- [x] Pin every external Action in active repository workflows.
- [x] Establish a reviewed warning-only lint baseline: full scan must contain zero errors before a baseline may be accepted.
- [x] Remove the ReAppzuku-owned Gradle-10 Groovy assignment deprecation identified by `--warning-mode all`.
- [x] Enforce Gradle dependency verification with committed SHA-256 metadata and dependency locking.
- [~] Expand emulator/device test lanes without wasting private Actions quota; the API 37 lane has been exercised and is currently blocked by preview PackageManager transport instability before repeatable instrumentation.

### Assurance evidence — 2026-09-04 to 2026-09-05

- Workflow run `33577363239` passed unit tests, zero-error full lint, reviewed warning baseline,
  `assembleDebugAndroidTest`, Room schema-11 existence check, and debug APK build.
- Release `ondemand-test` targets commit `e202e38c049a0d4a7cfc561f7a9c8348c9abd8ae`.
- Published APK SHA-256:
  `d841e34685d790197266c1e9c90a11619a33a219377892f2c429c00930dbf5d4`.
- Normal validation returns to a read-only Validate -> writable Publish split after the one-time
  migration workflow.
- Runs `33816595259` and `33816830268` independently forced Room compilation with cache disabled and generated schema 11 from the current source model.
- One-time run `33816989187` was guarded to stage exactly one path and committed only `app/schemas/com.gree1d.reappzuku.db.AppDatabase/11.json` as commit `8707be6`.
- Dependency-assurance run `33832156664` generated `app/gradle.lockfile` and SHA-256 `gradle/verification-metadata.xml`, then passed unit, lint, AndroidTest compile and APK build again without metadata-writing flags.
- Artifact `9922094160` is preserved by SHA-256 `43c53cc13431cd2b2513fb0d9108836d548dd4b33b5673e9ddd49e4b1954918c`; its exact generated files are committed and normal builds now enforce them.
- Dependency-verification refresh run `33900939628` regenerated the complete SHA-256 set from fresh resolution and then passed the build again after deleting the dependency cache, closing the warm-cache blind spot.
- Normal read-only validation run `33901414025` subsequently passed on the permanent locked/verified dependency state.

## Phase 7 — Android 17 / API 37

- [~] Android 17/API 37 runtime compatibility lane exists and has been executed before changing target SDK; build/boot/unlock/package-readiness are proven, but repeatable installation/instrumentation is `RISK/BLOCKED` by preview PackageManager `Broken pipe (32)` failures.
- [x] Plan compatible AGP/toolchain migration separately in `ANDROID17_COMPATIBILITY.md`.
- [~] Execute the API 37 lane and re-test hidden APIs, Accessibility, FGS, WorkManager, alarm behavior and process/memory assumptions; current preview environment blocks the lane before those app probes can complete.
- [x] Migrate build tooling to an API-37-capable AGP/Gradle combination while keeping `targetSdk 36`.
- [x] Raise `compileSdk` to 37 and validate before changing target behavior.
- [ ] Raise `targetSdk` to 37 only after target-37 behavior probes pass.

### Android 17 runtime evidence — 2026-09-04

- Run `33699862420`: both app and instrumentation APKs installed successfully on API 37 and `USE_NEW_MESSAGEQUEUE` was enabled; instrumentation then exposed a lane bug because user 0 had not yet reached `RUNNING_UNLOCKED`.
- The lane was corrected to require `RUNNING_UNLOCKED`, a ready PackageManager service, 8 GiB `/data`, stable-channel emulator binaries, explicit runner libraries, and build-before-emulator resource separation.
- Run `33812905493`: API-37 image setup, app/androidTest build, stable-emulator boot, API=37 verification, `RUNNING_UNLOCKED`, PackageManager readiness and free-space checks all passed. Three non-streaming app installation attempts each pushed the 19,343,120-byte APK successfully, then failed only at the PackageManager transaction with `Failure calling service package: Broken pipe (32)`.
- No APK validation/signature/parse/`INSTALL_FAILED_*` error was observed. Runtime compatibility therefore remains `RISK/BLOCKED`, not failed and not `PROVEN`. No further Actions retries should be spent on the same preview image.
- The temporary runtime workflow was removed from the release path; normal read-only Validate -> write-only Publish CI was restored in `031a8634db725bb93185d3e55819dc8b5165e96d`.
- Run `33814172310` proved the AGP 9.4.0 / Gradle 9.6.0 / built-in Kotlin 2.3.21 migration at `compileSdk 36`, `targetSdk 36`: unit tests, lint, AndroidTest compilation, Room schema validation and APK build all passed; publishing was skipped.
- Run `33815939003` then proved `compileSdk 37` with `targetSdk 36` through the same gates. The validated APK SHA-256 is `50f07dc229729b0df68c14d6550cf0f52c286297c8ae541fc62f57c18a5c9912`; publishing was skipped.
- The remaining Android-17 blocker is runtime execution only. `targetSdk 37` stays blocked until the API-37 runtime probes can execute repeatably.

## Phase 8 — UX, i18n, maintainability

- [x] Propagate fork-specific Smart Lifecycle/App Behavior/security strings to supported locales.
- [x] Explain blocking automation directly beside disabled App Behavior controls, listing the active AutoKill/Smart Lifecycle/Sleep/Preset/Scheduler blockers from the central policy.
- [x] Split large managers behind testable facades: `PrivilegedShell`, `AlarmScheduler`, `Clock`/`ScheduleTime`, `BackupCodec` and central protection/background/parser policy boundaries now isolate the named high-risk seams.
- [~] Keep `CHECK_MATRIX.md` status/evidence current after every high-impact change (refreshed through 2026-09-05; ongoing discipline).


### Maintainability evidence — 2026-09-05

- Scheduling facade gate `33946729221` passed unit tests, lint, AndroidTest compilation and debug APK build before integrating `Clock`/`ScheduleTime` and `AlarmScheduler`.
- BackupCodec gate `33946888672` compiles the focused Android codec tests and passes the same application validation before integration.

- App Behavior exposes the exact active continuity blockers from `BackgroundWorkPolicy` rather than duplicating feature-state logic in the UI.
- `Clock` plus pure `ScheduleTime` make daily scheduling deterministic under JVM tests, while `AlarmScheduler` centralizes AlarmManager availability and exact-vs-best-effort behavior.
- `PresetManager` and `RestrictionsScheduler` keep their existing public constructors but receive clock/alarm dependencies through internal injection points.
- `BackupCodec` now owns bounded/versioned JSON envelope decode/encode while `BackupManager` retains the transactional storage/rollback side effects; the named facade extraction item is closed.

## Stable-release gate

A candidate is not stable until all P0 findings are `PROVEN`, no P1 is unowned, reboot/permission/migration paths have repeatable Android evidence, update provenance is correct, exported privileged boundaries are reviewed, and release artifact/signing identity + rollback path are recorded.
