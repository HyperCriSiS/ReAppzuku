# AI Session State

Updated: 2026-10-10

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

## Previous API-24 work unit (completed by PRs #90-#92)

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

### Phase 10: deterministic new-app reconciliation recovery (2026-10-08)

- PR #93 (`phase10/new-app-reconcile-failure-recovery`) makes the existing inventory merge into a pure, package-local helper without changing its union/prune semantics. Four JVM tests inject a handler failure followed by successful retry, a concurrent live-package observation, duplicate event delivery and removed-package pruning; all input sets remain unmodified.
- A new non-privileged Android instrumentation test checks that repeated `NewAppInstallMonitor.schedulePeriodic` calls retain one live persisted WorkManager task with the same UUID under `ExistingPeriodicWorkPolicy.KEEP`. It only cancels the test-created task when none existed at entry.
- Branch-exact standard `37849871675` PASSED unit/lint/AndroidTest compilation/Room schema/APK. API 24 `37849877422` PASSED `OK (17 tests)` and its same-debug-key 28-to-29 update, wrong-signer and downgrade rejection checks. API 37 targeted run `37849884333` PASSED `OK (1 test)` plus its existing launcher/external security steps. Java/Kotlin CodeQL `37849864623` PASSED on functional head `53971aad`. Final documentation-only CodeQL remains to be reviewed before merge.
- Evidence boundary: simulated processing errors + deterministic merge, repeated enqueues and durable unique WorkManager metadata. NOT proven: killing/restarting the process, actual periodic Worker retry execution, delayed special-permission grant, OEM/physical operation, root or stable release signing.

## Previous next work unit (permission transition addressed; process loss still open)

Phase 10 follow-up: bounded Android 24/37 process-death and delayed-permission recovery tests with read-only state checks and no privileged mutation. Keep backup-v7 corrupt/content-URI testing as an independent later unit. Avoid destructive preference/Room resets and actual release-key/physical-device claims.

### Phase 10: exact-alarm permission transition on API 37 (2026-10-08)

- PR #94, branch `phase10/exact-alarm-delayed-grant`, adds a test-only scheduler regression class and two scoped workflow changes. No production code, permissions, dependencies, Room state or user policies were modified.
- API24 emulator `37851874765` PASSED `OK (20 tests)` including the pre-Android-12 exact-alarm control, same-debug-key in-place upgrade with preserved data and rejected wrong signer/downgrade. Standard unit/lint/AndroidTest/schema/APK `37851868722` PASSED. Tested-functional-head CodeQL `37851863510` PASSED.
- API37 targeted runtime `37851880052` PASSED two distinct `OK (1 test)` instrumentation sessions: special-access AppOp `ignore` -> scheduler returned `BEST_EFFORT`; changing the AppOp to `allow` -> scheduler returned `EXACT`. Both requests were scheduled one day ahead and cancelled in `finally`; CI restored the original AppOp mode in an EXIT trap. External security-probe and launcher steps also passed.
- Limitation: this is a deterministic emulator AppOp transition, not a real user's settings UI, background-delivery reliability, real process death/restart, physical/OEM compatibility, Shizuku/root or production release/signing evidence. Roadmap stays partial; check final documentation-head CodeQL before merge.

## Previous next work unit (addressed by PR #95)

Phase 10: persisted WorkManager UUID across process death and relaunch was tested on disposable API24/API37 emulators; Worker execution and OEM background eviction remain separate questions.

### Phase 10: real PID death and WorkManager durable identity (2026-10-09)

- PR #95, branch `phase10/api24-api37-real-process-restart`, adds two isolated non-privileged Android instrumentation phases and a CI-controlled PID transition on API24 and API37. No production Java, manifest permissions, data schema, migration, default configuration or dependency changes.
- First instrumentation phase obtains a persisted `NewAppInstallReconcile` unique periodic WorkManager UUID and reports it with a test-owned ownership flag. The workflow launches MainActivity, records the debug app main PID, sends SIGKILL via `run-as` at the **app's own UID**, asserts that the original PID is gone, restarts MainActivity and asserts a new PID. A second instrumentation session reads the persisted WorkManager UUID **before** any test-induced enqueue, verifies the same UUID, checks idempotence after enqueue and only cancels a task if the test created it.
- The first CI revisions failed because ActivityManager `am kill` did not kill the background app on API24, while API37's short Monkey launch/background step left no process. The next diagnostic revision exposed shell quoting that made `run-as sh -c` call `kill` incorrectly on API24. The final source-and-workflow revision `797f405b` instead launches MainActivity with `am start -W` and calls `run-as <package> /system/bin/kill -9 <pid>` directly.
- **Green final evidence:** API24 run `37854926964` proved original PID 4314 terminated, replacement PID 4379, both `OK (1 test)` phases and `REAPPZUKU_PROCESS_RESTART_WORK_UUID_PRESERVED`, followed by green in-place same-debug-signer upgrade, wrong-signer and downgrade rejection. API37 targeted run `37854932411` proved PID 4975 -> 5039 and both `OK (1 test)` phases with UUID preserved, plus external security abuse probe and launcher. Standard unit/lint/schema/AndroidTest/APK run `37853204763` and functional-head CodeQL `37854923832` succeeded.
- This is explicit **debug-app SIGKILL/relaunch**, not force-stop, low-memory killer (LMK) validation, OEM background eviction, real WorkManager `doWork()` delivery or periodic retry execution, root/Shizuku authorization, physical devices or production release signing. Preserve these external evidence limits.
- Before merging #95, inspect the intended test/workflow/docs diff, check absence of review threads and require final documentation-head CodeQL SUCCESS.

## Previous next work unit (debug Worker retry covered by PR #96)

An isolated debug-only WorkManager retry and successful resumed execution were validated. The actual production worker is not yet failure-injected, and physical devices remain untested.

### Phase 10: debug WorkManager retry execution after process death (2026-10-09)

