# AI Session State

Updated: 2026-09-27

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
- Migration activation remains deliberately deferred: the old settings UI is still editable, so precomputing explicit policies now could make the policy snapshot stale after later legacy edits.

Validation for PR #59:
- Standard validation `36270576451` passed.
- CodeQL `36270709709` passed.
- Separate GitHub agentic reviewer failed before useful analysis because of its model/agent setup; no code finding was produced.

### Phase 9 execution-routing foundation

Current PR: #60  
Branch: `feature/policy-execution-routing`  
Head before this checkpoint: `ef9e785e29c14a269dedbf434dfd30b9736c5040`

- Extended `AppPolicyResolver` with trigger-aware Immediate execution gating.
- Explicit `IMMEDIATE` policies now require their configured trigger bit; legacy fallback keeps compatibility with the existing global trigger configuration.
- Extended Smart routing with an explicit boot-pass guard requiring both `bootCleanup` and `TRIGGER_BOOT_CLEANUP`.
- Added JVM regressions for trigger matching, legacy compatibility and Smart boot cleanup.
- The large Auto-Kill / Smart Lifecycle manager rewrite is intentionally not part of this PR; this checkpoint keeps the first routing unit small and independently testable.
- The one-time legacy migration is still not auto-started. Activation should only happen when editing/migration compatibility is solved atomically.

Validation status for PR #60 at checkpoint creation:
- GitHub Advanced Security agent: passed.
- Standard validation `36313593139`: running.
- CodeQL `36313541735`: running.
- No review threads or review findings were present.

## Next work unit

**Wire the execution engines behind AppPolicyResolver**

1. Route `AutoKillManager` target selection through `AppPolicyResolver.shouldExecuteImmediate(...)`.
2. Pass the concrete trigger type through periodic/RAM, screen-off, hardware-event and app-launch execution paths so explicit policies honor `triggerMask`.
3. Honor per-app Immediate `killMethod` without regressing protected/system package safeguards or existing statistics/relaunch tracking.
4. Route `SmartLifecycleManager` package ownership through `AppPolicyResolver.shouldExecuteSmart(...)`; explicit SMART policies must coexist with the legacy fallback without dual ownership.
5. Honor explicit Smart standby/force-stop delays and boot-cleanup behavior.
6. Add focused manager/worker regression coverage, then run standard validation, CodeQL and API 37 for the completed execution-routing block.
7. Only after execution parity is proven, decide the atomic compatibility mechanism for activating `AppPolicyLegacyMigrator.migrateIfNeeded()` while legacy UI can still edit settings.

## Guardrails

- Never allow Smart and Immediate automation to own the same package simultaneously.
- Explicit Room-backed policy always has precedence over legacy fallback.
- Do not activate the migration marker while legacy UI edits can silently diverge from already-materialized explicit policies.
- Newly installed apps default to no privileged mutation unless the user explicitly selects an automatic default preset.
- Protected/system/persistent packages must continue to fail safe regardless of stored policy.
- Do not remove legacy settings UI until migration, execution and backup/restore parity are proven.
