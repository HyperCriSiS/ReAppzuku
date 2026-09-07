# ReAppzuku Architecture & Control Check Matrix

> Adapted from the Voice-platform Architecture Control Matrix.
>
> Audit baseline: `ondemand-shizuku`, refreshed 2026-09-07.

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
| A01 | Privilege bootstrap: Root / Shizuku / permission | DECIDED | Deterministic API-36 bridge tests cover state permutations, while run `34000092714` proves a fresh ungranted app against the official Shizuku server/manager waits for the real dialog and reaches READY only after grant; physical/OEM and root remain separate diversity paths. |
| A02 | Main-process / `:shizuku` process topology | DECIDED | On-demand topology is explicit and guarded; run `34057708486` proves provider-only operation keeps the main process/`ShappkyService` absent, while opt-in auto-wake starts the main process after a real Shizuku restart. OEM/process-management diversity remains open. |
| A03 | Privileged shell / Shizuku UserService | DECIDED | Readiness/death/rebind state is instrumented, queued service actions wait for a ready backend (`69750c7`, gate `34065892554`), and run `33997372602` proves real official-Shizuku UserService execution plus daemon-death/rebind recovery; root-specific execution remains open. |
| A04 | Running-app discovery / app-state collection | DECIDED | ActivityManager process/service parsing is isolated and JVM fixture-tested across AOSP/OEM-style forms; real official-Shizuku API-36 `ProcessRecord` parsing is proven (`API36_PROCESS_RECORD_PARSED` in `34153570390`), while deterministic live `ServiceRecord` creation is blocked by Android 16 background-service policy in the synthetic harness; OEM/physical runtime remains open. |
| A05 | Manual restrictions / freeze / force-stop / app ops | DECIDED | High-impact mutating operations use typed/validated `PrivilegedShell`; run `34005682619` proves representative real Shizuku command families and rollback behavior on a disposable target. Root-specific execution and deliberately destructive families remain separate evidence. |
| A06 | AutoKill / `ShappkyService` / periodic worker | DECIDED | API-36 instrumentation proves a fresh restart receiver obeys persisted disabled state and real reboot recovery succeeds; focused run `34004843634` additionally proves persisted disabled desired state across a real external app force-stop/process restart with PID change. |
| A07 | Smart Lifecycle | DECIDED | Recovery policy, boot-cleanup retry, failed-force-stop state preservation, exact package matching, dump protection and foreground/process parsing now have focused tests; API-36 repeated boot reconciliation is also proven. Physical/OEM process-output and false-positive diversity remain open. |
| A08 | Sleep / freeze lifecycle | DECIDED | Source/state coverage is backed by API-36 run `34075290320`, which proves real owned freeze, durable ownership across external app process death, and owned thaw/cleanup after wake/restart. Physical/OEM/Doze diversity remains open. |
| A09 | Presets / Restrictions Scheduler / exact alarms | DECIDED | Exact-alarm denial fallback, repeated boot WorkManager reconciliation and real API-36 OS reboot with scheduler/preset alarm reconstruction pass; physical/OEM variation remains release-diversity evidence. |
| A10 | Accessibility / app-launch tracking | DECIDED | Service configuration and unnecessary view-tree scope were corrected; `AppLaunchTriggerPolicy` JVM-tests exact target eligibility/5-second duplicate suppression (`34153818522`), and `AccessibilityServicePolicyTest` locks the minimal `TYPE_WINDOW_STATE_CHANGED`-only service contract (`34156391150`). Android accessibility runtime evidence remains pending. |
| A11 | Boot / process death / restart / recovery | DECIDED | Real API-36 OS reboot recovery, Shizuku daemon death/rebind, AutoKill desired-state recovery and Sleep owned-freeze recovery across external process death all pass; physical/OEM diversity remains release evidence rather than an implementation P0. |
| A12 | Settings / App Behavior / compatibility interlocks | DECIDED | Central `BackgroundWorkPolicy` owns compatibility; run `34057507889` exhaustively proves all 32 continuity-blocker masks against all requested Exit-on-Back / prevent-Shizuku-autostart combinations. Runtime UI diversity remains separate. |
| A13 | Backup / restore | DECIDED | API-36 instrumentation passes transactional rollback fault injection, legacy/future/malformed bounds and active-preset reconciliation, while package collections are capped, preset times/package lists are validated and standalone preset reads reuse the bounded backup reader. `34000092714` passes a real MediaStore `content://` round-trip; physical/OEM provider UI remains release-diversity evidence. |
| A14 | Room DB / statistics / logs | DECIDED | Supported v2→v11 migration executes successfully on API 36 with `app_stats` preservation and final-schema validation; SQL debug bind values are redacted (`34065691224`). Unavailable upstream schema history 1/3–10 cannot be fabricated. |
| A15 | Update channel / release / rollback | DECIDED | Fork-owned update resolution enumerates stable numeric releases, direct APK/release links are derived only from validated fork metadata, and the release workflow binds stable tag ↔ source `versionName` ↔ built APK `versionName`. Run `34037508198` proves installed-byte identity and fork-only endpoints. Stable signing/rollback identity remains incomplete. |
| A16 | Exported surfaces: shortcuts / tiles / receivers / widget | DECIDED | Shortcut confused-deputy routing now has an explicit principal policy (`f3145bb0`, `34154664082`), the explicit-intent abuse step passed in `33986395874`, and manifest/documentation parity plus platform permissions are regression-tested (`34138775145`). `ManifestCapabilityPolicyTest` additionally prevents silent package-visibility/usage-stats/Leanback capability drift (`677cd2ac`, `34163432363`); broader entrypoint runtime abuse coverage remains separate. |
| A17 | UI / error recovery / accessibility / i18n | DECIDED | Fork translations/accessibility fixes are covered, `NavigationManifestPolicyTest` prevents recursive activity-parent routing, and the accidental manifest rollback exposed while fixing `LogDetailActivity` was restored to the last-green capability set while preserving the correct parent (`f426b55c`, `34163189731`). Broader runtime/UX evidence remains pending. |
| A18 | Build / CI / dependencies / supply chain | DECIDED | Source-authoritative least-privilege CI, immutable Action pins, zero-error lint, dependency locking/SHA-256 verification and stable tag/source/APK version binding are enforced. `main` and `ondemand-shizuku` share identical permanent validation/release workflows; API-37 preview execution remains blocked. |

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
| A02 Process topology | D | D | R | R | R | D | - | - | R | D | R | D | **P** | D | - | R | - | R |
| A03 Shell/UserService | D | R | R | R | R | **R** | - | - | R | **R** | R | D | **P** | R | - | R | - | **R** |
| A04 App discovery | D | R | R | R | R | D | - | - | R | R | R | R | **D** | R | - | R | - | R |
| A05 Privileged actions | D | R | R | R | R | **R** | - | - | R | R | R | D | **P** | R | - | **R** | - | **R** |
| A06 AutoKill/service | **R** | **R** | R | **P** | **R** | D | - | - | R | R | R | D | **P** | R | - | **R** | R | **R** |
| A07 Smart Lifecycle | D | D | R | R | D | D | - | - | R | D | R | D | **P** | D | - | R | R | R |
| A08 Sleep/freeze | D | **P** | R | **P** | R | D | - | - | R | R | R | D | **P** | R | - | **R** | R | R |
| A09 Presets/scheduler | **R** | **R** | R | **P** | **R** | D | - | D | **P** | R | R | D | **P** | R | - | **R** | R | R |
| A10 Accessibility | D | R | R | D | D | **R** | - | - | **R** | D | R | D | **P** | **R** | R | R | - | **R** |
| A11 Boot/recovery | **R** | **R** | R | **P** | **R** | D | - | D | R | R | R | D | **P** | R | - | **R** | R | R |
| A12 App Behavior policy | D | D | R | D | D | D | - | D | D | D | R | D | **P** | D | R | **R** | R | R |
| A13 Backup/restore | **R** | R | R | **P** | R | D | **P** | **P** | D | R | **P** | R | **P** | R | R | **R** | **R** | **D** |
| A14 Room DB | D | R | R | D | R | D | - | **P** | D | D | **P** | R | **P** | R | - | R | **R** | R |
| A15 Update/release | D | R | R | R | R | D | **P** | D | D | D | D | R | **P** | R | R | R | **R** | D |
| A16 Exported entrypoints | D | R | R | R | R | D | D | - | D | D | R | R | **P** | R | - | D | R | D |
| A17 UI/i18n | D | R | R | D | D | D | - | D | D | D | R | R | **P** | D | **R** | R | R | R |
| A18 Build/supply chain | D | - | R | R | R | D | D | - | D | D | D | R | **P** | R | R | R | **R** | D |

