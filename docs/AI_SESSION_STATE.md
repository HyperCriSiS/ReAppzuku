# AI Session State

Updated: 2026-09-30

## Last completed work blocks

### Phase 9 legacy migration preparation

Merged PR: #59  
Merge commit: `3c435097eee6f9ed972dcabc5fa31e88fd7998ed`

- Added the idempotent legacy-to-`AppPolicy` migration planner/bridge.
- Existing explicit policies are preserved via insert-ignore.
- Legacy Smart-vs-Immediate conflicts resolve to `SMART`.
- Active/permanent/still-owned Sleep Mode packages resolve to `PROTECTED`.
- Whitelist mode is materialized against the currently installed eligible package set.
- Background restriction migration preserves strongest legacy ownership: MANUAL > HARD > MEDIUM > SOFT.
- Stable built-in preset IDs are seeded for Never touch, Messenger, Media, Balanced, Rarely used and Aggressive.
- Migration activation remains deliberately deferred because the old settings UI is still editable; activating a one-time snapshot before edit compatibility exists could create stale explicit policies.

Validation:
- Standard validation `36270576451` passed.
- CodeQL `36270709709` passed.

### Phase 9 Auto-Kill execution routing

Merged PR: #61  
Merge commit: `196ea1abe2acfe4a8c75b166ed21ea1832c0e889`

- Automatic Auto-Kill target ownership now goes through `AppPolicyResolver.shouldExecuteImmediate(...)`.
- Explicit Room-backed policy takes precedence over legacy whitelist/blacklist state.
- Legacy Smart Lifecycle ownership wins over legacy Immediate ownership for shared blacklist entries, preventing dual ownership.
- Automatic entry points carry concrete policy triggers for periodic, RAM threshold, screen-off, hardware event and app launch.
- Per-app Immediate `killMethod` is honored; invalid values fail safe to force-stop.
- Existing protected/persistent/foreground/scheduler safeguards, statistics and relaunch tracking remain intact.
- Added resolver regressions plus a source-authoritative trigger-routing contract.

Validation:
- Standard validation `36314992017` passed.
- CodeQL `36314991343` passed.
- GitHub Advanced Security `36314993786` passed.

### Phase 9 Smart Lifecycle execution routing

Merged PR: #62  
Merge commit: `e3c03a90114c49f8fe9e97de90e548d2f455b46a`

- Smart Lifecycle ownership now goes through `AppPolicyResolver.shouldExecuteSmart(...)`.
- The candidate reconciliation set contains legacy blacklist packages plus all explicit policy rows. Explicit non-SMART policies therefore remove legacy Smart ownership and clear stale Smart timing state.
- Explicit SMART policies use their own `standbyDelayMs` and `forceStopDelayMs`; legacy-owned packages keep the existing Gentle/Balanced/Aggressive profile delays.
- Invalid explicit delay values fail safe to the conservative `AppPolicy` defaults instead of inheriting a potentially more aggressive legacy profile.
- Force-stop delay is clamped so it can never precede the resolved standby delay.
- Explicit SMART boot cleanup is controlled by per-app `bootCleanup` plus `TRIGGER_BOOT_CLEANUP`.
- Legacy-owned packages retain the old global boot-cleanup switch during the transition.
- The boot worker always schedules the boot pass while the legacy Smart engine is enabled, so explicit policies can make their own boot-cleanup decision even when legacy global boot cleanup is disabled.
- Existing foreground, protected/system/persistent, media/widget/service, accessibility/notification-listener and recovery safeguards remain intact.
- The legacy global Smart Lifecycle switch remains the transitional master enable while the old settings UI still exists.
- Added resolver delay/ownership regressions and source-authoritative Smart routing contracts.

Validation:
- Final-head standard validation `36651682281` passed: unit tests, lint, AndroidTest compilation, Room schema verification and debug APK build/upload.
- Final-head CodeQL `36651678473` passed.
- Final-head GitHub Advanced Security `36651678716` passed.
- Android 17 / API 37 runtime `36652012427` passed on merge commit `e3c03a90114c49f8fe9e97de90e548d2f455b46a`: emulator boot, app/instrumentation/security-probe build and install, full instrumentation, external component abuse probe, launcher smoke test and evidence upload all passed.
- No PR review threads or code-review findings were present.

## Current Phase 9 architecture state

- Canonical strategies remain `UNMANAGED`, `PROTECTED`, `SMART`, `IMMEDIATE`.
- `Custom` is presentation state, not a fifth execution engine.
- Auto-Kill and Smart Lifecycle now both use `AppPolicyResolver` for per-app execution ownership.
- An explicit Room policy has precedence over legacy fallback in both engines.
- Smart and Immediate cannot simultaneously own the same package through the resolver.
- Legacy global settings are still present as transition controls/fallback and must not be removed yet.
- The one-time `AppPolicyLegacyMigrator` is implemented but still deliberately not auto-started.
- Room schema remains 12.

## Next work unit

**Atomic legacy-policy activation compatibility**

The execution-routing cutover is now proven, so the next blocker is safely activating the prepared legacy migration without allowing later edits in the still-existing legacy UI to diverge from materialized `AppPolicy` rows.

1. Audit every write path for legacy lifecycle state: whitelist/blacklist membership, Smart Lifecycle, Auto-Kill mode/state, Sleep Mode ownership and background-restriction settings.
2. Define one compatibility boundary for the remaining migration window. Preferred invariant: once explicit migration is activated, every relevant legacy edit that changes effective per-app ownership must update the canonical `AppPolicy` state atomically or explicitly invalidate/recompute the migrated row.
3. Do not simply call `AppPolicyLegacyMigrator.migrateIfNeeded()` at startup while legacy write paths can still create stale explicit rows.
4. Add regression coverage for post-migration edits, repeated migration, explicit-policy precedence and failure/retry behavior.
5. Only after the compatibility bridge is proven, activate the migration marker in production startup/reconciliation.
6. Then continue with the per-app Policy Editor, user presets, Automation Schedule rename/separation, new-app setup queue, badges/filters and backup/restore.
7. Update `docs/ROADMAP.md` Phase 9 execution-routing status to completed as part of the next documentation/compatibility commit if it is still shown as open.

## Guardrails

- Never allow Smart and Immediate automation to own the same package simultaneously.
- Explicit Room-backed policy always has precedence over legacy fallback.
- Do not activate the migration marker while legacy UI edits can silently diverge from already-materialized explicit policies.
- Newly installed apps default to no privileged mutation unless the user explicitly selects an automatic default preset.
- Protected/system/persistent packages must continue to fail safe regardless of stored policy.
- Keep the legacy global Smart Lifecycle switch as transitional master enable until the migration/UI cutover has a replacement scheduling contract.
- Do not remove legacy settings UI until migration, execution and backup/restore parity are proven.
