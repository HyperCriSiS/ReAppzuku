# Restrictions Scheduler: failure-recovery design (Phase 11)

Status: **design only; no privileged retry or recovery behavior implemented** (2026-10-10).

## Verified current boundary

`RestrictionsScheduler.tick()` processes protection changes per package and updates `temp_protected_packages` only after successful operations; `reconcileAfterClockChange()` similarly retains unresolved markers. Both invoke `scheduleNext()`. The marker is a **set of package names**, not a durable record of original restrictions. If a schedule disappears while a package is temporarily unprotected, a later pass cannot reconstruct what should be restored. A `SharedPreferences.commit(false)` can still change the process-local view; rollback is best effort, not a power-loss transaction.

## Proposed durable recovery record

Use a separate versioned, strictly bounded store, **not** a reinterpretation of existing `temp_protected_packages`. Each record should contain package ID, schedule ID, captured original restriction tuple (only fields that can be read and restored safely), applied-change bitset, lifecycle phase, timestamp and an idempotency/version token. Maximum entries, payload bytes and package-name lengths must be enforced before persistence. Never store a privileged command string or user-supplied component action.

**Capture before mutation:** read the original restrictions; durably write a pending record before lifting protection. Abort without mutation if capture or commit fails. After each successfully applied step, advance the record using a checked durable commit. If crash occurs between system mutation and record commit, recovery must be idempotent, inspect live state and never assume a step happened just because it was attempted.

**Restore on deletion:** deleting or disabling a schedule must retain the record until the previous restriction tuple is successfully restored or explicitly determined inapplicable. Do not infer original settings from current flags or delete the recovery record merely because a package no longer appears in any schedule. Detect package uninstall/replacement, user policy edits and overlapping active schedules before restoration; conflicts enter a reported manual-recovery state rather than overwriting intentional changes.

**Overlap semantics:** multiple schedules touching one package require a single package-scoped restoration owner with reference tracking; lifting the first schedule's window must not restore protection while another schedule still demands the same relaxation. Specify precedence explicitly and test it before migration.

## Retry design, deliberately non-destructive

- Retry only unsuccessful, eligible protection/restore operations; never replay `onActivateAction` or force-stop due solely to recovery, clock change or an unresolved old record.
- Apply bounded exponential backoff with a cap, plus a per-package retry budget and a future durable wake-up; no busy loop or repeated service launch on every receiver callback.
- Gate every attempt on already-established shell readiness and current permissions. Distinguish transient shell unavailable from permanent unsupported/denied, malformed record, uninstall and a conflicting user edit.
- Keep failed records and existing markers; log bounded diagnostics and surface terminal/manual recovery without silently reporting success.
- Ensure alarms are re-armed even on exceptions; avoid scheduling a retry earlier than is permitted by Android exact-alarm/Doze constraints. Re-read effective schedules and actual state for every retry.

## Non-destructive acceptance matrix

1. JVM: start/end, DST overlap/gap, adjacent and overlapping schedules, deletion during lifted state, duplicate package records and corruption/oversize rejection.
2. Injected faults: fail before first durable write; fail after store commit; fail after each system operation; `commit(false)` mutates in-memory preferences; process restart at every transition.
3. JVM/Android emulator: retries are bounded and idempotent; missing privileges never produce false success; deleting an active schedule never loses its saved tuple; concurrent user edits block stale restoration.
4. API24 and API37: persistence and wake-up behavior, alarm permission denial, app process death, backup/restore compatibility. No fake root/Shizuku success claims.
5. Physical acceptance: real root and Shizuku paths, OEM background constraints, device clock transitions, actual deleted-schedule restoration and power-loss scenarios require separately approved test devices.

## Implementation order and boundaries

1. Implement pure validated recovery-record model and state machine, with fault-injected JVM tests.
2. Add a bounded durable store and migrate from old marker **conservatively**: unknown legacy original tuples remain unresolved; never invent defaults.
3. Wire capture/commit around real lift/restore operations, then deleted-schedule handling and overlap logic.
4. Add bounded retries only after safety and API24/API37 gates pass; preserve existing `TIME_SET` and `TIMEZONE_CHANGED` receiver semantics.
5. Validate against `docs/QUALITY_GATES.md`, perform independent security review, then separately pursue physical privileged acceptance.

This document specifies future implementation; it **does not** close the Phase 11 clock/scheduler roadmap item.