- PR #96 (`phase10/debug-worker-retry-after-process-loss`) adds `app/src/debug/java/com/gree1d/reappzuku/core/DebugWorkRetryProbeWorker.java` **only to the debug build**. Its first `doWork()` invocation returns `Result.retry()`; a subsequent run returns `Result.success()` with distinctive output `executed_after_retry`. No release-variant worker, production schema, permissions, user preferences, release signing or dependency changes.
- The dedicated Android instrumentation starts unique test-only one-time work with 30-second linear backoff, verifies the FIRST `Result.retry()` before process death, then CI executes a verified **same-app-UID SIGKILL** and MainActivity relaunch (not Android Force-Stop), checks a new PID, and starts separate instrumentation that verifies the same persistent UUID, a nonzero run attempt count, SUCCEEDED state and worker-produced output. The entire workflow asserts the independent `REAPPZUKU_RETRY_SUCCESS=yes` result marker. It cancels only the dedicated CI work on completion; all real app policies and jobs are untouched.
- The initial workflow commit `4beba5e` had a YAML indentation error and never dispatched emulator tests; corrected at `9c7cc0a` before validation. Branch-exact API24 run `37861331700` PASSED with PID 4142 -> 4204, retry success marker and full same-debug-key upgrade, alternate-signer rejection and downgrade tests. Branch-exact API37 run `37861336462` PASSED with PID 4946 -> 5024, retry success marker, security-probe and launcher checks.
- Standard unit/lint/AndroidTest compile/Room schema/debug APK run `37861273310` PASSED (functional code on source-identical `4beba5e`). Functional final-head CodeQL `37861329026` PASSED. Require CodeQL on documentation-only head before squash merging.
- Evidence boundary: **WorkManager scheduling + retry dispatch is real but the Worker is a synthetic debug-only worker**, not `NewAppInstallReconcileWorker` running with a real installed-app inventory. Still unproven: actual production worker retry after injected package-processing failure, exact 15-minute periodic execution, device/OEM LMK behavior, root/Shizuku, production signing and rollback.

## Previous next work unit (backup-v7 coverage addressed by PR #97)

Targeted malformed-v7 restore, private content-URI denial and first-commit rollback were verified on disposable emulators. Production-worker fault injection remains open.

### Phase 10: non-destructive backup-v7 import and private URI boundaries (2026-10-09)

- PR #97, branch `phase10/api24-api37-backup-v7-boundary`, adds a dedicated CI-gated Android instrumentation class plus an **androidTest-only nonexported ContentProvider**. It does not change the production Java code, production manifest, permissions, Room schema, dependency versions, keys or release signing.
- The first test feeds truncated JSON, missing/inconsistent v7 Phase9 sections and invalid fields to `BackupManager.restoreBackupJson`, expecting rejection **before any durable commit** and asserting unchanged complete SharedPreferences key/value snapshots plus unchanged Room policy/preset counts. The private URI test verifies the target app cannot read or write a `content://` provider in the separately installed test APK (different UID, `exported=false`); existing preferences remain unchanged. The rollback test injects a fault at `AFTER_MAIN_COMMIT` during a structurally valid v7 import and verifies all original preference values/key presence and unchanged Room row counts. No `clear()`, package uninstall or blanket Room delete occurs in the three new probes.
- Evidence: branch-exact standard unit/lint/AndroidTest/schema/APK `37864010656` SUCCESS and tested-source CodeQL `37864004476` SUCCESS; Android 7/API24 workflow `37864015667` SUCCESS including the dedicated three tests plus preserved same-key update, wrong-signer and downgrade rejection; Android 17/API37 targeted workflow `37864020498` SUCCESS, `OK (3 tests)`, followed by unchanged security-probe and launcher checks. Check final **documentation-head CodeQL** before merging.
- Scope boundary: source-format and test-provider permission denial plus rollback immediately after MAIN commit, not a full SAF/DocumentsUI permission flow, provider failures after partial successful bytes, physical/OEM grant handling, or newly tested after-Phase9-DB rollback. The previously existing destructive-isolated `Phase9BackupRestoreTest` covers the deeper rollback independently, but its fixture is not reused here.

## Previous next work unit (completed by PR #98)

A test-only provider with partial data delivery followed by a real I/O failure now has disposable emulator evidence. SAF grant/revocation UX remains open.

### Phase 10: partial ContentResolver provider failure after bytes (2026-10-09)

- PR #98 (`phase10/api24-api37-backup-v7-stream-failure`) adds **androidTest-only**, exported-but-strictly-read-only `FaultyBackupStreamProvider` with fixed synthetic content URI paths, a CI-gated 3-test class and two targeted emulator workflow steps. Release/source app code, manifest, permissions, dependencies and schemas remain unchanged; no private user URI, root/Shizuku or policy mutation.
- The provider uses Android API19+ `ParcelFileDescriptor.createReliablePipe()` to stream a complete synthetic v7 JSON document (>12 KiB) before calling `closeWithError`. The test proves >8192 bytes arrived before **real IOException**, then separately checks that `BackupFileStore.read` propagates IOException instead of yielding any partial or valid import String. A normal EOF after a truncated JSON body must be rejected by `BackupManager.restoreBackupJson`, and a complete control stream must decode through `BackupCodec` without importing.
- An initial API24 diagnostic run `37865738281` failed in PR #97's PRE-EXISTING post-commit rollback test: concurrent new-app inventory initialization independently wrote `new_app_known_packages` between its before/after preference snapshots. Updated head `09829248` excludes **only** this asynchronously maintained **nonportable inventory cache** from both old and new backup boundary snapshot comparators; every other preference and Room policy/preset count remains asserted. No production test behavior was weakened and no config/cache is manually cleared.
- The final branch-exact API24 run `37866118734` PASSED the three new streaming tests, prior v7 boundary tests, safe WorkManager recovery, same-key upgrade and rejected wrong-signer/downgrade checks. API37 targeted run `37866124868` PASSED `OK (3 tests)` plus external security and launcher checks. Standard unit/lint/AndroidTest/Room/APK `37866129166` and tested-functional-head CodeQL `37866103997` PASSED. Final documentation-head CodeQL must pass before merge.
- Evidence boundary: this proves ContentResolver transport error propagation and corrupted/partial JSON rejection **without invoking a restore transaction for a failed read**; not SAF/DocumentsUI grant/revoke UX, full source-visible restore UI integration, physical/OEM content providers, or a new post-Phase9-DB rollback test. The provider is exported only from a disposable instrumentation APK and serves read-only synthetic fixtures.

### Phase 10: installed exported-component manifest boundaries (2026-10-09)