No runtime surface is currently marked fully PROVEN. That is intentional until repeatable evidence exists.

---


## Evidence/status refresh — 2026-09-07

The finding narratives below are retained as audit provenance. This refresh supersedes their
historical “current” wording where implementation has moved on.

- **CM-P0-01:** implementation fixed at source/instrumentation level. API-36 instrumentation proves a newly constructed
  restart receiver re-reads persisted desired state and cannot undo an intentional Auto-Kill disable;
  real reboot recovery passes, and focused run `34004843634` closes the external OS force-stop/process-restart desired-state gap with a verified PID change.
- **CM-P0-02:** implementation fixed and installed-binary provenance proven. Update resolution points to `HyperCriSiS/ReAppzuku`, enumerates releases instead of trusting `/releases/latest`, skips draft/prerelease/non-numeric rolling tags, and treats “no stable release” as no update. Run `34037508198` installed the current test APK on API 36, pulled it back with exact SHA-256 identity (`b57c694dc75705f23f5de0b7ef7988811fb350276aa11063b4077af224089dd8`) and verified the expected fork endpoints across all DEX files with no upstream or obsolete `/releases/latest` endpoint.
- **CM-P0-03:** state model/readiness gating is API-36 instrumented for binder-late, deny→grant,
  listener removal followed by grant, and binder-death→return/rebind. Run `34000092714` proves the real
  official-Shizuku first-run permission flow, `33997372602` proves daemon death/restart and same-process rebind, and `34036827312` proves Activity recreation behind the still-open real permission dialog followed by grant/READY/scan recovery. Root remains a separate diversity path.
