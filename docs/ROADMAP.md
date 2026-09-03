# ReAppzuku Assurance Roadmap

Source: `CHECK_MATRIX.md`, `QUALITY_GATES.md`, Red-Team review and live-device feedback.

Status: `[x]` implemented with repeatable build/test evidence, `[~]` implemented but Android live/runtime evidence still pending, `[ ]` open.

## Phase 1 — Live hardening baseline

- [x] Central desired-state rule: intentional Auto-Kill disable beats service self-restart.
- [x] Fork-owned update provenance (`HyperCriSiS/ReAppzuku`); ignore non-version test tags.
- [~] Restore preset alarms/state after normal boot.
- [x] Central exact-alarm capability with safe best-effort fallback when permission is unavailable.
- [x] Keep only `SCHEDULE_EXACT_ALARM`; remove redundant `USE_EXACT_ALARM`.
- [x] Remove obsolete locked-boot path for credential-protected configuration.
- [x] Remove destructive Room migration fallback; missing migration fails visibly rather than erasing logs/statistics.
- [x] Backup v5 includes Smart Lifecycle/App Behavior settings and rejects future formats.
- [x] Accessibility config: correct settings activity and remove unnecessary view-tree flag.
- [x] First pure JVM policy/version tests.
- [x] Checked-in source is authoritative; normal CI no longer patches/commits application source.

### Live test checklist

1. Fresh install / Shizuku permission removed: app waits for permission + UserService readiness and then loads apps.
2. Grant Shizuku slowly; rotate/background during dialog; no `Failed to get running Apps output` race.
3. Enable Auto-Kill, then disable it and wait >10 s: foreground service must stay off.
4. Enable Auto-Kill, kill ReAppzuku service/process externally: recovery may restart it while desired state remains enabled.
5. Reboot with Auto-Kill disabled: no persistent foreground service should appear.
6. Reboot with an enabled timed preset: preset alarms are rebuilt and the currently active time window is reconciled.
7. Deny/remove exact-alarm access: preset/scheduler must not crash; timing degrades to best-effort.
8. Backup/restore: Exit-on-Back, Prevent Shizuku Auto-Start and Smart Lifecycle settings round-trip.
9. Update check must never navigate to or install from upstream `gree1d/ReAppzuku`.

## Phase 2 — Privilege state machine

- [x] Explicit backend states: unavailable / permission-required / pending / granted / service-binding / ready / lost plus Root-ready.
- [x] Unit-test the pure readiness-state transition model.
- [~] App scan is gated on actual backend readiness, not permission alone.
- [x] `waiting/binding` is no longer mapped to generic running-app failure.
- [ ] Android instrumentation/live probes for binder-late, deny→grant, Activity recreation, service death and Shizuku restart.

## Phase 3 — Transactional backup / restore

- [x] Validate all imported preset/config data before first durable write.
- [x] 2 MiB import-size guard and future-version rejection.
- [x] Snapshot main preferences and both preset stores before commit.
- [x] Synchronous commit across participating stores with rollback on partial failure.
- [x] Runtime reconciliation only after durable success.
- [x] Active-preset/base-snapshot state treated as runtime state rather than portable configuration.
- [x] Full preset section removes stale local presets missing from imported backup.
- [~] Add dedicated fault-injection/round-trip Android tests for rollback and runtime reconciliation.

## Phase 4 — Persistence / Room / platform backup

- [x] Explicitly exclude Android cloud backup and device-transfer restore; ReAppzuku's versioned backup remains the configuration contract.
- [x] Room schema export configured and current schema 11 generation verified in CI.
- [x] `MigrationTestHelper` instrumentation test for supported historical schema v2 → v11 compiles.
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
- [ ] Migrate build tooling to an API-37-capable AGP/Gradle combination while keeping `targetSdk 36`.
- [ ] Raise `compileSdk` to 37 and validate before changing target behavior.
- [ ] Raise `targetSdk` to 37 only after target-37 behavior probes pass.

### Android 17 runtime evidence — 2026-09-04

- Run `33699862420`: both app and instrumentation APKs installed successfully on API 37 and `USE_NEW_MESSAGEQUEUE` was enabled; instrumentation then exposed a lane bug because user 0 had not yet reached `RUNNING_UNLOCKED`.
- The lane was corrected to require `RUNNING_UNLOCKED`, a ready PackageManager service, 8 GiB `/data`, stable-channel emulator binaries, explicit runner libraries, and build-before-emulator resource separation.
- Run `33812905493`: API-37 image setup, app/androidTest build, stable-emulator boot, API=37 verification, `RUNNING_UNLOCKED`, PackageManager readiness and free-space checks all passed. Three non-streaming app installation attempts each pushed the 19,343,120-byte APK successfully, then failed only at the PackageManager transaction with `Failure calling service package: Broken pipe (32)`.
- No APK validation/signature/parse/`INSTALL_FAILED_*` error was observed. Runtime compatibility therefore remains `RISK/BLOCKED`, not failed and not `PROVEN`. No further Actions retries should be spent on the same preview image.
- The temporary runtime workflow was removed from the release path; normal read-only Validate -> write-only Publish CI was restored in `031a8634db725bb93185d3e55819dc8b5165e96d`.

## Phase 8 — UX, i18n, maintainability

- [x] Propagate fork-specific Smart Lifecycle/App Behavior/security strings to supported locales.
- [ ] Explain blocking automation directly beside disabled App Behavior controls.
- [ ] Split large managers behind testable facades (`PrivilegedShell`, `AlarmScheduler`, `ProtectionPolicy`, `BackupCodec`, `Clock`).
- [ ] Keep `CHECK_MATRIX.md` status/evidence current after every high-impact change.

## Stable-release gate

A candidate is not stable until all P0 findings are `PROVEN`, no P1 is unowned, reboot/permission/migration paths have repeatable Android evidence, update provenance is correct, exported privileged boundaries are reviewed, and release artifact/signing identity + rollback path are recorded.
