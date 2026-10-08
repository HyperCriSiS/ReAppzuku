# AI Session State

Updated: 2026-10-08

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

### Dependency assurance maintenance — 2026-10-07

- WorkManager was updated from 2.11.1 to 2.12.0 in PR #76 / merge `2982d13a7814f93492660976241207e777aae015`.
- Gradle 9.8.0 regenerated dependency locks and SHA-256 verification metadata from resolved artifacts before review; the final PR contains only the version change, two lockfile version replacements and the four new WorkManager artifact checksums.
- Branch-exact validation passed: standard `37553836083`, Android 17 / API 37 runtime `37553682771`, and CodeQL. The API-37 lane passed PACKAGE_ADDED fallback, general instrumentation, completed Phase 9 model validation, external component abuse probing and launcher smoke.
- GitHub Advanced Security agent execution again failed only because the Copilot monthly quota was exhausted (HTTP 402), not because of a code/security finding.
- Dependabot PR #71 was closed as superseded by #76. Core KTX 1.19.1 PR #72 was intentionally closed/deferred because its transitive dependency churn is not justified on the current AGP 9.4.1 line.

### Release evidence baseline refresh — 2026-10-08

- Phase 9 checkpoint PR #75 merged to `main` as `3edb0f0f6f56cbba266339aa608f69fcb8591f4a`; CodeQL run `37554275820` passed.
- Updated `CHECK_MATRIX.md` and `QUALITY_GATES.md` with the proven API-37/WorkManager 2.12.0 evidence and labeled older preview/backup/schema/toolchain facts as historical.
- Added `PHYSICAL_DEVICE_VALIDATION.md` as a bounded real-device and stable-signing acceptance plan. This is **not** evidence that the device/root/signing tests have run.
- Still open: real physical/OEM tests, actual Magisk/KernelSU root execution, stable signing identity, in-place same-key upgrade and proven rollback.

### Android 7 / API 24 minimum-SDK launch evidence — 2026-10-08

- PR #78 merged as `f50bf3451bf66953eb231d170f67ae1cf9efdf29` and added an isolated, manually dispatched Android 7/API 24 install-and-launch smoke workflow (`.github/workflows/android24-smoke.yml`). The lane uses read-only GitHub permissions, immutable checkout/JDK action SHAs, strict Gradle dependency verification and SHA-256-pinned Android command-line tools; it does not publish or change the existing API-37 lane.
- Initial run `37785733842` built the APK but did not reach emulator startup: Google CDN aborted the pinned command-line-tools download with `curl (92) HTTP/2 INTERNAL_ERROR`. This is infrastructure evidence, not an Android-7 application failure.
- PR #79 merged as `57ae109fec521886012642532e4f84ff4f7e043f` and hardened only the SDK download with HTTP/1.1, resumable transfer and bounded transient-error retries; pinned SHA-256 verification is unchanged.
- Corrected branch-exact run `37786168734` **PASSED**. It built the product APK, verified the pinned SDK archive, installed the API-24 Google APIs emulator image, booted and checked `ro.build.version.sdk=24`, installed the `targetSdk=37` debug APK, injected a launcher event, detected no immediate `AndroidRuntime` fatal for ReAppzuku, observed a live app process and verified `MainActivity` resumed/focused. Runtime log emitted `API24_INSTALL_PASS` and `API24_LAUNCH_SMOKE_PASS`.
- The tested APK SHA-256 was `aefbe950c70287749e05de8e4ac23cd3f02aac3e5ff0d8edebf197e2cf6eb0b5`.
- CodeQL passed for both PR heads; PR #79 had no review threads.
- **Scope boundary:** This is a minimum-SDK emulator *install/launch* smoke, not proof of API-24 lifecycle automation, privileged Shizuku/root operations, OEM compatibility, physical-device execution, production signing, in-place update or rollback.

### Android 7 / API 24 local persistence instrumentation — 2026-10-08

- PR #80 merged as `9972a9b9c2e450b4a06d2d966a3ec6ba9599816c`, extending the manual Android 7 / API 24 smoke workflow with the existing three bounded Android instrumentation suites: `BackupCodecTest`, `AppDatabaseMigrationTest` and `Phase9BackupRestoreTest`.
- Branch-exact runtime `37788629739` passed **OK (10 tests)** and `API24_LOCAL_PERSISTENCE_PASS`: verified Room v2→v13 and v12→v13 migration on API 24, backup-v7 explicit policies/presets/new-app settings roundtrip, v6 restore compatibility, injected post-DB-commit rollback and backup envelope rejection/compatibility.
- The same run additionally passed minSdk-24 build, pinned SDK tool digest, emulator boot, app APK install/targetSdk-37 check, launcher/crash smoke and AndroidTest APK install.
- Standard source/build/lint/Room validation passed in `37788815971`; pinned Java/Kotlin CodeQL passed on PR #80, which had no review threads.
- **Scope boundary:** API-24 local data compatibility and launcher startup are now runtime-tested. This is **not** evidence for API-24 Shizuku/root operations, periodic job/alarm execution, physical/OEM devices, stable signing, installed updates or rollback.

