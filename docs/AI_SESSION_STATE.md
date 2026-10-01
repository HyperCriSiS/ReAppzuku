# AI Session State

Updated: 2026-10-01

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
- Legacy global settings remain transition controls/fallback and must not be removed until remaining Phase 9 parity work is complete.
- Background-restriction strength is stored canonically, while exact manual AppOps/bucket/whitelist legacy details still remain in their existing per-package preference keys pending backup/UI parity work.

## Next work unit

**Transactional backup/restore for Phase 9 policy state**

1. Extend the versioned backup format to include canonical `AppPolicy` rows, reusable `PolicyPreset` rows and new-app setup mode/default-preset/Needs-setup queue state.
2. Preserve the existing transactional restore contract: stage and validate everything before the first durable write, then commit policy DB + preferences as one recoverable unit with rollback on failure.
3. Validate package identifiers, strategies, preset IDs/relationships, trigger masks, delay bounds, restriction strength and queue contents before importing.
4. Keep built-in preset IDs stable and prevent imported user presets from silently overwriting incompatible built-ins.
5. Reconcile legacy migration fingerprint/new-app queue state after a successful restore without allowing Smart and Immediate dual ownership.
6. Add focused parser/rollback/JVM coverage plus Android restore-path coverage before considering legacy settings UI removal.

## Guardrails

- Never allow Smart and Immediate automation to own the same package simultaneously.
- Explicit Room-backed policy always has precedence over migration-owned or legacy fallback state.
- Newly installed apps default to no privileged mutation unless the user explicitly selects an automatic default preset.
- Protected/system/persistent packages continue to fail safe regardless of stored policy.
- Policy presets describe per-app behavior; Automation Schedules describe when automation is active. Do not merge those concepts.
- Keep legacy UI/settings until migration, execution and backup/restore parity are proven.