- **CM-P1-01 / CM-P1-02:** boot preset recovery and one central exact-alarm capability are
  implemented. API-36 runtime proves denied-exact-alarm best-effort fallback plus repeated
  BootReceiver WorkManager idempotency, and run `33985971887` proves real OS reboot recovery with scheduler/preset alarm reconstruction; physical/OEM variation remains release-diversity evidence.
- **CM-P1-03:** restore is validate-first, bounded, future-version aware and transactional with
  rollback. API-36 instrumentation passes injected durable failures and imported active-preset reconciliation,
  and run `34000092714` passes a real Android MediaStore `content://` export/import/restore round-trip. Physical/OEM document-provider UI remains release-diversity evidence.
- **CM-P1-04:** destructive Room fallback is removed. v2→v11 `MigrationTestHelper` now executes
  successfully on API 36, preserving existing `app_stats` rows and validating the final schema.
  Historical schemas 1 and 3–10 were never preserved upstream and are intentionally not fabricated.
- **CM-P1-05 / 06 / 07:** normal CI is source-authoritative, immutable-SHA pinned and split into
  read-only validation and writable publishing. Lint must show zero errors before its reviewed
  warning baseline is accepted.
- **CM-P1-08:** the original “no automated test tree” finding is closed. The branch now has broad JVM policy/parser/security coverage plus Android instrumentation for restore, migration, boot, permission, process topology, Shizuku death/rebind, process death, shortcut abuse and real privileged commands. Individual runtime-diversity gaps remain tracked by their matrix surfaces rather than by a blanket lack-of-tests finding.
- **CM-P1-09:** accessibility service settings path/scope are corrected; `AccessibilityServicePolicyTest` locks the minimal window-state-only contract and normal run `34156391150` passed.
- **CM-P1-10:** exported shortcut privilege routing is now an explicit `ShortcutEntryPolicy`; authenticated secure actions, rejected unauthenticated secure actions and confirmation-only legacy/unknown routes are JVM-tested. Run `34154664082` passed, `33986395874` retains explicit-intent abuse evidence, and `34138775145` enforces production-manifest/documentation parity.
- **CM-P1-11:** Android cloud/device-transfer backup is explicitly excluded and locked by `PlatformBackupPolicyTest`; run `34155781163` passed. The versioned ReAppzuku backup remains the configuration contract.
- **CM-P1-12:** Android 16/API 36 now provides a stable repeatable runtime baseline. The separate
  Android-17/API-37 preview lane still blocks at PackageManager transport before repeatable app probes,
  so `targetSdk 37` remains gated.
