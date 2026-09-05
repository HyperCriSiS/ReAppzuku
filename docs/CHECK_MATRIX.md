# ReAppzuku Architecture & Control Check Matrix

> Adapted from the Voice-platform Architecture Control Matrix.
>
> Audit baseline: `ondemand-shizuku`, refreshed 2026-09-05.

## Purpose

This matrix is not a feature checklist. A feature is not considered complete because it worked once.
A cell closes only when the intended invariant is explicit, important failure modes are covered,
recovery behavior is defined, and repeatable evidence exists.

### Status vocabulary

- **OPEN** — not yet reviewed or not specified.
- **RISK** — a concrete weakness, unresolved failure mode, or missing evidence exists.
- **DECIDED** — architecture/behavior is explicit, but repeatable evidence is still incomplete.
- **PROVEN** — invariant is implemented and backed by repeatable evidence.
- **N/A** — lens does not apply.

Only **PROVEN** is fully closed.

## Axis A — ReAppzuku system surfaces

| ID | Surface | Current status | Main reason |
|---|---|---|---|
| A01 | Privilege bootstrap: Root / Shizuku / permission | RISK | State machine improved, but first-run/rebind/process-death paths have no automated evidence. |
| A02 | Main-process / `:shizuku` process topology | DECIDED | On-demand topology is explicit; process-start invariants are not regression-tested. |
| A03 | Privileged shell / Shizuku UserService | RISK | Arbitrary shell transport is powerful; readiness, death/rebind and command safety need tests/facades. |
| A04 | Running-app discovery / app-state collection | DECIDED | ActivityManager process/service text parsing is isolated and JVM fixture-tested across AOSP/OEM-style forms; Android runtime/platform evidence remains open. |
| A05 | Manual restrictions / freeze / force-stop / app ops | RISK | High-impact privileged actions lack typed command boundary and validation tests. |
| A06 | AutoKill / `ShappkyService` / periodic worker | DECIDED | Desired-state guard prevents an intentional disable from being undone; live process-death evidence remains pending. |
| A07 | Smart Lifecycle | DECIDED | Conservative blacklist/protection design exists; state/false-positive/reboot evidence missing. |
| A08 | Sleep / freeze lifecycle | RISK | Interacts with FGS, alarms, screen state and restoration without state-machine tests. |
| A09 | Presets / Restrictions Scheduler / exact alarms | DECIDED | Boot recovery and central exact-alarm capability are implemented; reboot/permission-revocation runtime evidence remains pending. |
| A10 | Accessibility / app-launch tracking | DECIDED | Service configuration and unnecessary view-tree scope were corrected; Android runtime evidence remains pending. |
| A11 | Boot / process death / restart / recovery | **RISK-P0** | Multiple independent recovery paths can contradict user intent; reboot restoration is incomplete. |
| A12 | Settings / App Behavior / compatibility interlocks | DECIDED | Central policy now exists; truth table needs exhaustive tests. |
| A13 | Backup / restore | DECIDED | Validate-first transactional restore, future-version rejection and fork settings are implemented; Android fault-injection evidence remains pending. |
| A14 | Room DB / statistics / logs | DECIDED | Destructive fallback was removed and v2→v11 migration instrumentation compiles; API-37 execution and schema-history evidence remain open. |
| A15 | Update channel / release / rollback | DECIDED | Update provenance is fork-owned and release artifact digest is recorded; signing/rollback evidence remains incomplete. |
| A16 | Exported surfaces: shortcuts / tiles / receivers / widget | DECIDED | Shortcut confused-deputy path is hardened and all exported principals are documented; repeatable abuse probes remain pending. |
| A17 | UI / error recovery / accessibility / i18n | DECIDED | Fork feature translations and lint-critical accessibility fixes are complete; broader runtime/UX evidence remains pending. |
| A18 | Build / CI / dependencies / supply chain | DECIDED | Source-authoritative least-privilege CI, immutable Action pins, zero-error lint, dependency locking and SHA-256 verification are enforced; broader emulator/runtime lanes remain open. |

## Axis B — independent lenses

