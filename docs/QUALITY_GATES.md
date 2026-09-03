# ReAppzuku Quality Gates

> Adapted from the Voice-platform quality-gate process.
> These gates turn `docs/CHECK_MATRIX.md` findings into repeatable release rules.

## Gate 0 — Pre-code design gate

Required for changes touching privilege, process topology, background execution, app killing,
backup, database, exported components or Android policy.

Before code:
- state the user-visible goal;
- state the desired-state invariant;
- list lifecycle transitions;
- identify conflicting controllers;
- define failure/recovery behavior;
- define what evidence will prove the change.

No feature may introduce a second independent source of truth when an existing policy object can own
the decision.

## Gate 1 — Privilege/backend readiness

No privileged operation may execute merely because Shizuku permission is granted.

The backend is ready only when:
- root backend is actually executable; or
- Shizuku binder exists, permission is granted, and UserService is connected.

Required tests:
- deny -> grant;
- delayed bind;
- binder/service death;
- Shizuku restart;
- Activity recreation during permission dialog;
- repeated concurrent callers.

## Gate 2 — Desired background state

There must be one explicit answer to: **Should ReAppzuku be running in the background now?**

Controllers that affect this include:
- AutoKill/background service;
- Smart Lifecycle;
- Sleep;
- Presets;
- Restrictions Scheduler;
- App Behavior options.

Required invariant:
- explicit user disable dominates self-restart logic;
- recovery may restart only when persisted desired state still requires it;
- no boot receiver or alarm may silently re-enable a disabled behavior.

## Gate 3 — High-impact action safety

Before force-stop/freeze/disable/app-op changes:
- target package is validated;
- protected-app policy is applied;
- current foreground/user-critical state is checked where applicable;
- action is idempotent or has defined repeated behavior;
- failure is surfaced truthfully.

Long-term target: typed privileged operations instead of ad-hoc shell strings.

## Gate 4 — Alarm / scheduler correctness

For any exact-alarm feature:
- one exact-alarm permission model is used;
- `canScheduleExactAlarms()` is checked where required;
- denial has actionable UI;
- reboot rebuilds alarms;
- permission revocation is recoverable;
- time zone / clock changes are considered;
- repeated scheduling is idempotent.

## Gate 5 — Backup / restore / migration

Restore must be:
1. parsed;
2. version-validated;
3. fully validated without side effects;
4. staged;
5. committed;
6. runtime state reconciled only after durable commit.

A failed restore must not leave a partially applied configuration.

All durable fork-owned settings must round-trip unless explicitly documented as non-portable/runtime.

Database upgrades:
- no destructive fallback in release unless user explicitly opted into data reset;
- every supported Room schema is committed;
- migration tests are required.

## Gate 6 — Exported component / principal boundary

For every `exported=true` component:
- why it must be exported is documented;
- accepted callers/actions/data are defined;
- privileged effects are separated from public routing where practical;
- explicit-intent abuse is tested;
- untrusted extras never become raw privileged shell commands.

## Gate 7 — Accessibility / privacy minimization

Accessibility service:
- subscribes only to required event types;
- does not request view-tree scope unless a feature needs it;
- settings activity resolves correctly;
- disabling the service degrades gracefully;
- foreground tracking stores the minimum durable data required.

Debug logs:
- are opt-in;
- avoid secrets and unnecessarily detailed bind args;
- clearly distinguish observed state from inferred state.

## Gate 8 — Android compatibility

Validation lanes:
- minimum supported API;
- representative modern API;
- current target API;
- newest Android/API compatibility lane.

Android 17/API 37 must be tested before target migration.
Compile/target 37 requires a compatible AGP upgrade; treat build-tool migration as an explicit change.

Hidden APIs / dumpsys parsers require:
- failure fallback;
- fixture/device probes;
- truthful “unknown” rather than fabricated state.

## Gate 9 — CI / supply chain

Validation CI is read-only.

