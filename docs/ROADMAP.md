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
- [ ] Add focused automated restore tests for corrupt/legacy/future/oversized/rollback paths.
- [ ] Live-test transactional rollback and active-preset restore.

## Phase 4 — Persistence and migration evidence

- [x] Explicitly exclude Android cloud backup and device-transfer restore; ReAppzuku's versioned backup remains the configuration contract.
- [x] Room schema export configured and current schema 11 generation verified in CI.
- [x] `MigrationTestHelper` instrumentation test for supported historical schema v2 -> v11 compiles.
- [~] Migration test validates preservation of existing `app_stats` data and final schema when executed on Android.
- [ ] Execute migration test on emulator/device in CI or release-validation lane.
- [ ] Commit generated schema 11 and every schema from now on. Historical schemas 1 and 3–10 were never preserved upstream and must not be fabricated.

## Phase 5 — Privileged surface hardening

- [x] Review identified exported shortcut Activity as a confused-deputy boundary.
- [x] Dynamic RAM shortcut uses an install-specific 256-bit authentication token.
- [x] Token comparison uses constant-time equality and has JVM regression coverage.
- [x] Static/legacy shortcut cannot silently invoke privilege; it requires explicit user confirmation.
- [x] Other explicit external launches can at most open a confirmation dialog before foreground-app force-stop.
- [x] Foreground shortcut action waits for a genuinely ready shell backend.
- [x] Review every remaining `exported=true` component and document its principal boundary in `EXPORTED_COMPONENTS.md`.
- [ ] Introduce typed/validated privileged operations instead of ad-hoc shell command strings where practical.
- [ ] Fixture-test dumpsys/parser protection logic across Android/OEM variants.

## Phase 6 — CI and supply chain

- [x] Preserve `concurrency.cancel-in-progress` for the live-test workflow.
- [x] Pin `actions/checkout` and `actions/setup-java` to immutable full commit SHAs.
- [x] Validation workflow is source-authoritative; no patch/generator step remains after migrations complete.
- [x] Split read-only validation from release publishing with job-level least-privilege tokens.
- [x] Run `lintDebug` without `updateLintBaseline`; baseline changes only through reviewed source commits.
- [x] Pin every external Action in active repository workflows.
- [x] Establish a reviewed warning-only lint baseline: full scan must contain zero errors before a baseline may be accepted.
- [ ] Add dependency verification/locking evaluation.
- [~] Expand emulator/device test lanes without wasting private Actions quota; the API 37 lane has been exercised and is currently blocked by preview PackageManager transport instability before repeatable instrumentation.

### Assurance evidence — 2026-09-02

- Workflow run `33577363239` passed unit tests, zero-error full lint, reviewed warning baseline,
  `assembleDebugAndroidTest`, Room schema-11 existence check, and debug APK build.
- Release `ondemand-test` targets commit `e202e38c049a0d4a7cfc561f7a9c8348c9abd8ae`.
- Published APK SHA-256:
  `d841e34685d790197266c1e9c90a11619a33a219377892f2c429c00930dbf5d4`.
- Normal validation returns to a read-only Validate -> writable Publish split after the one-time
  migration workflow.

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
- [ ] Explain blocking automation directly beside disabled App Behavior controls.
- [ ] Split large managers behind testable facades (`PrivilegedShell`, `AlarmScheduler`, `ProtectionPolicy`, `BackupCodec`, `Clock`).
- [ ] Keep `CHECK_MATRIX.md` status/evidence current after every high-impact change.

## Stable-release gate

A candidate is not stable until all P0 findings are `PROVEN`, no P1 is unowned, reboot/permission/migration paths have repeatable Android evidence, update provenance is correct, exported privileged boundaries are reviewed, and release artifact/signing identity + rollback path are recorded.