The following lenses are applied to every relevant surface:

- **L01** functional correctness
- **L02** state / lifecycle
- **L03** concurrency / races
- **L04** ordering / idempotency
- **L05** performance / latency
- **L06** memory / resource pressure
- **L07** power / thermal
- **L08** availability / recovery
- **L09** security / STRIDE
- **L10** privacy / data minimization
- **L11** supply chain / untrusted input
- **L12** storage semantics
- **L13** Android / platform policy
- **L14** compatibility / interoperability
- **L15** evolution / modifiability
- **L16** backward compatibility / migration
- **L17** observability / truthfulness
- **L18** testability / determinism
- **L19** usability / error recovery
- **L20** accessibility / input diversity
- **L21** internationalization / language
- **L22** ML / model behavior — N/A
- **L23** safety / control interaction (STPA)
- **L24** licensing / compliance
- **L25** release / rollback
- **L26** abuse / misconfiguration

## Coverage summary by surface

`P` = PROVEN, `D` = DECIDED, `R` = RISK, `-` = N/A/not material.
The table intentionally stays conservative: a successful APK build alone is not runtime proof.

| Surface | L01 | L02 | L03 | L04 | L08 | L09 | L11 | L12 | L13 | L15 | L16 | L17 | L18 | L19 | L21 | L23 | L25 | L26 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| A01 Privilege bootstrap | D | R | R | R | R | D | - | - | R | D | R | D | **R** | R | - | R | - | R |
| A02 Process topology | D | D | R | R | R | D | - | - | R | D | R | D | **R** | D | - | R | - | R |
| A03 Shell/UserService | D | R | R | R | R | **R** | - | - | R | **R** | R | D | **R** | R | - | R | - | **R** |
| A04 App discovery | D | R | R | R | R | D | - | - | R | R | R | R | **R** | R | - | R | - | R |
| A05 Privileged actions | D | R | R | R | R | **R** | - | - | R | R | R | D | **R** | R | - | **R** | - | **R** |
| A06 AutoKill/service | **R** | **R** | R | **R** | **R** | D | - | - | R | R | R | D | **R** | R | - | **R** | R | **R** |
| A07 Smart Lifecycle | D | D | R | R | D | D | - | - | R | D | R | D | **R** | D | - | R | R | R |
| A08 Sleep/freeze | D | R | R | R | R | D | - | - | R | R | R | D | **R** | R | - | **R** | R | R |
| A09 Presets/scheduler | **R** | **R** | R | R | **R** | D | - | D | **R** | R | R | D | **R** | R | - | **R** | R | R |
| A10 Accessibility | D | R | R | D | D | **R** | - | - | **R** | D | R | D | R | **R** | R | R | - | **R** |
| A11 Boot/recovery | **R** | **R** | R | **R** | **R** | D | - | D | R | R | R | D | **R** | R | - | **R** | R | R |
| A12 App Behavior policy | D | D | R | D | D | D | - | D | D | D | R | D | **R** | D | R | **R** | R | R |
| A13 Backup/restore | **R** | R | R | **R** | R | R | **R** | **R** | D | R | **R** | R | **R** | R | R | **R** | **R** | **R** |
| A14 Room DB | D | R | R | D | R | D | - | **R** | D | D | **R** | R | **R** | R | - | R | **R** | R |
| A15 Update/release | **R** | R | R | R | R | R | **R** | D | D | R | **R** | R | **R** | R | R | R | **R** | R |
| A16 Exported entrypoints | D | R | R | R | R | **R** | **R** | - | R | R | R | R | **R** | R | - | **R** | R | **R** |
| A17 UI/i18n | D | R | R | D | D | D | - | D | D | R | R | R | R | **R** | **R** | R | R | R |
| A18 Build/supply chain | D | - | R | R | R | **R** | **R** | - | R | **R** | **R** | R | **R** | R | R | R | **R** | R |

No runtime surface is currently marked fully PROVEN. That is intentional until repeatable evidence exists.

---


## Evidence/status refresh — 2026-09-05