- PR #99 (`phase10/exported-component-manifest-regression`) adds read-only `ExportedComponentManifestInstrumentationTest` and includes it in the existing API-24 instrumentation suite. It interrogates the **installed, merged** manifest through PackageManager: private Policy Editor/settings and trigger receivers; intended exported launcher/system/widget entry points; required Accessibility and Quick Settings binding permissions; and Shizuku provider authority and read/write binding permissions. No production manifest, Java code, policy data, database, dependency, permission or release-signing behavior changes.
- The first diagnostic emulator runs `37869355728` (API24) / `37869360030` (API37) found an instrumentation-only `NameNotFoundException`: `ShizukuWakeReceiver` is intentionally runtime-disabled for On-demand behavior. The test now uses `PackageManager.MATCH_DISABLED_COMPONENTS` when auditing receivers/providers. A subsequent instrumentation compile check identified the test's incorrect `ProviderInfo.permission` field; repaired to check `readPermission` and `writePermission` separately.
- **Passing functional-head evidence (`e37788a`):** standard unit/lint/AndroidTest compilation/Room/APK `37873215584` SUCCESS; Java/Kotlin CodeQL `37873200529` SUCCESS; full Android 7/API24 `37873206394` SUCCESS (including the new installed-manifest checks, backup safeguards, process restart/Worker retry, positive same-debug-key update, wrong-signer and downgrade refusal); targeted Android 17/API37 `37873211270` SUCCESS (new instrumentation, separate-UID external component abuse probe and launcher crash smoke).
- These checks prove the declared installed-component exposure and tested Android-enforced caller gates on disposable emulators, **not** physical/OEM behavior, all privileged workflows, actual production signing or release rollback. The existing external-probe test remains the direct foreign-app abuse evidence; package metadata inspection alone is not an attempted external launch.

### Phase 10: actual new-app WorkManager retry after debug-injected post-pass error (2026-10-09)

- PR #100 (`phase10/production-reconcile-worker-retry-proof`) instruments the **actual** `NewAppInstallReconcileWorker` class with a build-variant-specific seam. Only the `debug` source set implements an explicit WorkManager-Data opt-in that injects a single `RuntimeException` **after** `NewAppInstallMonitor.reconcileInstalledPackages` completed its normal pass; `release` has an unconditional no-op stub and returns `Data.EMPTY`. The standard validation workflow now compiles release-variant Java without signing/publishing to catch accidental release-stub breakage.
- Two opt-in Android instrumentation phases enqueue a unique one-time WorkRequest using the REAL worker with linear retry backoff, verify `Result.retry()` was reached, then the CI host kills the main debug-app PID with SIGKILL under its **own UID**, proves PID replacement, and verifies in a new instrumentation session that the persisted UUID reaches `SUCCEEDED` with run-attempt count >=1 and the worker-generated post-reconciliation output marker. Test-owned unique work is cancelled on completion; the test does not clear Room, preferences or installed-package inventory.
- **Passing functional-head evidence `843a0661`:** complete Android 7/API24 `37875292241` SUCCESS (including previous backup, process-death, synthetic Worker retry, same-debug-key upgrade, wrong-signer and downgrade guards); targeted Android 17/API37 `37875296175` SUCCESS (two-phase real Worker retry, separate-UID external security probe and launcher smoke). Standard debug/unit/lint/Room/APK `37875288280` and CodeQL `37875278874` also passed.
- **Passing release-compile CI head `ab92d628`:** standard `37875726931` SUCCESS including nonpublishing `:app:compileReleaseJavaWithJavac`; Java/Kotlin CodeQL `37875724510` SUCCESS.
- **Scope boundary:** this proves the debug build's REAL production Worker class performs an actual reconciliation pass, retries an injected **post-pass** exception, and subsequently completes across an observed SIGKILL/relaunch. It does NOT inject a failure inside `NewAppSetupCoordinator.handlePackageAdded`, establish per-package failure retry delay, test actual 15-minute periodic dispatch, OEM/LMK eviction, physical devices, root/Shizuku or a signed stable release/rollback.

### Phase 10: per-package new-app handler failure propagation and retry (2026-10-09)

- PR #101 (`phase10/newapp-per-package-failure-retry`) fixes a real bug: `NewAppInstallMonitor.reconcileInstalledPackages` formerly caught every per-package `RuntimeException`, omitted the failed package from inventory, then returned normal success to `NewAppInstallReconcileWorker`. Its existing `Result.retry()` path was therefore not reached until another periodic scan.
- The reconciliation now accumulates package-local completion and failure counts, keeps processing unrelated packages, commits all successful outcomes using the existing concurrency-safe inventory union/prune, **then throws once** if any handler failed. Worker `Result.retry()` applies backoff and sees only unrecorded packages on the next pass. Startup remains fail-safe; no policy strategy, permissions, database schema, dependency, notification routing or periodic interval changed.
- Pure JVM tests cover mixed-success/failure batches, all-failed batches, immutable input/success snapshots, an empty pass and retry processing only previously failed packages. The opt-in Android instrumentation runs the **real Worker** with a debug-only first-attempt fault at a per-package dispatch boundary; the test package is ReAppzuku itself, whose coordinator call is a no-op. Only its entry in the nonportable known-package cache is temporarily removed/restored; no real user policy, setup queue, notification, root/Shizuku or package install operation is touched. Release variant keeps a no-injection stub.
- Initial API24 `37877306020` and API37 `37877310874` runs exposed an Android test setup race: `App.onCreate` asynchronously enqueues migration and two reconciliation passes, which could re-record the test fixture before its Worker ran. Repaired test `6726d729` waits on the app's single-thread executor startup barrier and explicitly checks PackageManager enumeration/cache preparation. These earlier diagnostic failures did not establish a production regression.
- **Passing functional-head evidence `6726d729`:** standard unit/lint/AndroidTest compilation, Room and debug/release-Java gates `37877781681` SUCCESS; Java/Kotlin CodeQL `37877776604` SUCCESS; Android 17/API37 targeted `37877791766` SUCCESS (real per-package retry, external unprivileged security probe, launcher smoke); Android 7/API24 `37877787174` SUCCESS, including the new handler-failure retry, prior backup/WorkManager recovery, same-debug-key in-place update and wrong-signer/downgrade refusal with retained data.
- **Boundary:** injection occurs at the package-handler dispatch, before a coordinator transaction writes policy and queue state. This does not prove recovery from an exception/failed `SharedPreferences.commit()` **midway through** a multi-store policy/setup operation, live receiver executor failure behavior, physical/OEM/LMK conditions, exact 15-minute delivery, real root/Shizuku or signed release/rollback.

### Phase 10: fail-closed New-App pending queue and partial policy write recovery (2026-10-09)

