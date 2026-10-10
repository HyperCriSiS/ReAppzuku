# Phase 11 — fail-safe Restrictions Scheduler recovery design

Status: design only (2026-10-10). **No production behavior changed.**

## Problem and current boundaries

`RestrictionsScheduler.tick()` and clock reconciliation track `temp_protected_packages` as package names. Successful normal transitions now commit after performing operations (PR #115). This marker does **not** retain the original restriction tuple when an Automation Schedule is deleted. Thus a deleted schedule may leave no trustworthy information for restoring the package. Failed `SharedPreferences.commit()` may still mutate in-memory preferences; the current rollback is best effort, not an atomic power-loss guarantee. PR #116 proves RTC alarm re-arming after real `TIMEZONE_CHANGED` but not exact firing or `TIME_SET`.

## Recovery record contract

Introduce a separate versioned, size-bounded package-scoped durable record containing validated package ID, source schedule IDs, original restorable background restriction tuple, applied-operation bitmap, state, timestamp, and monotonically advancing operation token. Reject corrupt, oversized, unknown-version and unauthorized records. Do not store executable command strings or external component actions.

Before relaxing a restriction, capture the actual original state and **durably commit the pending recovery record**. If capture or persistence fails, perform no privileged mutation. Record outcomes after each confirmed system operation. Recovery after a crash must be idempotent: inspect live state and avoid treating an attempted operation as completed.

A schedule deletion or disable action must not drop an unresolved recovery record. Restore only captured values; if unavailable for a legacy marker, report unresolved and **do not guess defaults**. Check package uninstall/reinstall, policy changes and permission readiness. Conflicting user edits move the record into a manual-resolution state instead of overriding the user's newer intent.

For overlapping schedules, maintain one package-scoped restoration owner with active-reference tracking so the original restrictions are restored only after the last eligible window closes. Define precedence before integration.

## Bounded retry contract

Retry only eligible failed lift/restore operations. Use capped exponential backoff, per-package attempt budgets and a single durable scheduled retry. Re-evaluate current schedules and real state each time. Transient shell unavailability is not permanent denial. An unavailable or unready Shizuku/root session must never mark success. Re-arm ordinary boundaries on exceptions and respect exact-alarm/Doze constraints.

**Never** replay configured component launches or force-stop merely because a clock change, schedule deletion or recovery retry occurred. Preserve existing markers and recovery records until genuinely resolved. Log only bounded non-sensitive diagnostics.

## Test and rollout gates

1. Pure JVM state-machine tests: DST gaps/overlap, overnight windows, duplicate packages, overlapping/deleted/disabled schedules, invalid input and record limits.
2. Inject failures before record commit, after commit, between privileged steps, during completion, and with in-memory-changing `commit(false)`; repeat after simulated process death. Prove no false completion and no loss of rollback data.
3. API24/API37 emulator acceptance: persistence, worker/alarm wake-ups, permission denial, process restart, multiple schedules, backup compatibility, and no component launches/force-stops from recovery.
4. Run `docs/QUALITY_GATES.md` checks and separate CodeQL/security review before merge of implementation.
5. Real root/Shizuku, OEM/physical device, actual power loss and clock changes, production signing and rollback stay distinct external acceptance; do not claim these from synthetic emulator tests.

## Sequencing

Implement a pure recovery-record model and tests first; add the durable store next; then integrate pre-mutation capture, idempotent restore, overlap/deletion and finally bounded retries. Legacy package-only markers cannot be safely auto-migrated into invented original tuples.

This document does **not** close the partial Phase 11 roadmap item. Independently investigate real platform `TIME_SET` with full emulator host-time restoration.
