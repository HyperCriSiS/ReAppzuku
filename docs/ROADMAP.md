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
- [ ] Review remaining `exported=true` components individually (launcher, Accessibility, QS tiles, boot receiver, widget, Shizuku provider).
- [ ] Introduce typed/validated privileged operations instead of ad-hoc shell command strings where practical.
- [ ] Fixture-test dumpsys/parser protection logic across Android/OEM variants.

## Phase 6 — CI and supply chain

- [x] Preserve `concurrency.cancel-in-progress` for the live-test workflow.
- [x] Pin `actions/checkout` and `actions/setup-java` to immutable full commit SHAs.
- [x] Validation workflow is source-authoritative; no patch/generator step remains after migrations complete.
- [ ] Split read-only validation from release publishing with least-privilege tokens.
- [ ] Run lint without `updateLintBaseline`; baseline changes only through reviewed source commits.
- [ ] Pin every external Action in all other repository workflows.
- [ ] Add dependency verification/locking evaluation.
- [ ] Expand emulator/device test lanes without wasting private Actions quota.

## Phase 7 — Android 17 / API 37

- [ ] Add Android 17/API 37 runtime compatibility lane before changing target SDK.
- [ ] Plan compatible AGP/toolchain migration separately.
- [ ] Re-test hidden APIs, Accessibility, FGS, WorkManager, alarm behavior and process/memory assumptions.

## Phase 8 — UX, i18n, maintainability

- [ ] Propagate fork-specific Smart Lifecycle/App Behavior/security strings to supported locales.
- [ ] Explain blocking automation directly beside disabled App Behavior controls.
- [ ] Split large managers behind testable facades (`PrivilegedShell`, `AlarmScheduler`, `ProtectionPolicy`, `BackupCodec`, `Clock`).
- [ ] Keep `CHECK_MATRIX.md` status/evidence current after every high-impact change.

## Stable-release gate

A candidate is not stable until all P0 findings are `PROVEN`, no P1 is unowned, reboot/permission/migration paths have repeatable Android evidence, update provenance is correct, exported privileged boundaries are reviewed, and release artifact/signing identity + rollback path are recorded.