The finding narratives below are retained as audit provenance. This refresh supersedes their
historical “current” wording where implementation has moved on.

- **CM-P0-01:** implementation fixed. Desired-state guards prevent intentional Auto-Kill disable
  from being undone; live process-death/recovery evidence is still required.
- **CM-P0-02:** implementation fixed. Update provenance points to `HyperCriSiS/ReAppzuku`; the
  2026-09-02 assurance release was published from the fork with an explicit SHA-256.
- **CM-P0-03:** state model/readiness gating improved and JVM-tested; Android binder-late,
  deny→grant, service-death and Shizuku-restart probes remain open.
- **CM-P1-01 / CM-P1-02:** boot preset recovery and one central exact-alarm capability are
  implemented; device reboot and permission-revocation evidence remains open.
- **CM-P1-03:** restore is validate-first, bounded, future-version aware and transactional with
  rollback; dedicated Android fault injection remains open.
- **CM-P1-04:** destructive Room fallback is removed. v2→v11 `MigrationTestHelper` instrumentation
  compiles and is scheduled for the API-37 runtime lane.
- **CM-P1-05 / 06 / 07:** normal CI is source-authoritative, immutable-SHA pinned and split into
  read-only validation and writable publishing. Lint must show zero errors before its reviewed
  warning baseline is accepted.
- **CM-P1-09:** accessibility service settings path/scope are corrected.
- **CM-P1-10:** exported shortcut privilege boundary is authenticated/confirmed and remaining
  exported principals are documented in `EXPORTED_COMPONENTS.md`.
- **CM-P1-11:** Android cloud/device-transfer backup is explicitly excluded; the versioned
  ReAppzuku backup is the configuration contract.
- **CM-P1-12:** a manual Android-17/API-37 runtime lane is staged before target migration; execution
  evidence is the next roadmap step.
- **CM-P2-02:** package identifiers imported from backups/presets and high-impact shell boundaries
  now pass a shared package-name validator; typed privileged operations remain a longer-term goal.
- **CM-P2-03:** ActivityManager `ProcessRecord`/`ServiceRecord` parsing is isolated in a pure-Java parser with AOSP/OEM-style fixtures, and protected-package tests lock exact package boundaries. Other Smart Lifecycle/VPN heuristics still require runtime/fixture evidence.
- **CM-P1-06 / A18:** Gradle dependency locking and SHA-256 verification are now enforced; run `33900939628` re-proved verification from an empty dependency cache and normal run `33901414025` passed afterward.
- **CM-P2-04:** fork-specific Smart Lifecycle, App Behavior, shortcut-security and accessibility
  strings are propagated to ES/RU/UK/ZH.

Latest assurance evidence:
- workflow run `33940487030`: parser/protection JVM fixtures, lint, AndroidTest compilation and debug APK build passed before production integration commit `57d8897c70a5b4c9565379cd72317f1abb7c9e59`;
- workflow run `33900939628`: dependency verification re-generated from fresh resolution and re-proved after dependency-cache deletion;
- workflow run `33901414025`: normal permanent locked/verified validation passed;
- earlier release target: `e202e38c049a0d4a7cfc561f7a9c8348c9abd8ae`;
- earlier APK SHA-256: `d841e34685d790197266c1e9c90a11619a33a219377892f2c429c00930dbf5d4`.

# High-priority findings

## P0 — correctness / user intent

### CM-P0-01 — Intentional stop can be undone by service self-restart

**Surface:** A06/A11  
**Lenses:** L01, L02, L04, L08, L23, L26

`ShappkyService.onDestroy()` unconditionally schedules `RestartReceiver` after three seconds.
The receiver restarts the service whenever `isRunning()` is false, without re-checking whether
AutoKill/background service is still enabled.

**Hazard:** user disables background behavior -> service stops -> `onDestroy()` schedules restart ->
service returns despite explicit user intent.

**Required invariant:** an intentional disable must dominate crash-recovery logic.

**Fix direction:**
1. restart only if the persisted desired state still requires the service;
2. re-check desired state inside `RestartReceiver`;
3. cancel a pending restart when the user disables the service;
4. distinguish intentional shutdown from unexpected death where practical.

