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
- Migration activation remains deliberately deferred because the old settings UI is still editable; activating a one-time snapshot before edit compatibility exists could create stale explicit policies.

Validation:
- Standard validation `36270576451` passed.
- CodeQL `36270709709` passed.
- Separate GitHub agentic reviewer failed before useful analysis because of its model/agent setup; no code finding was produced.

### Phase 9 trigger-aware routing guards

Merged PR: #60  
Merge commit: `3cf1aac1933573c4a008d22064339b99fefc744a`

- Extended `AppPolicyResolver` with trigger-aware Immediate execution gating.
- Explicit `IMMEDIATE` policies require their configured trigger bit; legacy fallback preserves the existing global-trigger behavior.
- Extended Smart routing with an explicit boot-pass guard requiring both `bootCleanup` and `TRIGGER_BOOT_CLEANUP`.
- Added JVM regressions for trigger matching, legacy compatibility and Smart boot cleanup.
- Kept the large Auto-Kill / Smart Lifecycle manager rewrite out of this PR so the routing semantics are independently testable.
- The one-time legacy migration is still not auto-started.

Validation:
- Standard validation `36313661973` passed: unit tests, lint, AndroidTest compilation, Room schema verification and debug APK build/upload.
- CodeQL `36313649760` passed.
- GitHub Advanced Security agent passed on the identical code head before the documentation-only checkpoint; the rerun on the checkpoint commit failed in Copilot model setup (`claude-opus-5`), not during code analysis.
- No PR review threads or code-review findings were present.

## Next work unit

**Wire Auto-Kill and Smart Lifecycle execution behind AppPolicyResolver**

1. Route `AutoKillManager` target selection through `AppPolicyResolver.shouldExecuteImmediate(...)`.
2. Pass the concrete trigger through periodic/RAM, screen-off, hardware-event and app-launch paths so explicit policies honor `triggerMask`.
3. Honor per-app Immediate `killMethod` while preserving protected/system safeguards, statistics and relaunch tracking.
4. Route `SmartLifecycleManager` package ownership through `AppPolicyResolver.shouldExecuteSmart(...)`; explicit SMART policies must coexist with legacy fallback without dual ownership.
5. Honor explicit Smart standby/force-stop delays and boot-cleanup behavior.
6. Add focused manager/worker regression coverage and run standard validation, CodeQL and API 37 for the completed execution-routing block.
7. Only after execution parity is proven, define the atomic compatibility mechanism for activating `AppPolicyLegacyMigrator.migrateIfNeeded()` while legacy UI can still edit settings.

## Guardrails

- Never allow Smart and Immediate automation to own the same package simultaneously.
- Explicit Room-backed policy always has precedence over legacy fallback.
- Do not activate the migration marker while legacy UI edits can silently diverge from already-materialized explicit policies.
- Newly installed apps default to no privileged mutation unless the user explicitly selects an automatic default preset.
- Protected/system/persistent packages must continue to fail safe regardless of stored policy.
- Do not remove legacy settings UI until migration, execution and backup/restore parity are proven.