- PR #102 (`phase10/newapp-queue-partial-commit-recovery`) resolves the concrete Ask / missing-default-preset orphan path: `NewAppSetupCoordinator` now commits the **pending marker first**, then ensures the explicit unmanaged Room policy, then notifies. A failed queue commit can no longer leave behind a newly created `SOURCE_EXPLICIT` row that causes subsequent `explicitlyConfigured && !queued` checks to suppress setup. If the Room upsert fails after the pending commit, the durable pending marker keeps the package eligible for replay/retry.
- `NewAppSetupStore.addPending/removePending` now check `Editor.commit()` and throw on `false`; they **always retry the disk commit, even if the requested membership already appears in memory**. Android may update in-memory SharedPreferences before a `commit(false)` return; skipping an apparently idempotent operation would misreport a non-durable queue mutation as successful.
- New pure-JVM ordering tests inject failures before the Room write, during the Room write and at notification dispatch. Android API24/API37 instrumentation uses **test-only synthetic SharedPreferences proxies** that model memory-changing `commit(false)` and verify failed writes are surfaced, retried and eventually persisted, without modifying real app settings/Room/user queues. This is not physical flash corruption testing.
- An initial API24 run `37879925887` exposed an unrelated pre-existing backup-v7 instrumentation startup race: asynchronous `App.onCreate` pending replay removed a stale queued package between restore snapshots. The backup test now waits for the app's startup executor before collecting preferences, preserving its strict comparison of **all portable** settings, including the actual pending queue. It was revalidated; production backup code is unchanged.
- **Passing functional head `834d6870`:** standard unit/lint/AndroidTest/Room/debug APK and nonpublishing release-Java compilation `37880363864` SUCCESS; Java/Kotlin CodeQL `37880360306` SUCCESS; Android 7/API24 full lane `37880367869` SUCCESS (new queue fault tests, backup v7/ContentResolver boundary and rollback, Worker process-death recovery, same-debug-signer update, wrong-signer and downgrade refusal); targeted Android 17/API37 lane `37880371770` SUCCESS (new queue fault tests, external separate-UID security probe, launcher).
- **Limitations:** this guarantees the planned ordering and detects reported `SharedPreferences.commit(false)`; it is not an atomic Room+SharedPreferences transaction across power loss or force-stop, cannot detect a storage system that falsely reports durability, and does not simulate failed real flash writes or partially applied preset/notification side effects on physical/OEM devices. No Room schema, dependencies, privileges, package version or production signing changed.


### Phase 10: preset cleanup retry safety (2026-10-09)
- PR #103 avoids reapplying an already committed explicit preset when pending-queue removal failed; retry now re-commits queue cleanup before cancelling its notification. Existing autogenerated unmanaged placeholders remain eligible for Ask/missing-preset setup.
- JVM regression tests cover committed presets, later user edits, placeholders, migrated policies and exception ordering. A synthetic API24/API37 SharedPreferences proxy models `commit(false)` changing in-memory state without updating durable data. No real Room or user settings are modified by these fault tests.
- Verified functional head `76576f48`: standard unit/lint/AndroidTest/Room/debug APK/release-Java `37886186429` SUCCESS; CodeQL `37886179075` SUCCESS; full API24 `37886190391` SUCCESS (including existing backup/WorkManager/upgrade/refusal safeguards); targeted API37 `37886195214` SUCCESS (including external-UID security and launcher).
- Limits: an exact manual policy identical to the autogenerated placeholder remains indistinguishable without provenance; no atomic Room+Preferences power-loss test or physical/OEM/root, production signing/rollback evidence. No schema, dependency, privilege or version changes.

### Phase 10: SAF one-shot URI boundaries and persisted-grant negative tests (2026-10-09)

- PR #104 (`phase10/saf-uri-permission-boundary`) keeps the backup UI on its existing one-shot `ActivityResultContracts.CreateDocument` / `OpenDocument` flow. `BackupFileStore` now accepts only `content://` with a nonempty authority and rejects `file://`, HTTP and malformed URIs before stream open; no permission or persisted URI storage is added to the app.
- New opt-in, read-only `Api24Api37SafUriBoundaryInstrumentationTest` checks Android rejection of reading/writing a private foreign test provider without a grant; rejection of `takePersistableUriPermission` and `releasePersistableUriPermission` on an ungranted URI; unchanged `getPersistedUriPermissions()` after a normal one-shot synthetic ContentResolver read; and unchanged all portable preferences and Room policy/preset counts. A JVM source guard enforces the one-shot picker contracts without implicit persistent-grant acquisition.
- **Passing functional head `7cf96156`:** standard Unit/Lint/AndroidTest/Room/debug and nonpublishing release-Java compilation `37941165730` SUCCESS; Java/Kotlin CodeQL `37941149917` SUCCESS; full Android 7/API24 `37941174674` SUCCESS including four new SAF boundary tests, earlier backup v7/Worker retry/upgrade-signer/downgrade guards; targeted Android 17/API37 `37941182174` SUCCESS including new SAF tests, separate-UID external component probe and launcher.
- **Scope boundary:** tests prove refusal of **ungranted** private URIs and absence of newly retained grant state in these one-shot uses. They do **not** exercise a real user-selected DocumentsUI grant, successful persistable-grant acquisition, provider-specific revocation/expiry or physically different OEM/storage providers. `docs/SAF_DOCUMENTSUI_ACCEPTANCE.md` lists that work as NOT RUN. URI streaming and I/O failures from the existing backup-v7 tests remain separately covered. No schema, privileges, dependencies, app version or signing changes.

### Phase 10: non-destructive post-Phase9-DB-commit backup-v7 rollback (2026-10-09)

- PR #105 (`phase10/backup-v7-post-room-commit-rollback`) adds a fourth targeted test to `Api24Api37BackupV7BoundaryInstrumentationTest` without changing the production restore implementation. The previously existing `Phase9BackupRestoreTest` already tested `AFTER_PHASE9_DB_COMMIT` against a **cleared and reseeded** fixture; this bounded follow-up tests the same late fault while preserving a snapshot of the **pre-existing** emulator stores.
- The test serializes the current valid v7 backup, adds one synthetic explicit `UNMANAGED` policy **only to incoming JSON**, flips one portable backup preference, and injects `IllegalStateException` after the actual Phase9 Room commit. It explicitly checks that the synthetic policy was visible at the injected commit point, that the import returned false, and that the synthetic row disappeared after rollback.
- Regression assertions compare **every persisted field of every original Room app-policy and built-in/user preset row**, all main portable SharedPreferences keys/values and key absences, and both Automation Schedule preference stores. The separately maintained, nonportable installed-package inventory remains excluded from main-preference equality to avoid an unrelated concurrent WorkManager pass. No test setup wipes Room data, resets preferences or installs packages. The existing API24 expected boundary-test count is now four.
- **Passing functional source head `ee00f2ea`:** standard JVM/Lint/AndroidTest/Room/debug APK and nonpublishing release-Java compilation `37959461441` SUCCESS; CodeQL `37959441221` SUCCESS; full API24 emulator `37959472090` SUCCESS including all four backup-v7 boundary tests, real WorkManager/process-recovery and positive same-ephemeral-signer upgrade plus rejected wrong signer/downgrade; targeted API37 emulator `37959480269` SUCCESS including the new late-commit rollback test, separate-UID external component security probe and launcher.
- **Scope:** deterministic exception **after successful Room commit** and verified compensating rollback in emulator software; not atomic multi-store writes across genuine power loss, failed flash persistence, unrelated concurrent user changes, OEM physical provider diversity, root/Shizuku or production signing. The real DocumentsUI user-grant/revocation plan remains **NOT RUN** at `docs/SAF_DOCUMENTSUI_ACCEPTANCE.md`. No application Java/manifest, database schema, permissions, dependency locks or signing identity changed.