Required:
- no source commits from validation jobs;
- no `updateLintBaseline` in validation/release jobs;
- lint baseline changes are reviewed source changes;
- unit tests run on every relevant PR/push;
- external GitHub Actions are pinned to immutable full SHAs;
- token permissions are least-privilege;
- stale runs use concurrency cancellation;
- Gradle dependency verification/locking is evaluated.

Publishing is a separate job/workflow with `contents: write`.

## Gate 10 — Update / release / rollback

The app must never recommend an artifact from a different distribution provenance by accident.

Required:
- update owner/repository/channel belongs to this fork;
- version comparison is deterministic;
- release signing identity is stable;
- artifact SHA-256 is published;
- install/update path is tested from previous fork build;
- rollback procedure is documented;
- upstream releases cannot silently overwrite fork behavior.

## Gate 11 — i18n / UX truthfulness

New user-visible features must:
- have base strings;
- be propagated to supported locales or explicitly tracked;
- explain disabled controls and the blocking feature;
- never show “failed” when the real state is “waiting for permission/service”;
- never show “success” before privileged side effects are complete.

## Gate 12 — Definition of Done

A change is not DONE merely because the APK builds.

For high-impact changes, DONE requires:
- architecture invariant documented;
- relevant CHECK_MATRIX cells updated;
- failure/recovery path implemented;
- automated test or repeatable Android probe added;
- lint/tests pass without baseline mutation;
- no unresolved P0;
- P1 risks have explicit owner/issue;
- release provenance is preserved.


## Current gate evidence — 2026-09-04

- Assurance run `33577363239` completed with zero lint errors before baseline acceptance.
- The committed lint baseline contains reviewed historical warnings only; security, compatibility,
  accessibility and correctness categories explicitly listed by the migration gate were not accepted
  into the baseline.
- JVM tests, `assembleDebugAndroidTest`, Room schema-11 presence and `assembleDebug` all passed.
- Release `ondemand-test` targets `e202e38c049a0d4a7cfc561f7a9c8348c9abd8ae`; APK SHA-256 is
  `d841e34685d790197266c1e9c90a11619a33a219377892f2c429c00930dbf5d4`.
- The normal workflow is source-authoritative again: validation has `contents: read`; only the
  downstream publish job receives `contents: write`.
- The Android 17/API 37 lane was exercised before any `compileSdk`/`targetSdk` migration. Build, API-37 boot, user unlock and PackageManager readiness are repeatable; one run installed both APKs and enabled `USE_NEW_MESSAGEQUEUE`, but current repeated runs are blocked at the preview PackageManager transaction by `Broken pipe (32)`. This is tracked as `RISK/BLOCKED`, not compatibility failure and not `PROVEN`.
- Run `33812905493` retried only that known transport failure three times with non-streaming installation; all three pushes succeeded and all three PackageManager calls failed identically. No application-signature, parse, permission or `INSTALL_FAILED_*` error appeared, so further retries on the same preview image are prohibited by the quota/quality policy.
- The temporary API-37 dispatch path was removed after evidence collection. Commit `031a8634db725bb93185d3e55819dc8b5165e96d` restores the exact least-privilege Validate -> Publish workflow.

## Immediate conversion plan

### Phase 1 — P0 containment
- guard `ShappkyService` restart against intentional disable;
- move update checker to fork-owned channel;
- lock down Shizuku readiness with state-machine tests.

### Phase 2 — persistence/reboot correctness
- restore preset alarms after boot;
- repair exact-alarm permission architecture;
- make backup restore transactional/versioned/complete;
- remove destructive Room fallback and add migration schemas/tests.

### Phase 3 — evidence infrastructure
- introduce JVM unit tests and Android instrumentation;
- extract testable facades (`PrivilegedShell`, `Clock`, `AlarmScheduler`, desired-state policy);
- add PR/push validation CI.

### Phase 4 — hardening
- exported-entrypoint review;
- accessibility minimization;
- API 37 compatibility lane;
- dependency/action pinning;
- localization completion;
- release/rollback provenance.