### App Behavior three-switch intent recovery — 2026-10-08

- Merged PR #81 as `c33cfede8344953f73444002e5b0331fe25216bc`, following a user report that the three App Behavior switches behaved inconsistently on an older installed build.
- Confirmed the destructive source behavior: activating an Auto-Kill/Sleep/schedule continuity blocker rewrote the persisted `exit_on_back` / `prevent_shizuku_autostart` choices to false. Clearing the blocker could not recover the user's prior choices.
- `BackgroundWorkPolicy.enforceCompatibleBehavior` now applies only **effective** blocking and Shizuku receiver synchronization, not destructive SharedPreferences writes. Requested settings survive blocker activation and are effective again after it clears.
- `SettingsActivity` shows the three **effective** On-demand / Prevent Shizuku auto-start / Exit on Back values and gates all three checked-change callbacks during programmatic refreshes. The On-demand master remains derived from the two fine-grained settings; it does not introduce a separate preference.
- The app-lifetime main-process preference listener re-synchronizes the Shizuku wake component for Auto-Kill, Sleep Mode, active Automation Schedule, restriction scheduler and user preference changes even while Settings is closed.
- Safety invariants remain: Back cannot terminate the main process while background continuity is needed; Shizuku wake stays enabled for blocked automation even if the user requested prevention; the isolated `:shizuku` provider stays minimal.
- Standard CI `37792179324` and pinned Java/Kotlin CodeQL passed. Android 17/API 37 focused runtime `37792190348` passed `OK (1 test)` exercising all three switches, persisted choices during a blocker, restoration after unblock, and receiver enabled/disabled state. Its external security probe and launcher smoke also passed. No review threads.
- **Upgrade caveat:** Choices already overwritten in an older installed build cannot be reconstructed automatically. The user must select them once after updating, unless their previous choice is available in a backup.

### App Behavior simplified On-demand settings — 2026-10-08

- Merged PR #82 as `47e2254316bb78f8c5c4931b5b1d8dd07ced1aa3` following user agreement that the three equally prominent App Behavior switches were redundant.
- On-demand mode is now the sole prominent switch. The two independent controls — Prevent Shizuku auto-start and Exit on Back — are collapsed by default under a keyboard/TalkBack-focusable Advanced settings row, with expansion retained on Activity recreation.
- The On-demand switch remains a convenience combination over the **existing** two SharedPreferences values. No third stored preference, migration or automation-execution semantics were introduced.
- When saved fine-grained values differ, the primary UI explicitly shows **Custom** rather than suggesting that the unchecked On-demand switch represents ordinary off behavior. Active automation shows the **saved** requested mode while all effective switches remain blocked/disabled; restoring the automation blocker does not modify preferences.
- Added localized strings in en/es/ru/uk/zh-CN, pure state-classification JVM tests, source/UI contract tests and API-37 Android UI instrumentation for expansion, custom state, retained choices and blocking/unblocking recovery.
- Corrected an initially truncated layout push **before merge**; final diff preserves all 67 prior settings view IDs, adding only the five intended App Behavior views.
- Final checks on `8e0ab34fe33d076091e97664aebe5d7de5d2c77d`: standard `37795423767`, CodeQL, API-37 runtime `37795436115` (`OK (1 test)`, external security probe PASS and launcher smoke). No PR review threads.

### Read-only installed-APK signing/upgrade preflight — 2026-10-08

- PR #83 merged as `b5578b25a05a78de2a831c24f06d655623c5088e`. It adds `scripts/release-upgrade-preflight.sh` for manually authorized physical-device update preparation, with no on-device mutation, uninstall, install, preference change or log upload.
- The script verifies the candidate ReAppzuku package and higher versionCode, `apksigner`-verified single-signer certificate SHA-256 against an independently pinned expected digest, and the installed package's base-APK identity and signer. The installed APK is copied only into a private host temp directory that is deleted on exit.
- It fails closed for wrong/mismatched signing identities (including historical random-debug-key installs), wrong package, equal/downgrade versionCode, absent installation, inaccessible/ambiguous installed APK or untrusted signing fingerprint.
- `scripts/tests/test-release-upgrade-preflight.sh` covers twelve mocked positive/negative scenarios and runs in the read-only standard CI before Gradle. Branch-exact standard validation `37797686424` passed (including APK build), CodeQL `37797684433` passed, and PR review threads were empty.
- `docs/PHYSICAL_DEVICE_VALIDATION.md` and `docs/RELEASE_SIGNING.md` specify the manual invocation and limitations. **No physical device was examined**. No production signing identity/secrets are present or validated, no in-place update was installed, and no data-survival or rollback scenario was exercised.

