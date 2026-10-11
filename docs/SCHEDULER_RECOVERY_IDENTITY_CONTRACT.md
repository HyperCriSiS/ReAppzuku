# Phase 11 — Installed package identity and AppOps observation contract

Status: **diagnostic / read-only / not authorized for recovery** (2026-10-11). Issue #130.

## Version boundaries

- Journal v1 cannot encode raw enabled state, UID provenance, exact AppOps modes or DeviceIdle user/system origin. No upgrade/conversion to v2 is safe.
- The separate RZJ2 format persists complete *older schema* observations, but **does not encode installed certificate, user ID, UID AppOps layer, install identity continuity or exact restoration commands**. An RZJ2 OBSERVED record may only become REVIEW_REQUIRED; it must never authorize a live scheduler mutation.
- `SchedulerInstalledPackageIdentity` is an *unpersisted diagnostic*. It double-reads PackageManager data, comparing package name, app UID, Android user, SHA-256 of the current APK signing certificate (from trusted PackageManager rather than shell text), versionCode, firstInstallTime and lastUpdateTime; it rejects missing/multiple signer and multiple *visible* UID-package mappings.
- `checkAgain` detects observed changes since capture. Two equal snapshots **cannot** guarantee the package stays installed afterward, rule out same-signer reinstall with reused data or prove package-list completeness under Android visibility filtering. Signing lineage / rotation, shared UIDs and explicit long-term identity attestation remain UNKNOWN.
- A **new, independently versioned evidence contract** is required for UID-scoped modes and signer/user provenance. Never append extra fields to old RZJ2 bytes or silently interpret them as equivalent.

## Read-only AppOps semantics

Android AppOps can contain separate package- and UID-scoped modes. A UID override takes precedence over the package mode in the AOSP shell implementation. Its `cmd appops get` formatter can label a row `Uid mode: OP: MODE`; a package row can independently read `OP: MODE`.

The new `SchedulerAppOpsDiagnostic` parses the layers separately and recognizes exact observed `allow`, `ignore`, `default`, `foreground`, `errored`, `deny`, and `ask` tokens. Duplicate, malformed, unknown, absent or conflicting descriptions return UNKNOWN. `No operations.` and `Default mode:` are **not stored explicit modes**. A UID-only result is **not** proof of an explicit package-level mode. If an explicit UID override appears, the existing `SchedulerRestrictionAndroidReader.appOpMode` returns UNKNOWN because its RZJ2 destination cannot preserve both layers.

This is a human-readable shell **diagnostic**, not an authoritative API for stable UID precedence across every Android release/OEM. The new API24/API37 CI step emits raw, bounded results for ReAppzuku's disposable installed debug package and UID with *no AppOps writes*. Unsupported syntax, missing operations and unknown results remain UNKNOWN; no assertion fabricates an implicit default or silently converts it to an explicit package mode.

## Android-version constraints

| Item | Android 7 / API24 | Android 17 / API37 |
|---|---|---|
| Current APK certificate SHA-256 | Legacy PackageManager GET_SIGNATURES | PackageManager SigningInfo current APK signer |
| UID + Android user, version and install timestamps | PackageManager read | PackageManager read |
| Shared-UID completeness | Visible packages only; cannot certify globally | Visible packages only; visibility restrictions apply |
| Suspension flag | UNKNOWN (not an authoritative compatible API) | May be read through PackageManager, but can fail |
| AppOps package/UID | Exact shell output may be different or unsupported | Exact shell output may be different or unsupported |
| Implicit mode | UNKNOWN, not `default` | UNKNOWN, not `default` |


## Controlled AppOps conflict test — disposable emulators only

An opt-in CI shell fixture (`scripts/tests/ci-real-uid-appops-conflict.sh`) operates
**only** on the separately installed minimal-manifest
`com.reappzuku.securityprobe` APK on API37, never the product,
physical hardware or a user's apps. On API24 the already installed
`com.gree1d.reappzuku.test` is inspected **read-only** and never reset.
The script checks the Android API/user/package identity and a unique fixture
UID, then inventories and snapshots the **entire** package and numeric-UID
AppOps output. Android 17 prepopulates many `IGNORE` UID permission modes
even on minimally declared apps. The first two diagnostics correctly refused
to mutate either the AndroidTest or securityProbe APK while a broad reset
would destroy that unrelated state. The revised test requires only
`WAKE_LOCK` to be absent at both levels, leaving all other modes untouched.

It then attempts package `WAKE_LOCK=allow` against UID
`WAKE_LOCK=ignore`, observes the actual shell text and tests further UID
`allow`/`default` transitions. Its EXIT trap invokes **two targeted
`WAKE_LOCK=default` setters** (one UID-scoped and one package-scoped) and
compares the complete original package inventory plus both `WAKE_LOCK`
queries byte-for-byte with the baseline. A failed restoration fails CI.
No broad `cmd appops reset` or unrelated-op writes are permitted.
The AOSP source confirms UID precedence, but platform-visible state equality
is not a flash-power-loss or explicit-absence attestation.

On Android 7/API24 the numeric-UID shell syntax is already known to be
unsupported by the tested emulator. The fixture performs baseline
inspection only and reports `API24_UID_OVERRIDE_PROBE_READ_ONLY_UNSUPPORTED`.
Its successful exit is **not** a positive UID test. The script must never
be run against personal devices, existing configured packages, system
applications or production installations.

Even a successful API37 fixture proves a bounded **shell-mode precedence
observation** and restoration for one disposable package, not the behavior
of a real Root/Shizuku backend in production, complete AppOps provenance,
power-loss safety or authorization to replay privileges.

## Remaining gates

Independent current package/user/certificate identity across a real durable capture and restore; UID-level modes and attribution precedence per official platform API; coherent full-state observations before mutation; independent known-correct restoration commands and fault injection on supported API levels; rollback on deletion/overlap; process and power-loss behavior; production Root/Shizuku, physical/OEM, stable signing and upgrade. **Do not wire read-only diagnostics into the live RestrictionsScheduler.**