**Required evidence:** state-machine test for enabled→disable→destroy; process-kill recovery probe.

### CM-P0-02 — Update checker points to upstream, not the fork

**Surface:** A15  
**Lenses:** L01, L11, L16, L25, L26

`UpdateChecker` currently uses:

- `https://api.github.com/repos/gree1d/ReAppzuku/releases/latest`
- `https://github.com/gree1d/ReAppzuku/releases`

**Hazard:** the customized fork can recommend an upstream APK that removes the fork's behavior,
Smart Lifecycle and On-demand Shizuku changes.

**Required invariant:** update provenance must match the installed distribution channel.

**Fix direction:** make update channel a build-time constant owned by the fork; verify package/signing
identity and expose release channel/version provenance to the user.

### CM-P0-03 — No regression evidence for the Shizuku permission/UserService state machine

**Surface:** A01/A02/A03/A04  
**Lenses:** L02, L03, L04, L08, L18

The recent first-run bug was caused by treating `PERMISSION_GRANTED` and `UserService ready` as the
same state. The code is now improved, but the same class of regression can return.

**Required invariant:** no privileged operation runs until the selected backend is ready.

**Required evidence matrix:**
- Shizuku absent;
- binder arrives late;
- permission denied;
- permission pending;
- permission granted, service bind delayed;
- service dies during command;
- Shizuku restarts while app is open;
- activity stops/recreates during permission dialog;
- root backend available / unavailable.

---

# P1 findings

### CM-P1-01 — Preset alarms are not restored after reboot

`BootReceiver` restores Restrictions Scheduler and Smart Lifecycle, but does not rebuild
`PresetManager` AlarmManager entries. Exact alarms do not survive reboot.

**Fix direction:** after normal boot, reload enabled presets, validate exact-alarm capability,
schedule alarms and call `checkAndApplyCurrentPreset()`.

### CM-P1-02 — Exact-alarm permission model is inconsistent

The manifest declares both `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM`, while preset and
restriction scheduling call exact-alarm APIs without a central `canScheduleExactAlarms()` gate.

Android documents `USE_EXACT_ALARM` as restricted/core-functionality use and says only one of
`USE_EXACT_ALARM` or `SCHEDULE_EXACT_ALARM` should be requested on a device. ReAppzuku's exact
scheduling is an optional feature, so `SCHEDULE_EXACT_ALARM` is the safer default architecture.

**Fix direction:** keep one permission, add a central ExactAlarmCapability facade, recovery UI,
and reschedule on permission grant/revocation events.

### CM-P1-03 — Backup restore is non-transactional and incomplete

Current restore:
1. parses JSON;
2. writes main SharedPreferences with `apply()`;
3. restores presets afterward.

If preset restore fails, the method returns false after main state has already changed.

The backup format currently omits fork-owned durable settings including:
- `KEY_EXIT_ON_BACK`
- `KEY_PREVENT_SHIZUKU_AUTOSTART`
- `KEY_SMART_LIFECYCLE_ENABLED`
- `KEY_SMART_BOOT_CLEANUP_ENABLED`
- `KEY_SMART_LIFECYCLE_PROFILE`

The read version is logged but future/incompatible versions are not rejected.

**Required invariant:** restore is validate-first, commit-once, recoverable, versioned, and applies
runtime side effects only after durable state is consistent.

### CM-P1-04 — Room can silently destroy data

`AppDatabase` declares migrations but also calls `fallbackToDestructiveMigration()`.
If a migration path is missed, statistics/log data may be recreated rather than failing safely.

Only an old Room schema snapshot is currently committed while DB version is much newer.

**Fix direction:** remove destructive fallback for release, commit every schema, and add
`MigrationTestHelper` coverage for supported upgrade paths.

### CM-P1-05 — CI can hide lint regressions

The release workflow runs `./gradlew updateLintBaseline` before the release build.
That mutates the acceptance baseline rather than checking against it.

**Required invariant:** validation jobs never rewrite their own quality threshold.