- **CM-P2-01:** `PackageStateSource` is now an integrated read-only facade for BackgroundAppManager's running-process queries; commit `685d333f89692d13b7c0d8dd8514c3674eea6be1` replaces four duplicated `ps` parsing paths and passed normal run `34138482661`.
- **CM-P2-02:** mutating package/component/PID operations route through `PrivilegedShell` with
  typed enums/validated identifiers. Strict run `33946348081` required the repository-wide raw
  mutating-shell audit to return `NONE`; run `34005682619` additionally proves representative real Shizuku AppOps/standby/DeviceIdle/suspend/enable/force-stop/broadcast families with rollback checks. Root-specific and deliberately destructive families remain open.
- **CM-P2-03:** ActivityManager `ProcessRecord`/`ServiceRecord` parsing is isolated in pure Java; Smart Lifecycle package/dump/foreground/process text handling is further bounded by `PackageTextMatcher`, `SmartLifecycleProtectionPolicy` and `SmartLifecycleTextParser` with JVM tests. Run `34153570390` proves the `ProcessRecord` half on real API 36 through official Shizuku; live `ServiceRecord` injection remains a platform-harness gap rather than a source/parser gap. OEM/physical parser diversity remains open.
- **CM-P1-06 / A18:** Gradle dependency locking and SHA-256 verification are now enforced; run `33900939628` re-proved verification from an empty dependency cache and normal run `33901414025` passed afterward.
- **CM-P2-04:** fork-specific Smart Lifecycle, App Behavior, shortcut-security and accessibility
  strings are propagated to the current localized set (ES/RU/UK/ZH). Disabled App Behavior controls also list the exact
  active continuity blockers from `BackgroundWorkPolicy`.
- **CM-P2-05:** SQL debug bind contents are redacted by `SqlQueryLogFormatter`; tests assert sensitive package/state tokens cannot enter the formatted log while retaining SQL shape and bind count. Normal validation `34065691224` passed.

