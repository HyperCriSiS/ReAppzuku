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

### Phase 9 Auto-Kill execution routing

Merged PR: #61  
Merge commit: `196ea1abe2acfe4a8c75b166ed21ea1832c0e889`

- Automatic Auto-Kill target ownership now goes through `AppPolicyResolver.shouldExecuteImmediate(...)`.
- Explicit Room-backed policy takes precedence over legacy whitelist/blacklist state.
- Legacy Smart Lifecycle ownership still wins over legacy Immediate ownership for shared blacklist entries, preventing dual ownership during the migration window.
- Automatic entry points now carry the concrete policy trigger:
  - periodic -> `TRIGGER_PERIODIC`
  - RAM-threshold -> `TRIGGER_RAM_THRESHOLD`
  - screen-off -> `TRIGGER_SCREEN_OFF`
  - hardware event -> `TRIGGER_HARDWARE_EVENT`
  - app launch -> `TRIGGER_APP_LAUNCH`
- Explicit `IMMEDIATE` policies therefore execute only for enabled trigger bits.
- Per-app `killMethod` is honored for explicit Immediate policies. Invalid explicit or legacy values fail safe to `force-stop`.
- Existing hidden/protected/current-foreground/scheduler safeguards remain before policy execution; persistent apps are rejected fail-safe in both legacy targeting modes.
- Statistics, pending-RAM accounting and relaunch detection remain on the existing path.
- Added resolver regressions plus a source-authoritative routing contract that locks all automatic trigger entry points to their policy bits.
- Smart Lifecycle execution is deliberately not modified in this block.
- The one-time legacy migration remains deliberately inactive until legacy UI editing and explicit policy persistence can be made atomic.

Validation:
- Standard validation `36314992017` passed: unit tests, lint, AndroidTest compilation, Room schema verification and debug APK build/upload.
- CodeQL `36314991343` passed, including analyzed-source build.
- GitHub Advanced Security agent `36314993786` passed.
- No PR review threads or code-review findings were present.
- Git blob identities for all eight changed files were verified against the locally reviewed source before CI.

## Next work unit

**Route Smart Lifecycle execution through AppPolicyResolver**

1. Build the managed package set from explicit `SMART` policies plus legacy fallback packages that resolve to SMART; explicit non-SMART policies must remove legacy ownership.
2. Route every candidate through `AppPolicyResolver.shouldExecuteSmart(...)` so Smart and Immediate cannot own the same package.
3. For explicit SMART policies, use per-app `standbyDelayMs` and `forceStopDelayMs`; keep existing profile-derived delays only for legacy fallback.
4. On boot passes, honor explicit `bootCleanup` + `TRIGGER_BOOT_CLEANUP`, while retaining the existing global legacy boot-cleanup switch for legacy-owned packages.
5. Preserve foreground, media, widget, foreground-service, accessibility/notification-listener, persistent/system and recovery safeguards.
6. Add focused Smart routing/delay regressions and a source-authoritative ownership contract.
7. Run standard validation and CodeQL for the Smart block.
8. After Auto-Kill + Smart routing are both merged, run the Android 17 / API 37 runtime lane for the completed execution-routing cutover.
9. Only after execution parity is proven, define the atomic compatibility mechanism for activating `AppPolicyLegacyMigrator.migrateIfNeeded()` while legacy UI can still edit settings.

## Guardrails

- Never allow Smart and Immediate automation to own the same package simultaneously.
- Explicit Room-backed policy always has precedence over legacy fallback.
- Do not activate the migration marker while legacy UI edits can silently diverge from already-materialized explicit policies.
- Newly installed apps default to no privileged mutation unless the user explicitly selects an automatic default preset.
- Protected/system/persistent packages must continue to fail safe regardless of stored policy.
- Do not remove legacy settings UI until migration, execution and backup/restore parity are proven.