### Phase 10: Android 17 denied-notification fallback (2026-10-09)

- PR #106 (`phase10/api37-notification-denied-setup-fallback`) adds opt-in API37 instrumentation verifying the **actual Android runtime `POST_NOTIFICATIONS` permission denied** path without changing production notifier, new-app coordinator, Settings UI, permissions or schema. The disposable-emulator workflow records the initial runtime permission grant, revokes it before the test and restores the original grant state with a shell EXIT trap.
- The test queues only the installed instrumentation APK package, checks that no setup notification is posted while permission is denied, confirms the pending entry remains durable and that Settings displays its count and keeps **Review next** enabled, and verifies that Review next opens the correct Policy Editor without silently saving or changing the existing policy. The original queue membership, setup mode and test-package policy are restored in `finally`. No privileged app action or user data reset is performed.
- An initial diagnostic API37 run `37961686159` failed at an **overstrict test assertion** comparing against the policy before Settings startup. The real background setup replay legitimately created a safe explicit `UNMANAGED` placeholder while Settings loaded. Repaired test `fcdd5349` waits for the shared executor and compares the full policy immediately around Review navigation; its cleanup also restores the prior test-package policy.
- **Passing repaired test head `fcdd5349`:** standard unit/lint/AndroidTest/Room/debug APK/nonpublishing release-Java `37962369169` SUCCESS; Java/Kotlin CodeQL `37962353988` SUCCESS; targeted Android 17/API37 `37962375502` SUCCESS, including the denied-permission fallback test, separate-UID external component probe and launcher smoke. Pre-Oreo notification tap and Review-next on API24 were previously exercised in PRs #91/#92; they were not rerun in this test-only change.
- **Limits:** this checks explicit denied runtime permission on a disposable emulator, not the user-visible permission prompt, later grant/denial transitions, actual delayed installed-package replay under power/Doze/LMK, physical/OEM notification behavior, Shizuku/root or production-signing/release acceptance.

### Phase 10: visible-only main-list bulk selection (2026-10-09)

- PR #107 (`phase10/main-list-visible-bulk-selection`) fixes a verified selection defect in the main running-app list. `MainActivity.selectAll()` previously iterated `fullAppsList` and silently marked packages hidden by the active **search or Policy-Status filter**. Such hidden selections could later participate in a destructive bulk-kill command.
- Select all now delegates only the already-filtered `appsDataList` to `AppListBulkSelectionPolicy.selectVisible`. It still never selects protected or whitelisted rows; previously **explicitly** selected packages are not silently cleared by a filter change, and Deselect all still clears all selections. The toolbar uses its existing actual-selection-derived state rather than unconditionally showing Deselect all when no visible app was selectable.
- Four pure JVM tests verify hidden rows stay unselected, protected/whitelisted exclusion, preservation of an earlier explicit selection and zero-visible-row behavior. An authoritative source-policy test guards the MainActivity integration.
- **Passing functional head `be9b94da`:** standard unit/lint/AndroidTest compilation/Room/debug APK and nonpublishing release-Java `37966939829` SUCCESS; CodeQL `37966924070` SUCCESS; full Android 7/API24 `37966948354` SUCCESS (including backup-v7, real Worker retries and same-debug-key upgrade with wrong-signer/downgrade denial); targeted Android 17/API37 `37967280592` SUCCESS (installed manifest boundary, separate-UID external security probe and launcher).
- An initial Android17 dispatch aimed at the API24-only notification-tap class was cancelled in favor of the suitable manifest-boundary class; the replacement passed. **Evidence boundary:** Select-all behavior is verified by JVM policy and source wiring, not by actual end-to-end Android UI tapping or screen-reader use. Emulator runs verify the product still builds, installs and passes established runtime safety gates. No policy ownership, Room schema, permissions, deps, default state, root/Shizuku operations or signing changed.

### Phase 10: Policy Editor field labels and honest main-list empty states (2026-10-10)

- PR #108 (`phase10/policy-editor-label-associations`) merged to main at `2100cf801c31afd874777a87d79eaadcfb6b5dde`. Four Policy Editor Spinner captions now use Android `labelFor` on their respective controls. PR-head CodeQL and branch-exact standard `37998700671` SUCCESS; no TalkBack/device reading-order acceptance was performed.
- PR #109 (`phase10/main-list-empty-state`), functional head `ccf96859107b0e855232ce553e99349e4bad50d3`, adds an accessible non-touch-blocking text overlay while keeping the RecyclerView inside the existing SwipeRefreshLayout. A pure `AppListEmptyStatePolicy` distinguishes unfinished scans, completed scans with no running apps, and zero matches under search/policy filters. Shell permission/waiting/lost states clear prior completion to avoid a false empty assertion. Strings are provided in base EN and existing ES/RU/UK/zh-CN locales. JVM state tests and an authoritative source/layout wiring test guard the behavior. No app strategy, persisted policy/Room schema, privileges, dependencies or signing changes.
- **Passing final functional-head evidence:** standard unit/Lint/AndroidTest/release-Java/Room/debug APK run `37999654864` SUCCESS; Java/Kotlin CodeQL PR head SUCCESS; Android 17/API37 targeted `37999660002` SUCCESS, including installed-component instrumentation, separate-UID security probe and launcher smoke. The earlier API37 `37999192705` FAILED during Gradle test-classpath resolution of Maven `junit:4.13.2`/`hamcrest-core:1.3` before APK install; the clean full retry is the valid replacement evidence.
- **Scope boundary:** JVM decision logic and source wiring plus emulator build/launch are verified; no actual on-device typed search/filter gesture, view-overlay/screen-reader announcement, physical/OEM hardware, real Shizuku/root, production signing, release update or rollback acceptance is claimed.

### Phase 11: Policy Editor preset customization integrity (2026-10-10)

