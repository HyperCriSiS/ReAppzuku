# AI Session State

Updated: 2026-10-07

## Last completed work blocks

### Phase 9 legacy-policy activation

Main before the current editor block: `fce4efef9e8a9d50be473234e6aa4ab7428534e9`

- Activated retry-safe legacy-to-`AppPolicy` reconciliation on normal process startup.
- Room schema 13 records policy provenance so explicit rows are never replaced by migration-owned rows.
- Migration-owned rows are bound to a deterministic legacy-state fingerprint; stale snapshots immediately fall back to the bounded live legacy resolver until the next successful reconciliation.
- A valid snapshot disables direct legacy fallback for packages without a policy, preserving unmanaged behavior for newly installed apps until the setup-queue work lands.
- Auto-Kill and Smart Lifecycle remain mutually exclusive through `AppPolicyResolver`.

Validation on main before this block included green CodeQL and Android 17 / API 37 runtime coverage.

### Phase 9 per-app Policy Editor

Merged PR: #64  
Merge commit: `56797a6c0d66b8d127c08da2fbee3d3afd524fa7`

- Added a per-app lifecycle Policy Editor reachable from the main app options sheet.
- Editor covers canonical strategy, reusable preset, Smart standby/force-stop delays, Immediate kill method, boot cleanup, background restriction, media/foreground-service/widget protections, and all six trigger bits.
- Saving writes an explicit `AppPolicy`, so user edits override migration-owned legacy materialization without changing Room schema 13.
- Built-in presets use stable IDs with localized display names; user-created presets are supported with `Save as preset`.
- Existing customized preset-backed policies are preserved during spinner initialization instead of being overwritten by the selected preset.
- Policy normalization rejects unknown strategy/restriction/kill values, strips unknown trigger bits, preserves Smart “never force-stop”, and clamps Smart force-stop delay so it cannot precede standby.
- Added focused JVM regressions for preset application, normalization, never-force-stop semantics and preset round trips.
- Added Policy Editor localization for Spanish, Russian, Ukrainian and Simplified Chinese.
- Existing app-option checkbox behavior/tint is intentionally unchanged; an incidental draft change was detected in diff review and reverted before merge.

Validation:
- Standard validation: 36856861081
- CodeQL: 36856858307
- GitHub Advanced Security: 36856861855
- PR review threads: none at final review.

### Phase 9 Automation Schedules separation

Merged PR: #65  
Merge commit: `f37505e6035b67a30170a625e51d2994a8b75e32`

- Renamed the two user-facing time-window Auto-Kill preset surfaces to Automation Schedules.
- Added `AutomationScheduleManager` as the schedule-named UI/API facade over the legacy implementation.
- Kept historical persisted preference stores, alarm broadcast actions, backup prefix and serialized model fields unchanged so upgrades retain schedules and pending alarms.
- Kept Room-backed `PolicyPreset` semantically separate: it remains a reusable per-app behavior template and has no schedule start/end fields.
- Historical default names `Preset 1` / `Preset 2` render with localized Automation Schedule labels; new JSON exports use schedule terminology.
- Localized the renamed surface for English, Spanish, Russian, Ukrainian and Simplified Chinese.
- Added focused compatibility and source-authoritative separation regressions.
- No Room schema change and no automation execution rewrite.

Validation:
- Standard validation: `36862912195`
- CodeQL: `36862911361`
- GitHub Advanced Security: `36862917104`
- PR review threads: none at final review.

### Phase 9 durable new-app setup queue

Merged PR: #66  
Merge commit: `93502e2bd44c50dac5a2c9b2432258a928da9c0b`