Latest assurance evidence:
- workflow run `34163432363`: full normal validation passed with `ManifestCapabilityPolicyTest`, moving critical package-visibility/usage-stats/Leanback manifest drift detection into the unit-test stage as well as lint;
- workflow run `34163189731`: after restoring the last-green manifest capability set while preserving the corrected `LogDetailActivity` parent, unit tests, zero-error lint, AndroidTest compilation, Room schema verification and APK build/upload all passed;
- workflow run `34156391150`: full normal validation passed with the minimal accessibility-service scope contract;
- workflow run `34155781163`: full normal validation passed with platform cloud/device-transfer backup exclusion locked by tests;
- workflow run `34154664082`: full normal validation passed with explicit exported-shortcut principal routing;
- commits `4156ed6b`/`4be29c0f` bind update assets and links to the validated fork/tag contract; `1aca3af6` mirrors the hardened stable-release workflow onto the product branch, including stable tag ↔ source/APK version checks;
- commits `2b8ad243`/`5f8e470c` validate imported preset clock/package structure and bound standalone preset reads; `893b9454` retains the already-proven transactional restore while adding collection-count bounds;
- commit `f426b55c` repaired an unintended older-manifest overwrite detected by lint, preserving only the intended `LogDetailActivity` parent fix and restoring the reviewed permission/Leanback capability set;
- workflow run `34154071232`: final cleaned product head passed unit, lint, AndroidTest compilation, Room-schema verification and debug APK build after removing the blocked synthetic A04 service harness;
- workflow run `34153818522`: full normal validation passed after extracting `AppLaunchTriggerPolicy` with exact-target, feature-gate and duplicate-suppression JVM coverage;
- workflow run `34138775145`: full normal validation passed after adding exported-component documentation parity and platform-permission regression coverage;
- workflow run `34138482661`: full normal validation passed with `PackageStateSource` integrated into `BackgroundAppManager`;
- workflow run `34075290320`: API-36 Sleep Mode owned-freeze recovery passed real freeze, durable ownership across external process death, and owned thaw/cleanup after wake/restart;
- workflow run `34065892554`: shell-backend readiness gating for queued `ShappkyService` actions passed unit/lint/AndroidTest/APK validation before commit `69750c7`;
- workflow run `34065691224`: SQL bind-value redaction passed full normal validation;
- workflow run `34057708486`: API-36 on-demand process topology passed provider-only and opt-in main-process wake cases across a real Shizuku restart;
- workflow run `34057507889`: exhaustive 32-mask App Behavior compatibility truth table passed;
- workflow run `34005682619`: representative real Shizuku privileged command families and reversible rollback checks passed against a disposable target;
- workflow run `34153570390`: real API-36 official-Shizuku readiness and `ProcessRecord` parsing passed; the synthetic test-only service was then rejected by Android 16 background-service policy, so live `ServiceRecord` evidence is not claimed;
- workflow run `33974963048`: full Android 16/API-36 instrumentation passed with repeated BootReceiver execution proving no duplicate active AutoKill, Smart Lifecycle or boot-cleanup Unique Work;
- workflow run `33974637281`: full API-36 instrumentation passed transactional restore/fault injection, active-preset reconciliation, ShellManager binder/permission/death-rebind sequences, exact-alarm denial fallback and Room v2→v11 migration;
- workflow run `33974626448`: normal validation passed after packaging Room historical schemas into `androidTest` assets;
- workflow run `33974877204`: normal validation passed for repeated boot WorkManager-idempotency coverage;
- workflow run `34000313640`: strict monotone lint cleanup removed exactly one stale baseline issue, kept the four current warnings visible, and passed unit/lint/AndroidTest/APK validation before commit `9fc85bd`;
- workflow run `34000092714`: fresh official-Shizuku ungranted→dialog→grant→READY→scan ordering and real MediaStore `content://` backup round-trip passed on API 36;
- workflow run `34036827312`: exact real permission-dialog Activity recreation passed, preserving the dialog across Activity destruction/recreation and recovering grant→READY→scan ordering;
- workflow run `34037508198`: current test APK install/pull SHA-256 identity and multidex embedded fork-update provenance passed on API 36;
- commit `ba73b39e4e47e75a19ac2a01a120a69854e19f30`: event-driven late-Binder recovery, source-gated by run `33999924081`;
- workflow run `33997372602`: official Shizuku UserService shell execution, daemon death detection, restart, same-process rebind and post-recovery command passed;
- workflow run `33986395874`: explicit-intent exported-shortcut abuse step passed before a later unrelated Shizuku-recovery failure;
- workflow run `33985971887`: real API-36 OS reboot/unlock/post-boot recovery and alarm reconstruction passed; its bundled force-stop tail was later superseded by the focused passing force-stop gate;
- workflow run `34004843634`: persisted disabled desired state survived a real external `am force-stop`, with the production process changing PID from `2963` to `3250`;
- workflow run `34036827312`: real Shizuku permission dialog remained active across Activity recreation, then grant restored `SHIZUKU_READY` and only afterward allowed app scanning;
- workflow run `33974469090`: normal validation passed for stale service-restart desired-state dominance;
- workflow run `33946729221`: `Clock`/`ScheduleTime` + `AlarmScheduler` integration passed unit, lint, AndroidTest compile and APK build;
- workflow run `33946888672`: `BackupCodec` envelope extraction passed unit, lint, AndroidTest compile and APK build before integration;
- workflow run `33940964413`: typed `PrivilegedShell` integration for AutoKill/BackgroundAppManager passed unit, lint, AndroidTest compile and APK build;
- workflow run `33941247796`: lifecycle/scheduler `PrivilegedShell` integration passed the same gates;
- workflow run `33946348081`: App Behavior blocker policy/UI plus final strict raw mutating-shell audit (`NONE`) passed unit, lint, AndroidTest compile and APK build;
- workflow run `33946501074`: normal read-only validation passed on the cleaned permanent source state;
- workflow run `33940487030`: parser/protection JVM fixtures, lint, AndroidTest compilation and debug APK build passed before production integration commit `57d8897c70a5b4c9565379cd72317f1abb7c9e59`;
- workflow run `33900939628`: dependency verification re-generated from fresh resolution and re-proved after dependency-cache deletion;
- workflow run `33901414025`: normal permanent locked/verified validation passed;
- earlier release target: `e202e38c049a0d4a7cfc561f7a9c8348c9abd8ae`;
- earlier APK SHA-256: `d841e34685d790197266c1e9c90a11619a33a219377892f2c429c00930dbf5d4`.

