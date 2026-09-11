#!/usr/bin/env python3
from pathlib import Path


def write(path, content):
    p = Path(path); p.parent.mkdir(parents=True, exist_ok=True); p.write_text(content, encoding="utf-8")

write("docs/ROADMAP.md", '''# ReAppzuku Assurance Roadmap

Source: `CHECK_MATRIX.md`, `QUALITY_GATES.md`, Red-Team review and live-device feedback.

Status: `[x]` implemented, `[~]` implemented but live evidence pending, `[ ]` open.

## Phase 1 — Live hardening baseline

- [~] Central desired-state rule: intentional Auto-Kill disable must beat service self-restart.
- [~] Fork-owned update provenance (`HyperCriSiS/ReAppzuku`); ignore non-version test tags.
- [~] Restore preset alarms/state after normal boot.
- [~] Central exact-alarm capability with safe best-effort fallback when permission is unavailable.
- [~] Keep only `SCHEDULE_EXACT_ALARM`; remove redundant `USE_EXACT_ALARM`.
- [~] Remove obsolete locked-boot path for credential-protected configuration.
- [~] Remove destructive Room migration fallback; missing migration must fail visibly rather than erase logs/statistics.
- [~] Backup v5: include Smart Lifecycle and App Behavior settings, reject future formats, validate presets before main commit.
- [~] Accessibility config: correct settings activity and remove unnecessary view-tree flag.
- [~] First pure JVM policy/version tests.
- [~] Checked-in source becomes authoritative; normal CI no longer patches/commits application source.

### Live test checklist for Phase 1

1. Fresh install / Shizuku permission removed: app waits for permission + UserService readiness and then loads apps.
2. Grant Shizuku slowly; rotate/background during dialog; no `Failed to get running Apps output` race.
3. Enable Auto-Kill, then disable it and wait >10 s: foreground service must stay off.
4. Enable Auto-Kill, kill ReAppzuku service/process externally: recovery may restart it while desired state remains enabled.
5. Reboot with Auto-Kill disabled: no persistent foreground service should appear.
6. Reboot with an enabled timed preset: preset alarms are rebuilt and the currently active time window is reconciled.
7. Deny/remove exact-alarm access: preset/scheduler must not crash; timing degrades to best-effort.
8. Backup/restore: Exit-on-Back, Prevent Shizuku Auto-Start and Smart Lifecycle settings round-trip.
9. Update check must never navigate to or install from the upstream `gree1d/ReAppzuku` channel.

## Phase 2 — Privilege state machine PROVEN

- [ ] Extract explicit backend states: unavailable / permission-pending / granted / service-binding / ready / lost.
- [ ] Unit-test state transitions and concurrent callers.
- [ ] Instrumentation probes for binder-late, deny→grant, activity recreation, service death and Shizuku restart.
- [ ] Never map `waiting` to a generic shell failure in UI/logs.

## Phase 3 — Persistence and migration

- [ ] Make backup restore fully transactional across main prefs + preset prefs with rollback/staging.
- [ ] Commit every Room schema 1…current and add MigrationTestHelper coverage.
- [ ] Define Android Auto Backup/data-extraction policy versus ReAppzuku's explicit backup format.
- [ ] Add corrupt/legacy/future/oversized-backup tests.

## Phase 4 — CI and supply chain

- [ ] Split read-only validation from release publishing with least-privilege tokens.
- [ ] Run lint without `updateLintBaseline`; baseline changes only through reviewed source commits.
- [ ] Pin external GitHub Actions to immutable full SHAs.
- [ ] Add dependency verification/locking evaluation.
- [ ] Expand unit/instrumentation test lanes and preserve `concurrency.cancel-in-progress`.

## Phase 5 — Privileged surface hardening

- [ ] Review every `exported=true` component and caller/principal boundary.
- [ ] Separate public shortcut routing from privileged executor.
- [ ] Introduce typed/validated privileged operations instead of ad-hoc command strings where practical.
- [ ] Fixture-test dumpsys/parser protection logic across Android/OEM variants.

## Phase 6 — Android 17 / API 37

- [ ] Add Android 17/API 37 runtime compatibility lane before changing target SDK.
- [ ] Plan compatible AGP/toolchain migration separately.
- [ ] Re-test hidden APIs, Accessibility, FGS, WorkManager, alarm behavior and process/memory assumptions.

## Phase 7 — UX, i18n, maintainability

- [ ] Propagate fork-specific Smart Lifecycle/App Behavior strings to supported locales.
- [ ] Explain blocking automation directly beside disabled App Behavior controls.
- [ ] Split large managers behind testable facades (`PrivilegedShell`, `AlarmScheduler`, `ProtectionPolicy`, `BackupCodec`, `Clock`).
- [ ] Keep CHECK_MATRIX status/evidence current after every high-impact change.

## Stable-release gate

A candidate is not stable until all P0 findings are `PROVEN`, no P1 is unowned, reboot/permission/migration paths have repeatable evidence, update provenance is correct, and the release artifact/signing identity + rollback path are recorded.
''')

# After this one-time migration, CI becomes source-authoritative: no patch scripts or git push.
write(".github/workflows/ondemand-test-build.yml", '''name: On-demand Shizuku test APK

on:
  push:
    branches:
      - ondemand-shizuku
  workflow_dispatch:

concurrency:
  group: ondemand-shizuku-test
  cancel-in-progress: true

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - name: Set up JDK 17
        uses: actions/setup-java@v5
        with:
          java-version: '17'
          distribution: temurin
          cache: gradle
      - name: Grant execute permission
        run: chmod +x gradlew
      - name: Unit tests
        run: ./gradlew testDebugUnitTest
      - name: Build debug APK
        run: ./gradlew assembleDebug
      - name: Prepare APK
        run: cp app/build/outputs/apk/debug/app-debug.apk ReAppzuku-1.8.7-ondemand-debug.apk
      - name: Publish test APK
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          sha256sum ReAppzuku-1.8.7-ondemand-debug.apk | tee ReAppzuku-1.8.7-ondemand-debug.apk.sha256
          gh release delete ondemand-test --cleanup-tag -y || true
          gh release create ondemand-test \
            ReAppzuku-1.8.7-ondemand-debug.apk \
            ReAppzuku-1.8.7-ondemand-debug.apk.sha256 \
            --target "${GITHUB_SHA}" \
            --title "ReAppzuku 1.8.7 Assurance Phase 1 test" \
            --notes "Live-test build: desired-service-state fix, fork-owned update channel, preset reboot recovery, exact-alarm fallback, backup v5, non-destructive Room migration policy, accessibility cleanup, and initial unit-test gates."
''')
print("assurance-phase1-assets applied")