- Added `PACKAGE_ADDED` handling with a durable Needs-setup queue and the three roadmap outcomes: Ask after install, Apply default preset, and Leave unmanaged.
- Ask is the default and performs no privileged lifecycle mutation. Ask and Leave unmanaged create an explicit `UNMANAGED` policy as a safety barrier against legacy fallback.
- Apply default preset is opt-in and may create a managed policy only after the user selects that mode; Balanced is the stored default preset choice until changed.
- Existing explicit policies are preserved. Migration-owned legacy policies do not count as user configuration for a reinstall and can be replaced by the new-app decision.
- Package IDs are validated; system, persistent and protected packages fail safe.
- The receiver ignores app updates via `EXTRA_REPLACING`, works asynchronously, and never opens UI over the foreground app.
- Ask notifications deep-link to the Policy Editor. A New app setup settings screen provides mode/default-preset controls, pending count and review-next fallback when notifications are denied or dismissed.
- Pending entries replay after legacy reconciliation at process start, survive process death/reboot, and prune packages that no longer exist.
- Successful Policy Editor saves clear the matching pending entry and notification.
- No Room schema bump was required; queue/mode/default-preset state is kept in existing app preferences.
- Updated the exported-component security review for `PackageAddedReceiver`.

Validation:
- Standard validation: `36919753802`
- CodeQL: `36919752974`
- GitHub Advanced Security: `36919755791`
- PR review threads: none at final review.

### Phase 9 main-list policy badges and filters

Merged PR: #67  
Merge commit: `c4bdeeae1a87cf8343410fcd83c30bf134f5e78d`

- Added canonical policy-status badges to the main running-app list: Managed · Smart, Managed · Immediate, Protected and Needs setup.
- Added Managed, Smart, Immediate, Protected and Needs setup filters to the existing sort dialog with OR semantics and persisted selection.
- Captures one immutable `AppPolicyListSnapshot` per scan on a background executor; Room and preferences are not queried per row.
- Display status follows the same explicit/migration/fallback boundary as execution through `AppPolicyLegacyMigrator` and `AppPolicyResolver`.
- Needs setup is driven only by the durable new-app queue. Protected/persistent fail-safe status takes display priority.
- Legacy whitelist/blacklist/Smart controls remain available and are not reinterpreted as a second ownership model.
- No Room schema change and no lifecycle execution rewrite.

Validation:
- Standard validation: `36926009009`
- CodeQL: `36935664025`
- GitHub Advanced Security: `36935665475`
- PR review threads: none at final review.


### Phase 9 transactional policy backup v7

Merged PR: #68  
Merge commit: `227f11057a16c8f8b4bfc63217cb06dbfb449631`

- Backup format v7 now carries explicit canonical `AppPolicy` rows, user-created `PolicyPreset` rows, new-app setup mode/default preset and the durable Needs-setup queue.
- Migration-owned rows and built-in presets remain compatibility/code-owned state and are intentionally not exported as portable user policy.
- Restore stages and validates Phase 9 relationships before durable writes, snapshots preferences plus Room policy state, and rolls both back after injected post-DB-commit failure.
- v6/older restore preserves existing Phase 9 state; generated output is also capped by the 2 MiB backup bound.

Validation:
- Standard validation: `36937049584`
- CodeQL: `36937623249`
- GitHub Advanced Security: `36937624956`
- PR review threads: none at final review.

### Phase 9 legacy settings UI retirement

PR: #69 (`phase9-remove-legacy-settings-ui`)  
Implementation head: `c7220467470540d04944674f24b2314d090f2430`

- Removed obsolete Settings UI for kill mode, whitelist, blacklist, global Smart Lifecycle enable, Smart app list, global Smart profile and global Smart boot cleanup.
- Kept historical preference keys/data as migration and restore compatibility inputs; no destructive cleanup is performed in this block.
- Canonical SMART execution is independent of the retired global Smart toggle.
- Smart periodic WorkManager state is reconciled from effective SMART ownership instead of being scheduled unconditionally; reconciliation runs after startup migration, Policy Editor saves, new-app decisions and restore/rollback.
- Smart manager exits before shell permission/dumpsys work when no package has effective SMART ownership.
- Legacy Smart alone no longer blocks On-demand behavior because canonical Smart automation is WorkManager-backed.
- Added source/JVM regressions for retired UI boundaries, retained compatibility keys, Smart worker reconciliation and updated continuity semantics.

Validation:
- Standard validation: `36947595322`
- CodeQL: `36947595766`
- GitHub Advanced Security: `36947596442`
- PR review threads: none at implementation-head review.

### Phase 9 completed-model Android 17 / API 37 validation

Merged PR: #73
Merge commit: `8533894b9716030609b148c675e5049312e10ed4`
Validated implementation head: `071186c8504541aea58345552923f1b1c8fcf3f1`