**Fix direction:** baseline changes only in reviewed commits; CI runs lint and fails on new findings.

### CM-P1-06 — Test/build workflow mutates source

`ondemand-test-build.yml` runs patch scripts, commits generated source back to the branch and then
builds/releases from that modified state.

**Hazards:** source/binary provenance becomes harder to reason about; pushes trigger more builds;
validation requires write token; a patch script can accidentally overwrite human changes.

**Fix direction:** make checked-in source authoritative. CI is read-only for validation.
Publishing is a separate explicitly writable job.

### CM-P1-07 — GitHub Actions are tag-pinned instead of immutable-SHA pinned

Current workflows use references such as `actions/checkout@v6`, `actions/setup-java@v5`,
`actions/upload-artifact@v7`.

Voice-platform quality gates require external Actions to be pinned to immutable full commit SHAs.

### CM-P1-08 — No automated test tree

The repository currently has no meaningful `app/src/test` or `app/src/androidTest` suite despite
large stateful managers and privileged flows.

This is the main reason most matrix cells remain RISK/DECIDED rather than PROVEN.

### CM-P1-09 — Accessibility service configuration has a stale settings activity

`accessibility_service_config.xml` references:

`com.gree1d.reappzuku.SettingsActivity`

while the activity lives under:

`com.gree1d.reappzuku.ui.SettingsActivity`

It also requests `flagIncludeNotImportantViews`, although the current foreground-tracking use only
needs package/window-state information.

**Fix direction:** correct settings activity and remove unnecessary accessibility surface unless a
tested feature requires it.

### CM-P1-10 — Exported privileged shortcut surface needs principal review

`KillShortcutActivity` is exported and ultimately triggers privileged kill behavior.
That may be necessary for launcher shortcuts, but the current architecture does not make the
caller/principal boundary explicit.

**Fix direction:** split public user-entry surface from internal privileged executor and prove that
third-party explicit intents cannot cause unintended privileged actions.

### CM-P1-11 — Automatic Android backup is not explicitly reconciled with ReAppzuku backup

The manifest has `android:allowBackup="true"` while ReAppzuku also implements its own structured
backup/restore.

**Fix direction:** define which state may enter Android Auto Backup, exclude privileged/runtime
state explicitly with backup/data-extraction rules, or disable platform backup if the application
backup is the sole supported contract.

### CM-P1-12 — Android 17 / API 37 compatibility lane is missing

The project currently compiles/targets API 36 with AGP 8.10.0. Android 17 is API 37 and includes
behavior changes that can affect hidden/reflection-heavy and memory-sensitive code.

**Fix direction:** add an API 37 compatibility lane first. Moving compile/target to 37 requires an
AGP upgrade (official current minimum for API 37 is AGP 9.1.1), so treat that as a planned build
migration rather than a blind version bump.

---

# P2 / maintainability findings

### CM-P2-01 — High-complexity classes are too large to isolate safely

Examples include `BackgroundAppManager`, `SettingsActivityDialogs`, `MainActivity`,
`PresetSettingsActivity`, `LogDetailActivity`, `AutoKillManager` and `ShappkyService`.

**Direction:** extract stable contracts/facades rather than rewrite:
- `PrivilegedShell`
- `PackageStateSource`
- `AutomationDesiredState`
- `AlarmScheduler`
- `Clock`
- `ProtectionPolicy`
- `BackupCodec`

### CM-P2-02 — Shell command construction should be typed

Package names normally come from Android and are constrained, but privileged commands are often
constructed by string concatenation. High-privilege code should validate package identifiers and
prefer typed shell operations.

### CM-P2-03 — Smart Lifecycle / process protection parsing needs bounded contracts

ActivityManager `ProcessRecord`/`ServiceRecord` parsing is now isolated in `ProcessDumpParser`; JVM
fixtures cover AOSP and OEM-style records, remote processes, CRLF and exact package-name boundaries.
`ProtectedAppsTest` also prevents prefix-neighbor packages from being treated as protected.