- PR #110 (`phase10/policy-editor-customized-state`), functional head `306907ab415f7d306e81cc6ce964fc39b3b35d38`, removes the sticky UI `customized` event flag: focusing a numeric field, selecting an unchanged value, and editing then reverting no longer persist a false preset override. Saving computes `AppPolicy.customized` from actual current editable-field equality against the selected `PolicyPreset` (or retains Custom with no preset ID).
- Deferred preset-Spinner initialization callbacks cannot reapply an already selected preset over an existing modified explicit policy. A visually accessible, translated "modified from selected preset" line reflects the live edited values for EN/ES/RU/UK/zh-CN and hides itself again if the values match. A new test-only Android class exercises focus/edit/revert, on-screen status, retained initial overrides and durable save with cleanup of only synthetic policy rows; JVM tests lock the comparison and UI source wiring.
- **Passing functional-head evidence:** standard JVM/Lint/AndroidTest/release-Java/Room/debug build `38002492744` SUCCESS; CodeQL `38002497898` SUCCESS; targeted Android17/API37 `38002496323` SUCCESS, including the Policy Editor customization instrumentation, separate-UID component abuse probe and target-37 launcher smoke. No production policy engine, new privilege, default setup state, Room schema, dependency or release-signing changes.
- **Limits:** UI tests ran on a disposable API37 emulator, not Android7, physical/OEM hardware, TalkBack/manual accessibility interaction, real Shizuku/root, stable signing, actual release update or rollback. No production-user package data was cleared in the test.

### Phase 11: New-app multi-queue review and explicit pending replay (2026-10-10)

- PR #111 (`phase11/new-app-review-queue-ux`) makes the Settings fallback usable for multiple pending installs. It shows each of the first eight durable, case-insensitively sorted package entries as an accessible, individually clickable Review button with an app-label/package disambiguator, a clear next-app name, and an overflow count. `Review next` still opens the first current pending package and refuses already-cleared selections.
- Changes to New App Setup mode, or to the default preset while the automatic mode is active, now require explicit confirmation when a queue is nonempty. Cancelling restores the Spinner and neither persists a new option nor replays the pending entries; confirming uses the existing `NewAppSetupCoordinator.replayPending` implementation. Async preset loading disables its controls until initial binding is complete. No default strategy, pending representation, ownership, Room schema, permission, dependency or privileged execution behavior changed.
- A pure `NewAppSetupReviewPolicy` locks deterministic case-insensitive package ordering with a case-sensitive tie break, bounded presentation and confirmation predicates. A dedicated API37 instrumentation suite uses the **installed** instrumentation APK and separate-UID security probe packages, creates safe `UNMANAGED` pending placeholders, checks independent save/return/review-next routing and cancellation, then restores both policy rows, their queue memberships and the setup-mode preference. Other pending entries are preserved.
- **Passing final functional-head evidence:** standard JVM, lint, AndroidTest compile, Room/debug APK and non-publishing release Java `38005451071` SUCCESS; CodeQL PR head SUCCESS; targeted Android 17/API37 `38005455084` SUCCESS, including the two new UI instrumentation tests, external-UID component-abuse probe and launcher smoke.
- Two diagnostic API37 runs exposed *test harness* assumptions, not proven application defects: run `38004638153` correctly pruned synthetic uninstalled packages; run `38005075775` passed the mode-cancel test but reused an ActivityMonitor removed by the first wait. Both were repaired before the final successful run.
- **Evidence boundary:** tests use disposable API37 software and installed non-system fixtures, not actual simultaneous installs, real denial/grant transitions, power loss, UI screen-reader/manual touch testing or physical/OEM, root, stable signing and production rollback. Denied-notification fallback remains independently proven by PR #106.

### Phase 11: deterministic wall-clock/DST schedule boundaries (2026-10-10)

- PR #112 (`phase11/schedule-wallclock-dst`) reuses the existing shared `ScheduleTime` implementation for Automation Schedules (legacy `PresetManager`) and Restrictions Scheduler. Daily times are resolved using the current local calendar date and time zone, **not** a fixed 24-hour duration. Missing local spring-forward minutes use `Calendar` forward normalization (for example, 02:30 → 03:30), and a repeated fall-back minute chooses the **earlier** instant once per local date. Explicit source tests use Europe/Berlin 2026 spring/fall transitions, a 30-minute Australia/Lord_Howe rollback and differing zone interpretations of one UTC instant.
- The existing exported `BootReceiver` additionally accepts only the protected `TIME_SET`/`TIMEZONE_CHANGED` system events. Their asynchronous `goAsync` branch rebuilds restriction and Automation Schedule RTC alarms and reconciles active Automation Schedules **without** executing the `BOOT_COMPLETED`-only AutoKill/Smart worker startup branch. The broadcast actions are documented in `EXPORTED_COMPONENTS.md` and are exempt from Android's manifest implicit-broadcast limits. No new manifest component/permission, Room schema, persisted schedule names, backup version, dependencies or privileged-shell command was added.
- A read-only installed-manifest Android test verifies that `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED` resolve to the existing receiver while `TIME_TICK` does not. The existing boot-idempotency instrumentation remains intact.
- **Passing evidence:** branch functional-head standard unit/lint/AndroidTest/Room/debug APK and nonpublishing release-Java `38006946362`; branch CodeQL passed; targeted Android 17/API37 boot idempotency + separate-UID security probe + launcher `38006775588`; API37 installed-manifest registration + security probe + launcher `38007071052`, all SUCCESS.
- **Still incomplete:** no real device clock/time-zone mutation and protected-broadcast delivery observation was performed; OEM, Doze/permission, mid-window Restrictions Scheduler active-state reconciliation after an abrupt clock jump, real root/Shizuku, stable signing and rollback remain external acceptance. Keep this roadmap item `[~]`, not `[x]`.

### Phase 11: fail-safe Restrictions clock-change reconciliation (2026-10-10)