**Maintainability status:** the named high-risk seams now have explicit `PrivilegedShell`, `PackageStateSource`, `AppLaunchTriggerPolicy`, parser/protection/background policy, `Clock`/`ScheduleTime`, `AlarmScheduler`, `BackupCodec` and SQL-log redaction boundaries. API-36 runtime covers real Shizuku first-run/UserService/daemon recovery, permission-dialog Activity recreation, real OS reboot/alarm reconstruction, external app force-stop/process restart, Sleep owned-freeze recovery, on-demand process topology, representative privileged command families, shortcut abuse and MediaStore backup I/O. Real API-36 `ProcessRecord` parsing is proven; the synthetic `ServiceRecord` harness was removed after Android 16 consistently blocked its background start, leaving ServiceRecord runtime diversity for a foreground/physical-device lane. Root, physical/OEM diversity and final stable-release signing/rollback evidence remain open.

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
2. cancel pending restart intents when the user disables AutoKill/background mode;
3. test process death with enabled/disabled desired state.

### CM-P0-02 — Update checker still points at upstream fork provenance

**Surface:** A15  
**Lenses:** L09, L11, L14, L17, L25, L26

`UpdateChecker` queries `gree1d/ReAppzuku` and then downloads assets from the returned release.
The installed fork is `HyperCriSiS/ReAppzuku` with behavior and possibly signing identity that may
not match upstream.

**Hazard:** a fork can offer the wrong APK to itself.

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

### CM-P1-08 — No automated test tree — RESOLVED 2026-09-07

Historical finding: the repository originally lacked meaningful `app/src/test` / `app/src/androidTest`
coverage despite large stateful managers and privileged flows.

Current state: the branch now contains a broad JVM suite for policy, parser, manifest, backup,
release, shell and lifecycle contracts plus Android instrumentation for transactional restore,
migration, boot/restart, Shizuku permission/death/rebind, process topology/process death, shortcut
abuse and representative real privileged commands. Remaining RISK/DECIDED cells are therefore
surface-specific runtime/diversity gaps, not a blanket absence of automated tests.

### CM-P1-09 — Accessibility service configuration has a stale settings activity

`accessibility_service_config.xml` references:

`com.gree1d.reappzuku.SettingsActivity`

while the activity lives under:

`com.gree1d.reappzuku.ui.SettingsActivity`

It also requests `flagIncludeNotImportantViews`, although the current foreground-tracking use only
needs package/window-state information.

**Fix direction:** correct settings activity and remove unnecessary accessibility surface unless a
tested feature requires it.

### CM-P1-10 — Exported privileged shortcut surface needs principal review — RESOLVED AT SOURCE/TEST LEVEL 2026-09-07

Historical finding: `KillShortcutActivity` is exported and ultimately reaches privileged kill behavior,
while the caller/principal boundary was not explicit.

Current state: `ShortcutEntryPolicy` makes the routing contract explicit and exhaustively tests secure,
legacy, unknown and null actions; unauthenticated secure actions are rejected and non-secure public
routes require confirmation. `ShortcutAuth` still authenticates time-bounded secure intents, explicit
third-party intent abuse has Android evidence, and exported-principal drift is separately locked by
`ExportedComponentsParityTest`. Broader OEM/launcher behavior remains A16 release-diversity evidence.

### CM-P1-11 — Automatic Android backup is not explicitly reconciled with ReAppzuku backup — RESOLVED 2026-09-07

Historical finding: platform Auto Backup and ReAppzuku's structured backup could have created
competing restore contracts.

Current state: `android:allowBackup="false"` is enforced, legacy Full Backup excludes all domains,
and Android 12+ Cloud Backup plus Device Transfer exclude root state. `PlatformBackupPolicyTest`
locks all three decisions; ReAppzuku's bounded/versioned application backup is the sole supported
configuration-transfer contract.

### CM-P1-12 — Android 17 / API 37 compatibility lane is missing

