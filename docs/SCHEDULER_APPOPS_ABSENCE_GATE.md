# Phase 11 / Issue #136: exact AppOps provenance and unsupported restoration

Status: **RESEARCH CONTRACT / BLOCKED**, 2026-10-11. This document is not approval for runtime AppOps mutation. Production `RestrictionsScheduler`, RZJ2, and v1 recovery records remain **evidence only**.

## Source-grounded distinction

Android's official [AppOps design notes](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/AppOps.md) explicitly distinguish the initial operation mode from `MODE_DEFAULT`. The initial mode can be `MODE_ALLOWED`. They warn that `setMode` (package) and `setUidMode` (UID) for one operation can result in confusing persisted combinations, and state absent from an `appops get`/dumpsys listing means initial state, **not** an explicitly stored `MODE_DEFAULT` record.

The [current AOSP shell parser](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/appop/AppOpsService.java) exposes `reset [--user USER] [PACKAGE]` and delegates to `resetAllModes(userId, packageName)`, not a documented per-operation deletion. [Historical service source](https://android.googlesource.com/platform/frameworks/base/+/84b1e35/services/core/java/com/android/server/AppOpsService.java) also shows `resetAllModes` processing many UID and package entries. This is **not** a safe inverse of one targeted write when unrelated modes preexist. AOSP internals and hidden/system APIs must not be treated as available shell/Shizuku/Root interfaces without a separately audited supported contract.

## Observed platform evidence (not hypothetical)

- Android 17/API37, [run 38105273705](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38105273705): an originally absent package `WAKE_LOCK` entry remained explicitly `WAKE_LOCK: allow` after `set allow`; full original-state equality correctly **FAILED**. Mixed package and UID mutations are disallowed in subsequent tests.
- Android 17/API37, [run 38105814131](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38105814131): a separate **UID-only** `WAKE_LOCK` fixture restored the complete original AppOps inventory exactly. This **does not** prove package-mode absence restoration.
- Android 7/API24: no positive proof of numeric UID mutation, isolated package absence deletion, or exact package restore. The prior read-only numeric UID query was unsupported/UNKNOWN.
- No physical-device, OEM-specific, real Root/Shizuku production, power-loss, reinstall/signature continuity or cross-process consistency proof exists.

## Versioned provenance proposal (not implemented)

A future **new, separate** record version must preserve, for *each* operation and scope, `userId`, exact installed `packageName`, installed `uid`, signer/certificate identity and observed installation generation; `scope=PACKAGE|UID`; `presence=ABSENT|EXPLICIT|UNKNOWN`; `mode` **only** when explicit; `platformInitialMode` separately when reliably known; observation timestamp, immutable full-state evidence fingerprint, version, and before/after identity rechecks. Missing, contradictory, concurrent or malformed data is UNKNOWN. Explicit `MODE_DEFAULT` must never be substituted for ABSENT, and initial ALLOW must never be substituted for explicit ALLOW. Existing v1/RZJ2 bytes must not be reinterpreted or migrated silently. An unchanged capture-time signer is not proof of identity at restore-time.

## Restoration support / release gate matrix

| Case | API24 | API37 | Automatic privileged recovery |
|---|---|---|---|
| Originally ABSENT **package** op, restore after explicit package set | UNSUPPORTED / unproven | **UNSUPPORTED**, negative experiment | **DENIED; REVIEW_REQUIRED** |
| Explicit package ALLOW/DEFAULT/IGNORE, restore exact presence and value | Unproven | Unproven end-to-end | **DENIED; REVIEW_REQUIRED** |
| Originally ABSENT UID WAKE_LOCK, isolated fixture-only restore | Unproven | **PASS in one disposable emulator configuration** | **DENIED** (cannot extrapolate) |
| Mixed package + UID op writes | Unproven | Explicit package residue observed | **DENIED; REVIEW_REQUIRED** |
| Broad `appops reset` with unrelated existing UID modes | Unsafe/unproven | Unsafe due to unrelated preexisting modes | **PROHIBITED** |
| UID / signer / user / installed generation changes, uninstall or reinstall | Unproven | Unproven | **DENIED; REVIEW_REQUIRED** |
| Operation absent or mode unknown/ambiguous | UNKNOWN | UNKNOWN | **DENIED; REVIEW_REQUIRED** |

Here **UNSUPPORTED** means no authorized, narrow, source-proven inverse has been demonstrated under the proposed shell/Shizuku/Root support contract; it does not claim a mathematical impossibility or rule out private system internals.

## Next acceptance steps (research/test only)

1. Audit API24 and API37 official shell parser and service implementation for an *individually scoped* deletion mechanism, its caller permissions and precisely tested side effects. If not supported, retain UNSUPPORTED permanently for that platform/backend.
2. If one exists, test **only** on a disposable emulator with a fresh uniquely verified single-UID package-only fixture; immutable complete preflight dump, both-scope reads, trap cleanup, and hard fail on unrelated changes. Never run `resetAllModes` or mix scope setters as an approximation.
3. Add independent JVM negative cases: ABSENT vs explicit ALLOW vs explicit DEFAULT, unexpected UID overrides, malformed and inconsistent snapshots, identity drift, failed command, and uninstall/reinstall. Each must fail closed.
4. Require source-head standard CI, CodeQL, API24 and API37 emulator evidence, plus exact original-state after cleanup. Without all applicable gates, leave runtime privileged recovery disconnected.
5. Issue #130 independently gates full live original-state capture, signer/reinstall continuity, durable rollback/fault injection, backend permissions, OEM/physical devices and flash-power-loss behavior.

**Decision:** no AppOps original-ABSENT package restoration is currently authorized. Keep Phase 11 PARTIAL.

## Supported-command source audit — 2026-10-11

- [Current AOSP AppOpsService shell parser](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/appop/AppOpsService.java) exposes per-op `set` but a **package-wide** `reset` command only. `reset` directly calls `resetAllModes(userId, packageName)`; no documented `delete` / `remove` or per-op `reset` syntax exists in that shell command switch.
- [Android 7.1.2-era AOSP AppOpsService](https://android.googlesource.com/platform/frameworks/base/+/android-7.1.2_r36/services/core/java/com/android/server/AppOpsService.java) likewise uses `resetAllModes`; its binder `setMode` requires privileged `UPDATE_APP_OPS_STATS` permission, not an arbitrary third-party call. This is historical source corroboration, **not** a substitute for direct API24 emulator evidence or an assertion that all OEMs match that branch.
- Modern `setMode` similarly checks the caller through `enforceManageAppOpsModes`; internal service/store helpers are not a publicly supported, permission-free or stable Shizuku/Root deletion API. Root privilege by itself does not document an operation-local rollback contract.
- [Official AppOps design notes](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/AppOps.md) distinguish the operation-specific initial mode from `MODE_DEFAULT`, and explicitly caution against setting both UID and package modes for the same operation. Restoring effective `allow` does **not** establish ABSENT provenance.

**Final supported-shell decision:** exact restoration of an originally ABSENT **package-level** AppOps mode is **UNSUPPORTED** under the audited shell contract on API37 and unproven/unsupported under the older inspected interface. It must remain `REVIEW_REQUIRED`, with **no automatic privileged restore**. No new destructive package-only test was undertaken: without a supported narrow inverse, such an experiment risks persistent fixture damage and cannot meet the preflight cleanup invariants. The successful API37 UID-only fixture is an independent, strictly limited exception and must not be promoted to package-level or production support. Issue #130 continues to gate live capture, signer/UID continuity, backend authority, durable rollback and physical/OEM acceptance.