- PR #113 (`phase11/clock-restrictions-reconcile`) follows PR #112's RTC re-arming with a separate, explicitly bounded reconciliation of the durable `temp_protected_packages` marker against the effective local-time schedule windows.
- The system-only `TIME_SET`/`TIMEZONE_CHANGED` receiver uses pure, read-only drift detection first. **Only an already-running** `ShappkyService` can accept the internal repair action; no service wake-up, component-launch action, force-stop or privileged mutation runs directly in the exported receiver. If unavailable, previous markers and next alarms remain untouched. At the next shell-ready normal service initialization, recovery is retried **after** saved background restrictions have finished restoration.
- Repair runs on the existing executor, isolates package failures and never invokes configured external launch actions or app force-stops merely because the clock changed. Successful protection changes are marked only after corresponding BG/standby/timer-thaw actions; failed or partial outcomes retain the old marker. A failed `SharedPreferences.commit()` triggers best-effort rollback of its in-memory marker to permit a future retry.
- Static alarm restoration now cancels obsolete `SCHEDULER_TICK` alarms for removed, invalid, oversized, fully disabled or unschedulable schedules instead of leaving an outdated wake-up to start the service later.
- JVM cases verify forward/backward drift, overnight and disabled windows, duplicate packages, zero-duration windows and invalid current time. API37 non-privileged instrumentation saves/restores only the schedule and marker preference keys, checks read-only drift evaluation and malformed JSON fail-closed, and runs with the established separate-UID security probe and launcher.
- **Passing functional-head gates:** standard `38008969961` (JVM, lint, AndroidTest, Room, APK and nonpublishing release Java); targeted Android17/API37 `38008973503` (instrumentation, separate-UID security probe, launcher); CodeQL passed. Earlier diagnostic branch source erroneously truncated the service receiver tail; exact main-source reconstruction repaired it before final validation.
- **Limits:** the emulator probe does not change Android system time or zone, prove actual protected-broadcast delivery, execute privileged restoration through real Shizuku/root, or simulate flash failure. A stale marker for a fully deleted schedule cannot safely restore its missing historical restriction settings; it remains a distinct recovery issue. Physical/OEM, Doze, production signing and rollback remain external acceptance. This roadmap item stays `[~]`.

### Phase 11: real Android-17 timezone broadcast delivery (2026-10-10)

- PR #114 / squash merge `c3c57166ce08aa940a83390ecd2fa2d12735c01d` added a disposable API37 system-timezone test with **no production Java changes**. Its targeted workflow actually invokes the platform `cmd alarm set-timezone` command, not a synthetic app broadcast, after an instrumentation observer signals readiness.
- The observer proves delivery of Android's protected `TIMEZONE_CHANGED` intent and matching `time-zone` extra, and verifies the **installed** manifest advertises the production `BootReceiver` for the same action. The workflow restores the original timezone and automatic timezone setting with an EXIT trap and confirms the property matches; it does not use privileged ReAppzuku commands, change app policies or touch real user data.
- **Passing evidence:** standard `38011671445`, targeted API37 `38011948878` (actual broadcast observer, separate-UID external security probe, launcher), PR-head CodeQL all SUCCESS. The initial dispatch on `main` incorrectly used the old workflow definition and was canceled; only the branch-ref targeted run is acceptance evidence.
- **Limit:** This establishes real OS broadcast delivery **to a dynamically registered observer**, alongside static `BootReceiver` manifest presence. It does **not** prove the specific manifest receiver executed its internal rescheduling, a real `TIME_SET` change, exact alarm trigger reconstruction, Shizuku/root mutation, or physical/OEM behavior.

### Phase 11: normal Restrictions Scheduler tick marker safety (2026-10-10)

- PR #115 (`phase11/tick-marker-commit-after-success`) fixes a verified pre-action protection-marker write in the normal RTC-boundary `RestrictionsScheduler.tick()`. Previously all desired packages were persisted **before** background lift/restore, timer thaw, bucket mutation or app stop succeeded, allowing false protected/unprotected markers and missed transitions.
- Tick now isolates each package failure, only adds/removes durable markers for actually completed operations, honors the typed shell result of stop/force-stop, schedules optional component launch only after prerequisite protection steps, protects recording from independent logging errors, and always re-arms next boundaries in `finally`. An unsuccessful marker `SharedPreferences.commit()` triggers best-effort restoration of the previous visible marker. If a schedule was deleted, its lost historical rollback tuple is not guessed and no force-stop is fabricated.
- Pure `RestrictionsTickMarkerPolicy` tests verify success/failure, mixed transitions, idempotence and defensive copies; source-authoritative tests guard actual production ordering and shell-result checks. No new privileged command, default automation setting, exported component, schema, backup representation or dependency.
- **Passing functional evidence:** standard CI `38012065196` (unit, lint, AndroidTest compile, Room, debug APK, nonpublishing release Java), targeted API37 `38012202143` (read-only scheduler drift, separate-UID probe, launcher), and rebased PR CodeQL SUCCESS. Targeted emulator does **not** execute destructive/privileged normal ticks.
- **Limits:** Actual Root/Shizuku failure injection under real scheduler boundaries, transient failure retry timing, removed-schedule rollback history, physical/OEM, real `TIME_SET`, signing and rollback remain open. Clock-jump reconciliation remains intentionally separate from normal boundary force-stop/component behavior.

### Phase 11: actual installed receiver RTC re-arming on Android 17 (2026-10-10)

- PR #116 (`phase11/api37-real-manifest-clock-rearm`) adds an opt-in disposable-emulator-only AndroidTest plus a host-side CI wrapper; **no production Java, manifest, dependency, signing, backup or schema changes**. A harmless future Restrictions Scheduler fixture has `protectFlags=0`, `onActivateAction=NOTHING`, no bucket action and targets the instrumentation APK. The test explicitly cancels the old scheduler alarm and verifies the fixture **has no pending `SCHEDULER_TICK` alarm** before registering its observer.
- The CI host invokes Android's real `cmd alarm set-timezone Pacific/Honolulu` after the observer reports readiness. The test receives the genuine `TIMEZONE_CHANGED` event, then observes a newly present scheduler `PendingIntent` through the platform `dumpsys alarm` inventory. The test never calls `BootReceiver`, `scheduleNextStatic` or a privileged service during verification, so the newly scheduled alarm demonstrates the installed production clock receiver's RTC re-arm path. Original preferences and alarms are restored in `finally`; the host restores the original timezone/auto-timezone configuration by EXIT trap and verifies the persisted timezone property.
- **Passing functional evidence:** standard `38013811153` (unit, lint, AndroidTest compile, schema and APK), targeted branch-exact API37 `38013815180` (real receiver RTC alarm creation, separate-UID security probe and launcher) and PR CodeQL SUCCESS.
- **Boundaries:** This proves existence of the real pending scheduler alarm after a platform timezone event, not its exact trigger timestamp or actual future firing, `TIME_SET` delivery, privileged root/Shizuku restrictions, OEM/physical variants, or production signing/rollback. The schedule's old restriction tuple is still unavailable after deletion. Roadmap status remains `[~]`.

## Next work unit

Investigate real platform `TIME_SET` delivery on a disposable emulator with rigorous host-time restoration; do not use production hardware or claim evidence from synthetic broadcasts. Separately design a durable, bounded removed-schedule recovery record and non-destructive transient-failure retry for the normal scheduler tick before implementing any privileged retry path. Real DocumentsUI grant/revocation, TalkBack/device checks, OEM/physical, root and production signing/rollback remain external acceptance.

### Phase 11: fail-closed scheduler rollback transaction foundation (2026-10-10)