The project currently compiles/targets API 36 with AGP 8.10.0. Android 17 is API 37 and includes
behavior changes that can affect hidden/reflection-heavy and memory-sensitive code.

**Fix direction:** add an API 37 compatibility lane first. Moving compile/target to 37 requires an
AGP upgrade (official current minimum for API 37 is AGP 9.1.1), so treat that as a planned build
migration rather than a blind version bump.

---

# P2 / maintainability findings

### CM-P2-01 — High-complexity classes need stable seams

**Status: partially resolved / ongoing.** The strategy is extraction rather than rewrite. Stable seams now include:
- `PrivilegedShell`
- integrated `PackageStateSource` for BackgroundAppManager read-only process state
- `AutomationDesiredState`
- `AlarmScheduler`
- `Clock` / `ScheduleTime`
- central protection/background/parser policies
- `AppLaunchTriggerPolicy`
- `BackupCodec`

`PackageStateSource` integration commit `685d333f89692d13b7c0d8dd8514c3674eea6be1` replaced four duplicated `ps` parsing paths and passed full normal validation in run `34138482661`. `AppLaunchTriggerPolicy` commit `fed74f01a57cd8379f773293b695f7ecbef63fbe` similarly extracts app-launch eligibility/dedupe from the AccessibilityService and passed normal run `34153818522`. Remaining large UI/orchestration classes are maintainability work, not an unresolved privilege invariant.

### CM-P2-02 — Shell command construction should be typed

**Status: resolved for audited mutating product paths.** `PrivilegedShell` owns typed/validated package,
component and PID mutations and the strict repository audit returns no raw mutating-shell construction
outside that boundary. Run `34005682619` additionally executes representative real Shizuku command
families with rollback verification. Root-specific execution and intentionally destructive operations
remain release-diversity/security evidence, not a reason to reintroduce raw shell construction.

### CM-P2-03 — Smart Lifecycle / process protection parsing needs bounded contracts

**Status: substantially resolved at source/test level.** `ProcessDumpParser` isolates ActivityManager
`ProcessRecord`/`ServiceRecord`; `PackageTextMatcher` enforces token/package boundaries;
`SmartLifecycleProtectionPolicy` owns dump-derived protection decisions; and
`SmartLifecycleTextParser` isolates foreground/process text extraction. JVM fixtures cover AOSP/OEM-style
records, remote processes, CRLF, exact package boundaries and false-positive neighbors.

Real API-36 evidence now covers the `ProcessRecord` path through official Shizuku (`34153570390`). Repeated attempts to create a synthetic live `ServiceRecord` were rejected by Android 16 background-service policy even through the privileged shell, so the non-deterministic harness was removed; ServiceRecord parsing remains bounded by JVM fixtures until a natural foreground/physical-device runtime source is available.
OEM/physical-device process-output diversity remains an external compatibility lane rather than a reason
to broaden parsers heuristically.

### CM-P2-04 — Fork-specific UI strings need locale parity

**Status: resolved for the current localized set.** Smart Lifecycle, App Behavior, shortcut-security and
accessibility strings are present in ES/RU/UK/ZH, while the UI gets blocker names from the central
`BackgroundWorkPolicy` instead of duplicating translation/state logic. Future locale expansion remains
ordinary i18n work.

### CM-P2-05 — Debug SQL bind args can expose app-state metadata to logcat

**Status: resolved.** `SqlQueryLogFormatter` logs SQL shape, bind count and a redaction marker but never
the bind contents. Tests explicitly use sensitive package/state tokens and assert they are absent.
Commits `0d55aab8` / `711f8195` are green in normal validation run `34065691224`.

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

## Release gate

A release candidate is only **PROVEN** when:
- no P0 surface remains RISK;
- all P1 findings have explicit owner/acceptance criteria;
- destructive DB fallback is absent;
- required migrations are non-destructive and tested;
- permission/process/reboot state machines have repeatable evidence;
- shell commands are bounded and validated;
- exported privileged boundaries are abuse-tested;
- backup format is round-trip/fault-injection tested;
- update provenance matches the installed fork identity;
- lint has zero errors and no CI step rewrites lint acceptance;
- immutable external Action pins and least-privilege job permissions are in place;
- release APK/AAB identity, signing provenance and rollback procedure are recorded.
