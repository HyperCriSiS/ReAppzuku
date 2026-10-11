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

The opt-in CI fixture `scripts/tests/ci-real-uid-appops-conflict.sh` operates
**only** on the minimal-manifest `com.reappzuku.securityprobe` test APK on
API37. On API24 it reads the separately installed AndroidTest APK only and
records numeric UID shell support as UNKNOWN; no mutations are performed.

Each test verifies that it is running on an emulator, Android user 0, with
a distinct fixture package and a single unique UID. Before any change it
captures the complete existing package/UID AppOps output, including
system-provisioned unrelated IGNORE records, and confirms `WAKE_LOCK` is
absent on both scopes and reports platform fallback `Default mode: allow`.

**Important empirically observed limitation:** a disposable API37 run
`38105273705` successfully created a real conflict:

```text
Uid mode: WAKE_LOCK: ignore
WAKE_LOCK: allow
```

But setting both scopes back to ALLOW **did not restore original provenance**:
the explicit package `WAKE_LOCK: allow` remained even though the initial
package observation had no operation. CI correctly failed with
`APP_OPS_FIXTURE_RESTORE_MISMATCH`. Thus an operation-level `set allow`
cannot be treated as universal inverse of an initially absent package mode.
A blanket `cmd appops reset` is unsafe here because both AndroidTest and
minimal securityProbe APKs begin with dozens of unrelated UID IGNORE entries.
Never infer safe exact rollback from matching effective allow/ignore behavior.

The revised CI probe therefore **never writes package-scoped AppOps**. It
exercises UID `WAKE_LOCK=ignore` against the unchanged implicit package
default ALLOW, then UID `allow` and `default`. The EXIT trap restores
**only UID WAKE_LOCK** to the verified platform default ALLOW, requiring
byte-for-byte equality with the complete original package inventory and
separate package/UID WAKE_LOCK queries. No unrelated UID mode or user policy
may be changed. A mismatch fails CI and is a recorded platform limitation,
not permission to weaken the original-state check.

The synthetic JVM parser still tests the independent package-allow/UID-ignore
row form. The new CI test, even if successful, proves a **bounded UID
precedence and safe targeted UID restoration** on a disposable emulator.
It does not establish exact package-override rollback, real privileged
Root/Shizuku restoration, signer lineage, cross-process atomicity, OEM/physical
compatibility or flash-power-loss durability. Keep v1 and RZJ2 unconnected
from privileged scheduler mutations.

## Remaining gates

Independent current package/user/certificate identity across a real durable capture and restore; UID-level modes and attribution precedence per official platform API; coherent full-state observations before mutation; independent known-correct restoration commands and fault injection on supported API levels; rollback on deletion/overlap; process and power-loss behavior; production Root/Shizuku, physical/OEM, stable signing and upgrade. **Do not wire read-only diagnostics into the live RestrictionsScheduler.**