### Phase 10: emulator in-place same-signer upgrade — 2026-10-08

- PR #85 merged as `f72032e613436697e567bb8dd6a3658f38b20164` and extended `docs/ROADMAP.md` with independent software-only Phase 10 and UX/maintenance Phase 11 tasks. Physical/OEM, real Magisk/KernelSU and stable-release signing remain separate external acceptance.
- An opt-in `reappzukuUpgradeSmokeVersionCode` Gradle property modifies **debug variant output** versionCode only. Release version, signing identity and product automation semantics are unchanged.
- A pair of API-24 APKs built from one source revision using the runner's single ephemeral debug signing key were installed sequentially with different versionCodes (28 then 29). The workflow verifies candidate APK identity, single certificate SHA-256 agreement, different artifact SHA-256, actual `adb install -r` success and post-update APK version.
- Dedicated Android instrumentation seeds a durable SharedPreferences marker and explicit `PROTECTED` Room `AppPolicy` before the upgrade, then verifies the marker, source and Room policy survive after the in-place update in a new instrumentation process.
- Earlier diagnostic runs `37803066958` and `37804111029` built APKs but exposed an overly strict CI-only `apksigner` output parser; no Android update was attempted before these parser checks failed. The parser was corrected without weakening the signer-equality gate.
- Final branch-exact API-24 run `37804848630` passed, with `OK (1 test)` and `API24_SAME_KEY_UPDATE_DATA_PASS` after the positive in-place update. Existing ten API-24 backup/Room tests also passed. Source-equivalent standard `37803048460` passed unit/lint/AndroidTest/Room/APK and final-head CodeQL passed; no review threads.
- **Scope:** This proves an ephemeral-debug-key **emulator** in-place update and local data persistence, not stable production signing identity, physical/OEM behavior, downgrade recovery, release artifact upgrade or real rollback.

### Phase 10: negative Android-7 upgrade guards — 2026-10-08

- PR #87 merged as `845838d7530bc61603710f51be278770588de0d9` and extends the disposable API-24 CI lane after the existing verified same-signer debug update.
- A short-lived alternate test keystore resigns a candidate APK; Android must reject it with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. A normal attempted install of the earlier versionCode must independently fail with `INSTALL_FAILED_VERSION_DOWNGRADE`.
- After **each** failed install, the test verifies installed version 29 and reruns dedicated Android instrumentation over actual SharedPreferences and an explicit `PROTECTED` Room `AppPolicy`. No downgrade override, uninstall, clear-data, actual release key or external device is involved.
- Branch-exact `37818081642` passed positive same-key update (`API24_SAME_KEY_UPDATE_DATA_PASS`), wrong-signer rejection (`API24_WRONG_SIGNER_REJECTED_DATA_PASS`) and downgrade rejection (`API24_DOWNGRADE_REJECTED_DATA_PASS`), each with `OK (1 test)` following its relevant stage; existing API-24 backup/Room suite passed.
- Standard `37818073462` passed unit, lint, instrumentation compilation, Room schema check and APK build. CodeQL passed. No review threads. **Actual production signing identity, physical hardware, signature rotation and meaningful rollback/recovery remain untested.**

### Phase 10: offline release-metadata selection and hostile fixtures — 2026-10-08

- PR #89 merged as `8a839fc0be991fb141b3ea6c7e9360b81a160696` from `phase10/offline-release-metadata-assurance` and fixes an observed updater correctness bug: `UpdateChecker` previously picked the **first** eligible GitHub API release, not the highest numeric stable version. Unordered API metadata could hide a newer update. New pure-JVM `ReleaseMetadataPolicy` now chooses the highest valid stable release.
- Malformed/whitespace-padded/overlong version tags are rejected; drafts, prereleases and invalid metadata booleans do not qualify. All displayed release page URLs are derived locally from the fork and validated tag; the direct APK URL requires exact signed-release name/tag/path equality. Foreign asset URLs cannot become direct notification downloads.
- The API request now rejects HTTP redirects and bounds the metadata response before parsing (1 Mi chars), number of inspected releases (20), assets per release (64) and changelog text (16,384 chars). All checks are offline-JVM testable except network transport; no API endpoint, dependency locks, signing material or live device was changed.
- Branch-exact normal validation `37827922518` passed unit, lint, AndroidTest compilation, Room schema verification and APK build. Pinned CodeQL `37827914054` passed, zero PR review threads. Tests prove pure source/metadata policies, **not** hostile live HTTP/TLS conditions, physical upgrades, production signing or rollback.

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

**Phase 10 bounded API-24 policy-editor and new-app setup queue coverage**

