# ReAppzuku quality gates

This document turns `CHECK_MATRIX.md` into executable engineering gates. A status is `PROVEN` only with repeatable evidence. Compilation alone is never sufficient proof for lifecycle, privilege, migration or reboot behavior.

## Gate 1 — Privileged backend readiness

The app must distinguish:
- backend unavailable;
- Shizuku permission required;
- permission request pending;
- permission granted but service not yet ready;
- UserService binding;
- backend ready;
- backend lost after previously being available;
- root ready.

Required evidence:
- JVM state-transition tests;
- delayed Shizuku binder probe;
- deny -> grant probe;
- Activity recreation while permission is pending;
- service death / Shizuku restart recovery;
- no privileged command before READY.

A waiting/binding state must never be surfaced as a generic shell-command failure.

## Gate 2 — Desired state / recovery

Every long-lived automation needs an explicit desired-state predicate independent of observed process state.

Required probes:
- enable -> process death -> desired service recovers;
- disable -> restart receiver -> service remains disabled;
- duplicate boot/schedule callbacks are idempotent;
- stale callbacks after disable are harmless.

## Gate 3 — Alarm / reboot behavior

Exact-alarm use must be capability-driven.

Required:
- only the permission model actually needed by the app is declared;
- `canScheduleExactAlarms()` gates exact scheduling where Android requires it;
- a documented inexact fallback exists;
- preset/restriction alarms are rebuilt after boot;
- no credential-encrypted preference read during locked boot unless direct-boot storage is explicitly introduced.

## Gate 4 — Backup / restore transaction

Restore must be all-or-nothing from the user's perspective.

Required:
- size cap before parsing;
- reject unsupported future versions;
- parse/validate entire payload before first durable mutation;
- snapshot all touched preference stores;
- durable main/preset writes before runtime side effects;
- rollback all stores if a later durable write fails;
- best-effort runtime reconciliation after rollback;
- active-preset base state cannot overwrite newly imported settings later;
- corrupt, legacy, future and oversized fixtures.

Android platform backup/device-transfer restore is explicitly disabled; ReAppzuku's versioned backup format is the configuration migration contract.

## Gate 5 — Room migration

No destructive fallback in production.

Required:
- continuous explicit migrations in source;
- exported schema for every version from the point this fork begins preserving them;
- historical schemas are sourced from genuine artifacts/history, never fabricated;
- `MigrationTestHelper` executes every supported historical path;
- representative rows survive migration;
- final schema validation passes.

Current constraint: upstream preserved schema 2 but not authentic schemas 1 and 3–10. Supported helper evidence therefore begins at v2 unless genuine older schemas are recovered.

## Gate 6 — Exported / privileged entry points

For every `android:exported="true"` component document:
- intended caller principal;
- input validation;
- privilege gained by reaching the component;
- replay/forgery risk;
- denial/degraded behavior when Shizuku/root is absent.

No untrusted external intent may become a generic privileged shell executor.

Required:
- dynamic privileged shortcut has an unguessable install-local authenticator;
- authenticator comparison is constant-time;
- static/legacy shortcut requires explicit user confirmation before privilege;
- explicit external Activity launches can only route through user confirmation;
- authenticated foreground action waits for shell READY;
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
- Run `33814172310` proved AGP 9.4.0 / Gradle 9.6.0 / built-in Kotlin 2.3.21 at SDK 36 across unit, lint, AndroidTest compile, Room schema and APK gates; publish was skipped.
- Run `33815939003` proved the same gate chain after raising only `compileSdk` to 37 while `targetSdk` remained 36. Validation APK SHA-256: `50f07dc229729b0df68c14d6550cf0f52c286297c8ae541fc62f57c18a5c9912`.
- Toolchain/API-37 compilation are therefore `PROVEN`; target-37 behavior remains gated by the separately blocked runtime lane.

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