Remaining Smart Lifecycle/VPN text heuristics still depend on platform output and therefore remain a
runtime/fixture evidence item rather than being marked globally PROVEN.

### CM-P2-04 — Fork-specific UI strings are not propagated to existing locales

Existing localized resources do not currently contain the new Smart Lifecycle / App Behavior
strings, so those surfaces fall back to the base language.

### CM-P2-05 — Debug SQL bind args can expose app-state metadata to logcat

Debug logging is opt-in, which is good, but query bind values should be treated as potentially
sensitive and redactable.

---

# Lifecycle phase matrix

Every high-impact feature must be tested in all applicable phases:

| Phase | Mandatory scenarios |
|---|---|
| Install / upgrade | fresh install; update from upstream; update from previous fork build; signature/channel mismatch |
| First run / permission | root absent; Shizuku absent; permission deny/grant; delayed binder/service |
| Steady operation | manual actions; Smart Lifecycle; AutoKill; presets; scheduler; UI refresh |
| Background maintenance | screen off/on; Doze; FGS; WorkManager retry; exact alarm denied |
| Process death | UI killed; service killed; Shizuku service dies; Android reclaims app |
| Reboot | boot locked/unlocked; alarms rebuilt; desired service state respected |
| Restore / migration | old backup; current backup; future backup; malformed backup; DB upgrade |
| Uninstall / reinstall | platform backup present/absent; stale Shizuku authorization; shortcut/widget cleanup |

# Fault / stimulus matrix

At minimum, each subsystem must consider:

1. **User/caller:** rapid toggles, repeated Back, conflicting features, malformed imported backup.
2. **Android/environment:** process death, Doze, reboot, permission revocation, exact-alarm denial.
3. **Privilege runtime:** Shizuku binder lost, service bind delayed, root command timeout.
4. **Storage:** corrupt SharedPreferences/JSON, DB migration gap, low storage.
5. **Automation/control:** preset + scheduler + Smart Lifecycle + AutoKill overlap.
6. **Adversarial:** explicit intents to exported entrypoints, tampered update metadata, untrusted backup.

# Evidence plan

## Unit / state-machine tests

1. `BackgroundWorkPolicyTest`
   - exhaustive truth table of AutoKill / Smart Lifecycle / Sleep / Preset / Scheduler
   - verify App Behavior toggles are forced off exactly when continuity is required.

2. `ShizukuReadinessStateMachineTest`
   - binder/permission/UserService permutations and service death/rebind.

3. `SmartLifecyclePolicyTest`
   - unmanaged/managed/protected/foreground/media/FGS/widget/VPN/device-policy decisions.

4. `BackupManagerTest`
   - round-trip; old/current/future version; malformed JSON; atomic failure; durable fork settings.

5. `RoomMigrationTest`
   - every supported historical schema to current.

6. `ServiceDesiredStateTest`
   - intentional disable never restarts service; unexpected death can recover when enabled.

7. `PresetBootRecoveryTest`
   - alarms and active-window state restored after reboot.

## Android / instrumentation probes

- first install + Shizuku permission
- permission denied then later granted
- Shizuku restarted while ReAppzuku is open
- process killed with automation enabled/disabled
- reboot with presets/scheduler/Smart Lifecycle
- exact-alarm permission revoked
- API 24, 31, 33, 35, 36 and Android 17/API 37 compatibility lane where practical
- at least one non-Pixel/OEM regression device for dumpsys/parser behavior

## Security / abuse probes

- explicit intent into exported shortcut activity
- backup with unexpected package identifiers / oversized arrays
- update metadata with wrong asset/package/signing provenance
- shell timeout and command cancellation
- accessibility service disabled/re-enabled while Smart Lifecycle is active

# Exit criteria

A release candidate is not matrix-clean until:

- all P0 findings are fixed and PROVEN;
- no P1 finding is unowned;
- required migrations are non-destructive and tested;
- permission/process/reboot state machines have repeatable evidence;
- CI validates without rewriting source or lint baseline;
- update provenance points to the correct distribution channel;
- release artifact digest and signing identity are recorded;
- rollback path is documented and tested.