1. Audit existing API-37 policy-editor and completed-model instrumentation for reusable targeted tests on API-24, avoiding full privileged/root/Shizuku flows.
2. Add Android 7/API-24 instrumentation for canonical per-app editor state and durable new-app queue operations, including explicit UNMANAGED safety, background notification/deeplink fallback where feasible, and data retention without clearing real user state.
3. Run source-authoritative standard CI and a **branch-exact, disposable** API-24 emulator test; ensure the prior positive and rejected-upgrade gates still pass.
4. Review security/CodeQL results, merge only passing work, and checkpoint documentation. Keep later Phase-10 scheduling/backup/UX assurance as separate PRs.

**External evidence remains out of scope:** physical/OEM devices, real Magisk/KernelSU, secure production signing key, actual stable-release upgrade and rollback.

## Guardrails

- Never allow Smart and Immediate automation to own the same package simultaneously.
- Explicit Room-backed policy always has precedence over migration-owned or legacy fallback state.
- Newly installed apps default to no privileged mutation unless the user explicitly selects an automatic default preset.
- Protected/system/persistent packages continue to fail safe regardless of stored policy.
- Policy presets describe per-app behavior; Automation Schedules describe when automation is active. Do not merge those concepts.
- Keep retired legacy preference data only where migration/restore compatibility requires it; do not reintroduce retired lifecycle ownership UI.

### Phase 10: API-24 new-app setup queue persistence (2026-10-08)

- PR #90 on `phase10/api24-newapp-queue-safety` adds two scoped instrumentation checks for durable setup queue persistence, duplicate prevention, invalid package rejection, isolated removal, and safe ASK default mode; touched preferences are restored without Room deletes or privileged actions.
- Branch-exact API-24 emulator run `37833638389` passed all steps: launcher/install, migration/backup/new-app instrumentation, same-debug-key version upgrade, rejected alternate-signer install, and rejected downgrade, preserving policy state.
- Standard run `37833699742` passed unit, lint, AndroidTest compilation, Room schema, APK and read-only release preflight. CodeQL `37833755180` passed with no review threads.
- Not yet covered: API-24 Policy Editor UI, in-app review-next recovery, notification/intent delivery, real physical devices, Shizuku/root, or production signing. Continue separately.

### Phase 10: API-24 policy-editor cancel and review navigation (2026-10-08)

- PR #91 on `phase10/api24-policy-editor-navigation` adds targeted non-privileged Android instrumentation for per-app Editor deep links, cancellation without saving/dismissing setup, and Review-next navigation to the correct pending package. Production code, dependencies and data schema are unchanged.
- Diagnostic API-24 run `37836212363` reached 13/14 tests: a synthetic uninstalled queue fixture was correctly pruned at settings startup. Repaired test head `e0faf466` uses the installed disposable instrumentation APK for review routing and restores its prior mode, queue membership and policy row instead of weakening production pruning.
- Final branch-exact API-24 run `37836914489` PASSED `OK (14 tests)` including positive same-debug-key update, wrong-signer rejection and downgrade rejection with saved data retained. Standard `37836921627` and CodeQL `37836908527` PASSED; review threads were empty before documentation.
- Still open: complete editor-save behavior, notification fallback and delayed delivery, process-death scheduling injection, physical/OEM and real Shizuku/root, actual release signing and rollback. No physical device or production signing key was used.

### Phase 10 API24 editor-save and notification follow-up (2026-10-08)

- PR #92 branch `phase10/api24-editor-save-notification` adds scoped Android 7 instrumentation for actual explicit PROTECTED policy persistence/reopening and isolated setup-queue removal, plus pre-Oreo notification tap routing without implicit policy saves. No production code, permissions, schema, or dependency changes.
- Standard unit/lint/AndroidTest/Room/APK run `37839043607` PASSED. Tested-code CodeQL `37839029338` and documentation-head CodeQL `37839838958` PASSED. Initial emulator run `37839036686` passed the 16 instrumentation tests but stalled during the second Gradle build and was explicitly cancelled; no application regression was identified there. Source-identical branch-exact retry `37841075862`, checking out commit `81779e2747e9288aeea0b185be5657ff6030f4e4`, PASSED `OK (16 tests)` and all remaining same-debug-signer upgrade, retained Room/preferences, rejected alternate-signer and downgrade checks. The replacement run completed green; its result does not provide physical-device or production signing evidence.
- The test writes and cleans up only test-owned synthetic package policy/queue/notification state. Physical/OEM, real root/Shizuku, production signing, actual release update and rollback are still OUT OF SCOPE.
- Review of the source/test/workflow changes and absence of PR review threads was completed. Final documentation-only head CodeQL must be rechecked before the squash merge; once merged, the checkpoint and Roadmap belong to `main`. Next bounded roadmap item is background scheduling recovery under failure injection, not a speculative production behavior change.