- PR #117 (`phase11/scheduler-recovery-design-clean`) adds `SchedulerRecoveryTransaction`, an Android-independent bounded and immutable package-scoped record with captured original restrictions, owner IDs and explicit PREPARED/APPLYING/ACTIVE/RESTORE_REQUIRED/RESTORING/REVIEW_REQUIRED/RESOLVED states. The `DurableStore` and `Operation` interfaces require a confirmed durable APPLYING/RESTORING record **before** an injected external action. In-memory-changing `commit(false)`, thrown RuntimeException, partial success and final-commit failure do not falsely claim completion; unknown states require inspection, not blind retries. Overlapping schedules retain the record until the last owner is removed.
- A pure-Java/JUnit fault suite has 19 cases: initial/post mutation persistence failures, partial/throwing external operations, conflicts, overlap/deletion, immutable snapshots, bounds and retry cap. A separate `javac` test harness locally passed 19/19; source-authoritative Gradle unit, lint, AndroidTest compile, nonpublishing release-Java, schema and debug APK gate `38018609834` passed. Tested-head Java/Kotlin CodeQL `38018587965` and PR check-runs passed; no review threads.
- **This is not an integrated recovery implementation.** No call sites in `RestrictionsScheduler`, Android durable store/codec, real original-state capture, retry worker/alarm, Root/Shizuku command or physical device tests were added. The existing package-name-only legacy marker still cannot safely reconstruct deleted-schedule original restrictions. The partial Phase 11 roadmap item remains open.
- Additional source audit found that existing `BackgroundAppManager.restoreBatteryWhitelist()` removes its ownership marker without checking whether the privileged ADD actually succeeded; `liftRestrictionsForScheduler()` and `restoreRestrictionsForScheduler()` do not consistently incorporate bucket/whitelist outcomes into returned success status. This is a **separate prioritized follow-up**, not fixed or covered by PR #117. Validate any privileged-result changes with fault injection and API24/API37 gates before merge.
- First experimental branch `phase11/scheduler-failure-recovery-design` accidentally wrote an empty checkpoint on **that branch only**; it must never be merged. Clean PR #117 was branched directly from unchanged `main` and this checkpoint was reconstructed from the full canonical main file rather than the empty experiment.


### Phase 11: Scheduler privileged-result and whitelist ownership repair (2026-10-10)

- PR #118 merged as `b79294989423ec33011fbcdc183f77c029cac33d` from `phase11/scheduler-whitelist-result-safety`, based on PR #117's clean `main`. The existing `BackgroundAppManager.restoreBatteryWhitelist()` now retains the ownership marker unless privileged DeviceIdle ADD returns success. Empty/unavailable DeviceIdle inventory output is treated as unknown, not confirmed absence. Both scheduler lift and restore propagate failed standby bucket and relevant whitelist steps through `SchedulerRestrictionOutcomePolicy`, rather than returning `ok` from AppOps success alone.
- Six pure JVM operation-result cases and three production-source integration guard cases passed. Standalone `javac` fixture harness passed 9/9; branch-exact standard Gradle/JVM/lint/AndroidTest/nonpublishing release-Java/schema/APK `38019141214`, Java/Kotlin CodeQL `38019122766`, and targeted Android 17/API37 `38019144059` all **SUCCESS**. The API37 workflow also passed its independent external-UID security probe and launcher smoke. PR review threads were empty.
- No new privileged command, default policy, manifest component, Android permission, Room schema, backup format or dependency was introduced. The API37 instrumentation remains non-privileged; no failing real root/Shizuku commands were injected. Existing `saveBatteryWhitelistRemoved()` uses asynchronous `SharedPreferences.apply()`, so this patch corrects the failed-command marker loss but **does not** prove power-loss durability or atomic multi-step restoration. No API24, physical/OEM or production-signed-release acceptance was performed for this PR.

## Next work unit

Before wiring the recovery journal into live scheduling, harden cross-instance/process-lifetime poisoning after a `SharedPreferences.commit(false)` (a fresh adapter in the same process must not trust potentially memory-only changes); also validate original-restriction tuple immutability and permitted phase transitions across journal revisions. Then integrate actual safe original-state capture and pre-mutation durable commits, followed by idempotent deleted-schedule restoration and bounded retries, separately tested against real Shizuku/root. `TIME_SET`, RTC firing, physical/OEM, production signing and rollback remain independent acceptance tasks.

### Phase 11: validated scheduler-recovery journal and actual process-death proof (2026-10-10)

- PR #120 / squash merge `2ea5fd370430200f359b2c235c716cd4b0c34a74` adds a strict pure-Java `SchedulerRecoveryJournalCodec` and `SchedulerRecoveryJournalStore` plus a dedicated, private, synchronous `SchedulerRecoverySharedPreferencesStore`. The format is deterministic; max 128 package records and 32 KiB decoded bytes; checked version/magic, owner count/IDs, package/phase/sequence and SHA-256 integrity. An invalid, unexpected or corrupt value blocks writing instead of being treated as an empty journal. The digest detects corruption but **is not an authenticity signature**. The journal is nonportable runtime state, not part of the user backup.
- Synchronous full-snapshot `SharedPreferences.commit()` is required. The store rejects stale revisions, keeps unresolved rows, deletes only `RESOLVED` records and poisons its instance after an uncertain write (including `commit(false)` changing memory). JVM tests inject pre/post-operation write failures, corruption and re-open from a separately modeled durable image. A production adapter must remain single-writer; **a new adapter in the same living process does not yet share the old instance's poison flag**, a fail-closed invariant to harden before active scheduler integration.
- Branch-exact source-authoritative standard validation `38068216883` **SUCCESS**, Java/Kotlin CodeQL `38068207851` **SUCCESS**, Android 17/API37 `38068222437` **SUCCESS**, complete Android 7/API24 regression `38068321207` **SUCCESS**. Targeted API37 actually seeded the test-only journal, verified it was durably visible before a simulated operation, terminated the debug app under its own UID via SIGKILL, observed a different PID after relaunch, then recovered/advanced/cleaned the stored tuple in a separate instrumentation session. API37 also passed separate-UID security and launcher smoke. API24 passed its existing launch/migration/worker/update-signature suite, **not** a dedicated API24 journal process-death test.
- **Still not implemented:** real `RestrictionsScheduler` call sites, capture of original Android restrictions, recovery during deletion, delayed retry scheduling, Root/Shizuku mutation fault tests, actual flash/power-loss guarantees, OEM hardware, complete API24 journal-specific runtime and production release acceptance. No existing user policy, manifest permission, Room schema or backup version changed.