- Added completed-model instrumentation covering conflicting legacy Auto-Kill/Smart inputs, canonical ownership, new-app setup behavior, notification/editor deep link, Smart WorkManager reconciliation and backup-v7 restore/reconciliation.
- API 37 exposed that manifest-declared `PACKAGE_ADDED` is not a reliable modern-target delivery mechanism. Production now registers a process-lifetime package receiver and maintains a persisted installed-package inventory.
- A unique 15-minute WorkManager reconciliation provides durable fallback for installs that occur while the process is not alive; live receiver updates and reconciliation share inventory locking to avoid lost concurrent package observations.
- Startup order remains migration first, then new-app monitor/replay, then Smart scheduling reconciliation.
- The final API-37 gate at head `071186c8504541aea58345552923f1b1c8fcf3f1` passed in run `37149945404`. Earlier failed/cancelled runs were used to close deterministic startup/inventory races and align the boot idempotency test with canonical Phase 9 scheduling ownership.
- Physical/OEM coverage remains release-diversity evidence and is not yet recorded for this completed-model block.

Validation:
- Android 17 / API 37 runtime: `37149945404`
- Final standard validation and CodeQL passed on the PR head.
- GitHub Advanced Security agent execution failed only because the GitHub Copilot monthly quota was exhausted (HTTP 402); no code/security finding was reported, and `main` does not require that check as a merge gate.

## Current Phase 9 architecture state

- Canonical strategies are `UNMANAGED`, `PROTECTED`, `SMART`, `IMMEDIATE`; `Custom` remains presentation/provenance state, not an execution engine.
- Auto-Kill and Smart Lifecycle both use `AppPolicyResolver`; one package cannot be owned by both engines.
- Explicit Room-backed policy always wins over legacy migration/fallback.
- Retry-safe schema-13 migration is active and fingerprints remaining editable legacy state.
- Per-app policy editing and reusable user presets now exist without another Room version bump.
- Automation Schedules are explicitly separate from reusable per-app `PolicyPreset` templates; legacy schedule persistence identifiers remain compatibility-only.
- Newly installed apps now enter an explicit safe setup path: Ask/Leave are canonical `UNMANAGED`, while only an explicitly selected default preset can auto-manage an app.
- A durable Needs-setup queue is replayed after legacy migration and cannot be suppressed by migration-owned legacy rows on reinstall.
- The main app list now exposes effective canonical ownership from a per-scan snapshot; badges and filters use the same resolver/migration boundary as execution.
- Legacy whitelist/blacklist/kill-mode/Smart settings are now compatibility-only migration/restore inputs; their obsolete Settings UI has been retired.
- Background-restriction strength is stored canonically; exact manual AppOps/bucket/whitelist details remain in their existing per-package preference keys because they are device-operation detail rather than a second lifecycle ownership model.
- Backup v7 transactionally round-trips portable Phase 9 policy/preset/new-app state.
- Smart WorkManager scheduling follows effective SMART ownership and no longer turns a hidden legacy Smart bit into a permanent background-continuity requirement.

## Next work unit

**Release-diversity evidence + dependency maintenance**

1. Record at least one physical/OEM pass for the install-notification/deep-link path and canonical conflict resolution as release-diversity evidence.
2. Keep deterministic API-37 coverage as the implementation gate; do not block future source work on repeated emulator evidence already proven here.
3. Dependency PRs #71 (WorkManager 2.12.0) and #72 (Core KTX 1.19.1) currently fail only because strict Gradle verification metadata/locks have not been refreshed; update them through the reviewed dependency-assurance path before merge.
## Guardrails

- Never allow Smart and Immediate automation to own the same package simultaneously.
- Explicit Room-backed policy always has precedence over migration-owned or legacy fallback state.
- Newly installed apps default to no privileged mutation unless the user explicitly selects an automatic default preset.
- Protected/system/persistent packages continue to fail safe regardless of stored policy.
- Policy presets describe per-app behavior; Automation Schedules describe when automation is active. Do not merge those concepts.
- Keep retired legacy preference data only where migration/restore compatibility requires it; do not reintroduce retired lifecycle ownership UI.
