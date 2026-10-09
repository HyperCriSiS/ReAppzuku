# ReAppzuku assurance roadmap

This roadmap converts the assurance matrix into a bounded engineering sequence. Work is ordered by blast radius: prevent silent privileged misbehavior first, then make persistence/recovery trustworthy, then harden interfaces and finally widen compatibility/UX coverage.

Status convention:
- `[x]` implemented in source;
- `[~]` implemented but repeatable live evidence is incomplete;
- `[ ]` not implemented / open.

## Phase 1 — Lifecycle, provenance and scheduling baseline

- [x] Model desired AutoKill service state separately from observed process state.
- [x] Prevent intentional service disable from being undone by restart paths.
- [x] Update checker points at the fork-owned repository, enumerates releases, skips drafts/prereleases/non-numeric rolling tags such as `ondemand-test`, and treats a repository with no stable numeric release as “no update” rather than a retry-worthy transport failure.
- [x] Strict numeric release-version comparison.
- [x] Restore preset scheduling after reboot.
- [x] Centralize exact-alarm capability and fall back when exact alarms are unavailable.
- [x] Keep only `SCHEDULE_EXACT_ALARM`; remove unnecessary exact-alarm declaration.
- [x] Remove locked-boot path because configuration lives in credential-encrypted storage.
- [x] Remove destructive Room fallback.
- [x] Backup format v6 includes fork behavior settings plus exact per-package manual restriction details (AppOps mask, standby bucket and whitelist-removal choice) and rejects future versions.
- [x] Accessibility configuration no longer requests unnecessary view-tree reporting.
- [x] First JVM regression tests and source-authoritative CI gates.

### Live evidence still required

- [x] Fresh Shizuku permission flow: API-36 run `34000092714` cold-started a cleared, explicitly ungranted ReAppzuku against the official Shizuku server/manager, observed the real authorization dialog, proved no app scan started before grant, then observed permission result -> `SHIZUKU_READY` -> app scan in order.
- [x] AutoKill disable survives restart recovery and external process death: API-36 instrumentation proves a fresh restart receiver obeys persisted disabled state, real reboot recovery passes, and focused run `34004843634` proves persisted disabled desired state across a real external `am force-stop` with PID change (`2963` -> `3250`).
- [x] Reboot reconciliation is idempotent on the stable API-36 emulator: repeated `BootReceiver` execution is Unique-Work safe, and real OS reboot run `33985971887` passed reboot/unlock/post-boot recovery with Restrictions Scheduler plus preset activate/deactivate alarms reconstructed. Physical/OEM devices remain release-diversity evidence, not an implementation blocker.
- [x] Exact-alarm denied path falls back correctly on Android 16/API 36.
- [~] Backup/restore round-trip now passes through a real Android MediaStore `content://` URI on API 36 in run `34000092714`, including export/import/restore on the production backup path; physical/OEM document-provider UI remains release-diversity evidence.
- [x] Fork update provenance on a real installed test build: API-36 run `34037508198` built and installed the current test APK, pulled the installed `base.apk` back from `/data/app`, proved exact SHA-256 identity (`b57c694dc75705f23f5de0b7ef7988811fb350276aa11063b4077af224089dd8`), then scanned every `classes*.dex` and verified the fork-owned stable-release API/page endpoints are embedded while both the upstream API endpoint and obsolete fork `/releases/latest` endpoint are absent.
- [x] Accessibility runtime on Android 17/API 37: external run `34302578767` installed the unchanged `ondemand-shizuku` product APK, modeled the explicit ECM-approved state (`BIND_ACCESSIBILITY_SERVICE=allow`, `ACCESS_RESTRICTED_SETTINGS=allow`), proved the ReAppzuku process remained alive while the production Accessibility service was enabled/binding with no crashed service, launched real System Settings and observed `smart_last_foreground_com.android.settings` from the production event path. `autoKillEnabled=false` and `app_launch_trigger_enabled=false` remained enforced throughout. Physical/OEM Accessibility diversity remains release evidence.

## Phase 2 — Explicit shell readiness state machine

- [x] Explicit backend states distinguish unavailable, permission-required/pending/granted, binding, ready and lost.
- [x] ShellManager owns app-lifetime binder receive/death state and waiter release.
- [x] Operational app scans require a genuinely ready backend, not merely permission.
- [x] MainActivity waits/retries only for transitional states instead of mapping waiting to generic shell failure.
- [x] JVM transition tests cover root precedence, permission/readiness separation and lost state.
- [x] Instrument binder-late, deny -> grant, Activity recreation and Shizuku restart/service-death recovery: deterministic API-36 bridge coverage proves the state permutations; real official-Shizuku first-run grant is proven by `34000092714`, same-process daemon death/rebind plus privileged command recovery by `33997372602`, and exact Activity recreation behind the still-open real Shizuku permission dialog followed by grant/READY/scan ordering by `34036827312`. Android 17/API 37 run `34316502181` independently repeats the real manager-dialog authorization and reaches `SHIZUKU_READY` before privileged mutations execute.
- [x] Gate queued `ShappkyService` actions on a genuinely ready shell backend. Sleep recovery testing exposed a race where permission was granted but the UserService was not ready; commit `69750c7de8080a689f8a5703f814f6efbc2a6c54` now waits for `ROOT_READY`/`SHIZUKU_READY` with one bounded Shizuku retry before managers or queued privileged actions initialize. One-time validation `34065892554` passed unit, lint, AndroidTest compile and debug build.
- [x] Add event-driven UI recovery when Shizuku appears after MainActivity is already open. Real first-run testing exposed a lost late-Binder race; commit `ba73b39e4e47e75a19ac2a01a120a69854e19f30`, guarded by run `33999924081`, adds sticky Binder-triggered preparation plus a coalesced retry, and run `34000092714` proves the repaired flow end-to-end.

## Phase 3 — Transactional backup and restore

- [x] Snapshot main + both preset stores before writes.
- [x] Stage and validate all backup content before first durable commit.
- [x] Full preset-section restore clears omitted local preset slots; legacy backups without a preset section preserve them.
- [x] Active-preset runtime backup keys are cleared before imported settings become the new base.
- [x] Main + preset preference writes use synchronous commits during restore.
- [x] Runtime/alarm/service reconciliation happens only after durable writes succeed.
- [x] Failed writes/runtime reconciliation roll preferences back and best-effort restore old runtime state.
- [x] Reject oversized (>2 MiB), malformed and future-version backup payloads.
- [x] Bound every package-oriented backup collection (including manual AppOps masks) to a deliberately generous 10,000 entries to prevent restore amplification without constraining real devices.
- [x] Validate imported preset clock ranges and package collections before scheduling/storage; invalid package identifiers fail closed as parse errors.
- [x] Reuse the bounded `BackupFileStore` reader for standalone preset imports so preset JSON cannot bypass the backup payload-size boundary.
- [x] Add focused automated restore tests for corrupt/legacy/future/oversized/rollback paths.
- [x] Backup v6 round-trips the complete manual-restriction tuple per package: AppOps mask, standby bucket and whitelist-removal choice; imported detail maps must exactly match the manual-app set, stale per-package detail keys are replaced, and v5 backups restore the formerly non-portable fields to defaults instead of leaking device-local state.
- [x] Execute transactional rollback and active-preset restore on Android runtime.

### Transactional restore test coverage — 2026-09-04 to 2026-09-08

- Android instrumentation coverage exercises malformed JSON, unversioned legacy payloads, future-version rejection, the 2 MiB input bound, restoration from captured main/preset rollback snapshots, injected failure after the main commit, injected failure after the first preset commit, and imported active-preset reconciliation.
- The rollback paths execute against real Android `SharedPreferences` and `PresetManager` storage without changing production behavior.
- API-36 runtime gate `33974637281` executed the original transactional restore suite successfully. Focused API-36 run `34259632923` installed the current app + instrumentation APKs and passed all 12 `BackupManagerRestoreTest` cases (`BACKUP_V6_RUNTIME_OK`), including v6 manual-detail round-trip/stale replacement, invalid-bucket rejection and v5 compatibility. Run `34000092714` additionally passed a real Android MediaStore `content://` export/import/restore round-trip; physical/OEM document-provider UI remains separate release-diversity validation.

## Phase 4 — Persistence and migration evidence

- [x] Explicitly exclude Android cloud backup and device-transfer restore; ReAppzuku's versioned backup remains the configuration contract.
- [x] Lock `allowBackup=false`, legacy Full Backup exclusion, Android 12+ Cloud Backup exclusion and Device Transfer exclusion with `PlatformBackupPolicyTest` (`34155781163`).
- [x] Room schema export configured and current schema 11 generation verified in CI.
- [x] `MigrationTestHelper` instrumentation test for supported historical schema v2 -> v11 compiles.
- [x] Migration test validates preservation of existing `app_stats` data and final schema on Android.
- [x] Execute supported v2 -> v11 migration on the stable API-36 emulator lane.
- [x] Commit compiler-generated schema 11 and preserve every schema from now on. Historical schemas 1 and 3–10 were never preserved upstream and must not be fabricated.

## Phase 5 — Privileged surface hardening

- [x] Review identified exported shortcut Activity as a confused-deputy boundary.
- [x] Dynamic RAM shortcut uses an install-specific 256-bit authentication token.
- [x] Token comparison uses constant-time equality and has JVM regression coverage.
- [x] Static/legacy shortcut cannot silently invoke privilege; it requires explicit user confirmation.
- [x] Other explicit external launches can at most open a confirmation dialog before foreground-app force-stop.
- [x] Foreground shortcut action waits for a genuinely ready shell backend.
- [x] Review every remaining `exported=true` component and document its principal boundary in `EXPORTED_COMPONENTS.md`.
- [x] Route mutating package/component/PID operations through typed, validated `PrivilegedShell` commands and reject invalid persisted/input values before shell execution.
- [x] Fixture-test dumpsys/parser protection logic across Android/OEM variants.
- [x] Bound Smart Lifecycle text heuristics behind `PackageTextMatcher`, `SmartLifecycleProtectionPolicy` and `SmartLifecycleTextParser`; JVM tests cover exact package boundaries, dump-protection decisions and foreground/process parsing.

### Privileged parser evidence — 2026-09-05

- `ProcessDumpParser` isolates the ActivityManager text formats consumed by `ProcessAnalyzer` from Android-dependent code.
- JVM fixtures cover AOSP PID-prefixed `ProcessRecord`, remote-process/OEM-style records, `curProcState`/`setProcState`, CRLF, exact package boundaries, binder package extraction, and short/full `ServiceRecord` forms including `$` class names.
- `ProtectedAppsTest` locks exact AOSP/Shizuku package protection plus current keyboard/launcher behavior and rejects prefix-neighbor false positives.
- One-time gate run `33940487030` passed unit tests, lint, AndroidTest compilation and debug APK build before committing the production integration as `57d8897c70a5b4c9565379cd72317f1abb7c9e59`.
- Smart Lifecycle no longer performs broad substring matching directly in the manager: commits `697fcdc4`, `69186c41` and `7fa4b6d6` extract exact package matching, dump-protection policy and foreground/process parsers with JVM coverage.
- API-36 runs through official Shizuku repeatedly reach `SHIZUKU_READY` and parse the real ReAppzuku ActivityManager `ProcessRecord`; run `34153570390` logs `API36_PROCESS_RECORD_PARSED`. The earlier Android-16 synthetic background-service attempt was correctly blocked and removed. It is now superseded by Android 17/API-37 run `35646519446`, where a debug-only non-exported target service is bound through the normal platform path, a real ActivityManager `ServiceRecord` is read via `UiAutomation`, and the production parser consumes the observed `c:<recentCallingPackage>` form.
- OEM/physical-device parser diversity remains separate release evidence even after the stable API-36 runtime contract is exercised.


### Privileged shell evidence — 2026-09-05

- `PrivilegedShell` owns validated kill/force-stop, uninstall, AppOps, standby bucket, DeviceIdle whitelist, suspend/unsuspend, enable/disable, PID kill and explicit component-launch commands.
- Runs `33940964413` and `33941247796` passed unit tests, lint, AndroidTest compilation and debug APK build before integrating the major manager paths.
- Final strict run `33946348081` passed the same gates and required a repository-wide mutating-shell audit to return `NONE` outside `PrivilegedShell`, including the Quick Tile and Restrictions Watchdog paths.
- Normal read-only validation run `33946501074` then passed on the cleaned permanent source state.
- These source/JVM/CI invariants are supplemented by real official-Shizuku UserService execution and daemon death/rebind recovery in API-36 run `33997372602`.
- Representative real command families are exercised on both API 36 (`34005682619`) and Android 17/API 37 (`34316502181`) against disposable targets: AppOps RUN_IN_BACKGROUND ignore/default, standby RARE/ACTIVE, DeviceIdle whitelist add/remove, suspend/unsuspend, disable-user/enable, force-stop and explicit component broadcast pass through real Shizuku. The API-37 lane also externally verifies the reversible package/whitelist state is clean after instrumentation rollback. Root-specific execution and deliberately destructive families such as uninstall remain separate evidence.

## Phase 6 — CI and supply chain

- [x] Preserve `concurrency.cancel-in-progress` for the live-test workflow.
- [x] Pin `actions/checkout` and `actions/setup-java` to immutable full commit SHAs.
- [x] Validation workflow is source-authoritative; no patch/generator step remains after migrations complete.
- [x] Retire the obsolete parallel `android.yml` release/CI path; `main` now keeps only the source-authoritative on-demand validation and hardened `signed-release.yml` workflows after the one-shot cleanup.
- [x] Split read-only validation from release publishing with job-level least-privilege tokens.
- [x] Bind stable release tags to source `versionName`, built APK `versionName`, package identity, expected artifact name and signing certificate before publish.
- [x] Keep `main` as the sole permanent product/validation/release branch; use short-lived branches only for isolated work and delete them after integration.
- [x] Bind updater APK/release links to the validated `HyperCriSiS/ReAppzuku` tag/asset contract instead of trusting arbitrary release metadata URLs or the first `.apk` asset.
- [x] Run `lintDebug` without `updateLintBaseline`; baseline changes only through reviewed source commits.
- [x] Pin every external Action in active repository workflows.
- [x] Establish a reviewed warning-only lint baseline: full scan must contain zero errors before a baseline may be accepted.
- [x] Remove the ReAppzuku-owned Gradle-10 Groovy assignment deprecation identified by `--warning-mode all`.
- [x] Enforce Gradle dependency verification with committed SHA-256 metadata and dependency locking.
- [x] Expand the emulator lane to Android 17/API 37 using the current `android-37.0;google_apis_ps16k;x86_64` image; run `34276106536` proves branch-exact target-37 APK build, first-attempt install, 41-test instrumentation and launcher smoke without consuming release/publish paths.

### Assurance evidence — 2026-09-04 to 2026-09-08

- Workflow run `33577363239` passed unit tests, zero-error full lint, reviewed warning baseline,
  `assembleDebugAndroidTest`, Room schema-11 existence check, and debug APK build.
- Release `ondemand-test` targets commit `e202e38c049a0d4a7cfc561f7a9c8348c9abd8ae`.
- Published APK SHA-256:
  `d841e34685d790197266c1e9c90a11619a33a219377892f2c429c00930dbf5d4`.
- Normal validation returns to a read-only Validate -> writable Publish split after the one-time
  migration workflow.
- Runs `33816595259` and `33816830268` independently forced Room compilation with cache disabled and generated schema 11 from the current source model.
- One-time run `33816989187` was guarded to stage exactly one path and committed only `app/schemas/com.gree1d.reappzuku.db.AppDatabase/11.json` as commit `8707be6`.
- Dependency-assurance run `33832156664` generated `app/gradle.lockfile` and SHA-256 `gradle/verification-metadata.xml`, then passed unit, lint, AndroidTest compile and APK build again without metadata-writing flags.
- Artifact `9922094160` is preserved by SHA-256 `43c53cc13431cd2b2513fb0d9108836d548dd4b33b5673e9ddd49e4b1954918c`; its exact generated files are committed and normal builds now enforce them.
- Dependency-verification refresh run `33900939628` regenerated the complete SHA-256 set from fresh resolution and then passed the build again after deleting the dependency cache, closing the warm-cache blind spot.
- Normal read-only validation run `33901414025` subsequently passed on the permanent locked/verified dependency state.

### Stable Android 16 / API 36 runtime evidence — 2026-09-05 to 2026-09-08

- API-36 gate `33974637281` booted to `RUNNING_UNLOCKED` and passed the complete instrumentation suite present at that commit: transactional restore/fault injection, imported active-preset reconciliation, ShellManager binder/permission/death-rebind sequences, the denied exact-alarm best-effort fallback, and Room v2 -> v11 migration with `app_stats` preservation and final-schema validation.
- The first diagnostic API-36 execution `33974371939` reached 17/18 passing tests and identified the only failure as missing Room schema assets, not a migration failure. `app/schemas` is now packaged into `androidTest` assets.
- Normal validation `33974626448` passed unit tests, lint, AndroidTest compilation, Room schema verification and APK build after the schema-assets fix.
- AutoKill stale-restart desired-state coverage passed normal validation in `33974469090`.
- Repeated boot-reconciliation coverage passed normal validation in `33974877204` and then passed the full API-36 Android runtime gate in `33974963048`, proving WorkManager Unique Work idempotency for AutoKill, Smart Lifecycle and boot cleanup.
- Real OS reboot run `33985971887` passed reboot/unlock/post-boot recovery and reconstructed scheduler/preset alarms. Its bundled force-stop tail was a harness failure; the focused API-36 gate `34004843634` subsequently passed the real external force-stop/process-restart desired-state case with a verified PID change.
- Run `33986395874` passed the explicit-intent exported-shortcut abuse step before an unrelated later Shizuku-recovery failure.
- Run `33997372602` then proved the official Shizuku UserService, shell UID execution, daemon death detection, same-process rebind after server restart and post-recovery privileged command execution.
- Run `34000092714` proved the fresh official-Shizuku permission dialog path with no pre-grant app scan and then passed the real MediaStore `content://` backup round-trip.
- Run `34036827312` proved exact Activity destruction/recreation while the real Shizuku permission dialog remained open, followed by grant -> `SHIZUKU_READY` -> app scan ordering.
- Update-provenance run `34037508198` installed the current test APK on API 36, pulled the installed package back byte-identically and verified the embedded fork-owned stable-release resolver across all DEX files. Updater changes are additionally green in normal validation runs `34037213036` and `34037439640`.
- Process-topology run `34057708486` proved the on-demand split on API 36: a provider-only case kept only `com.gree1d.reappzuku:shizuku` alive with the main process and `ShappkyService` absent; with auto-wake enabled, a real Shizuku restart then started the main process while preserving the provider process.
- App Behavior validation `34057507889` exhaustively covers all 32 AutoKill/Smart Lifecycle/Sleep/Preset/Scheduler blocker masks against all requested Exit-on-Back / prevent-Shizuku-autostart combinations.
- Sleep Mode run `34075290320` established a real owned freeze on a disposable package, proved both the disabled target and durable ownership marker survive external `am force-stop`, then restarted/woke ReAppzuku and thawed only the owned target while clearing ownership.
- The earlier SQL-debug privacy boundary (`0d55aab8`/`711f8195`, validation `34065691224`) was later superseded by removing production SQL query debug logging entirely together with `AppDebugManager` and its formatter/test support in `e51873e38944e21726b96009ea23f2678fda6dfd`; final standard `34907347300`, CodeQL `34907355609` and API-37 `34907362534` passed.
- `PackageStateSource` now centralizes BackgroundAppManager's read-only running-process collection; integration commit `685d333f89692d13b7c0d8dd8514c3674eea6be1` passed the full normal gate in run `34138482661`.
- Exported-component drift is now regression-tested: commit `45650077e28a846e300fbb7ffc1668d57791dbad` requires the production manifest's `exported=true` principals to match `EXPORTED_COMPONENTS.md` and checks key platform permissions; run `34138775145` passed.
- Exported shortcut routing is now an explicit principal contract (`f3145bb0`): only authenticated secure actions can reach direct RAM-kill execution, while legacy/unknown public routes remain confirmation-bound; run `34154664082` passed.
- External API-37 abuse run `34308440409` first used a separate unprivileged attacker APK/UID and proved protected exported entrypoints reject foreign access. This is now a permanent regression in `android17-runtime.yml`: run `34668594681` built and installed a dedicated no-permission `securityProbe` APK under a UID distinct from ReAppzuku, passed the full 43-test instrumentation suite, proved no Binder exposure from the Accessibility service or either Quick Tile, proved protected `BOOT_COMPLETED` injection is denied, and then passed the launcher smoke test. OEM/launcher/widget-host diversity remains release evidence.
- Android 17/API 37 PrivilegedShell run `34316502181` started official Shizuku, used the real manager authorization dialog, reached `SHIZUKU_READY`, executed the representative typed mutation families through the real UserService and verified rollback on the disposable target. Root-specific execution and deliberately destructive families remain separate release/security evidence.
- Platform backup exclusion is a CI contract (`7c096a9b`): `allowBackup=false`, legacy Full Backup, Cloud Backup and Device Transfer exclusions all passed normal validation `34155781163`.
- Accessibility scope is locked to the minimum window-state contract by `AccessibilityServicePolicyTest`; run `34156391150` passed. External API-37 run `34302578767` additionally proves the real Android 17 ECM-approved service path and a production Settings foreground event without enabling any AutoKill mutation path.
- Update/release trust is narrowed by `4156ed6b`/`4be29c0f` and the synchronized `signed-release.yml`: only validated fork/tag/asset URLs are eligible for direct updates, and stable publish requires tag ↔ source `versionName` ↔ APK `versionName` equality.
- Backup/preset untrusted-input boundaries now cap package collections, validate preset clock/package structure and reuse the bounded backup reader for standalone preset imports (`2b8ad243`, `5f8e470c`, `893b9454`). Backup v6 additionally makes the manual AppOps/bucket/whitelist tuple an exact snapshot; API-36 run `34259632923` passed all 12 focused restore tests on the installed app/test APKs.
- A navigation audit found `LogDetailActivity` recursively naming itself as parent. The intended parent fix accidentally carried an older full manifest; zero-error lint exposed that regression. Commit `f426b55c` restored the complete last-green manifest capability set while preserving the correct `StatisticsActivity` parent, and run `34163189731` then passed unit, lint, AndroidTest compile, Room schema and APK gates.
- Commit `677cd2ac` adds a focused manifest-capability regression contract for package visibility, usage-stats exclusion and the optional Leanback/TV surface; run `34163432363` passed the complete normal gate.
- Real API-36 ProcessRecord parsing through the official Shizuku backend is proven. Android 17/API 37 now also proves real ActivityManager `ServiceRecord` parsing in `35646519446`; diagnostic `35645713842` captured the current AOSP `c:<recentCallingPackage>` suffix and the production parser was minimally extended with JVM regression coverage. OEM/physical output diversity remains release evidence.
- Platform API bounds were tightened in PR #4: API-34-only `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` is no longer passed on API 29–33, dead pre-minSdk branches were removed, and repaired-head validation `34168563677` passed.
- Locale-sensitive machine parsing was eliminated from the audited system/dumpsys token paths in PR #5 (`92f847c0`); normal validation `34259194668` passed. User-facing app-label search/sorting and machine package-name matching were then separated explicitly in PR #6 (`94f37c7d`), with normal validation `34259963846` passing.
- API 36 remains the broad repeatable Android-runtime baseline for Shizuku/reboot/process-state scenarios. Android 17/API 37 is separately proven for target-37 build/install, real Shizuku manager authorization and privileged rollback; the current product line additionally passed full run `35646519446` with `OK (42 tests)`, real system-generated `ServiceRecord` parsing, separate-UID abuse probing and launcher smoke. Remaining release-diversity gaps are physical/OEM variation, final installed-release/signing identity + rollback evidence and root-specific execution.

## Phase 7 — Android 17 / API 37

- [x] Android 17/API 37 runtime compatibility is proven on the current `android-37.0;google_apis_ps16k;x86_64` image: branch-exact run `34276106536` booted API 37, built and installed both APKs on first attempt, verified installed `targetSdk=37`, passed all 41 instrumentation tests and passed launcher crash smoke.
- [x] Plan compatible AGP/toolchain migration separately in `ANDROID17_COMPATIBILITY.md`.
- [x] Execute the API 37 lane against the committed target-37 candidate. Run `34276106536` exercised the complete instrumentation suite present at commit `5cb329948d0bd7032ec8f297c9fbd077202d34e5` plus launcher crash smoke on Android 17/API 37; deeper physical/OEM behavior remains release-diversity evidence rather than a target-37 blocker.
- [x] Migrate build tooling to an API-37-capable AGP/Gradle combination while keeping `targetSdk 36`.
- [x] Raise `compileSdk` to 37 and validate before changing target behavior.
- [x] Raise `targetSdk` to 37 after the target-37 behavior probes passed (`34274411475` probe; branch-exact normal validation `34274940182`; branch-exact Android 17 runtime + launcher smoke `34276106536`).

### Android 17 runtime evidence — 2026-09-04 to 2026-09-08

- Run `33699862420`: both app and instrumentation APKs installed successfully on API 37 and `USE_NEW_MESSAGEQUEUE` was enabled; instrumentation then exposed a lane bug because user 0 had not yet reached `RUNNING_UNLOCKED`.
- The lane was corrected to require `RUNNING_UNLOCKED`, a ready PackageManager service, 8 GiB `/data`, stable-channel emulator binaries, explicit runner libraries, and build-before-emulator resource separation.
- Run `33812905493`: API-37 image setup, app/androidTest build, stable-emulator boot, API=37 verification, `RUNNING_UNLOCKED`, PackageManager readiness and free-space checks all passed. Three non-streaming app installation attempts each pushed the 19,343,120-byte APK successfully, then failed only at the PackageManager transaction with `Failure calling service package: Broken pipe (32)`.
- No APK validation/signature/parse/`INSTALL_FAILED_*` error was observed. At that time runtime compatibility correctly remained `RISK/BLOCKED`; no further Actions retries were spent on that same preview image. This historical blocker is superseded by the 2026-09-08 evidence below.
- The temporary runtime workflow was removed from the release path; normal read-only Validate -> write-only Publish CI was restored in `031a8634db725bb93185d3e55819dc8b5165e96d`.
- Run `33814172310` proved the AGP 9.4.0 / Gradle 9.6.0 / built-in Kotlin 2.3.21 migration at `compileSdk 36`, `targetSdk 36`: unit tests, lint, AndroidTest compilation, Room schema validation and APK build all passed; publishing was skipped.
- Run `33815939003` then proved `compileSdk 37` with `targetSdk 36` through the same gates. The validated APK SHA-256 is `50f07dc229729b0df68c14d6550cf0f52c286297c8ae541fc62f57c18a5c9912`; publishing was skipped.
- The earlier preview PackageManager blocker is superseded by the current Android 17 image/tooling. Run `34274411475` first proved a temporary target-37 build could install both APKs and pass all 41 instrumentation tests. Run `34274940182` then passed the normal source-authoritative unit/lint/AndroidTest/Room/APK gates on committed target-37 commit `5cb32994`. Finally, branch-exact run `34276106536` built the committed APKs, installed app and androidTest on the first attempt, verified installed `targetSdk=37`, passed `OK (41 tests)` and passed the launcher crash-buffer smoke test. `targetSdk 37` is therefore no longer blocked by API-37 runtime evidence.

## Phase 8 — UX, i18n, maintainability

- [x] Propagate fork-specific Smart Lifecycle/App Behavior/security strings to supported locales.
- [x] Explain blocking automation directly beside disabled App Behavior controls, listing the active AutoKill/Smart Lifecycle/Sleep/Preset/Scheduler blockers from the central policy.
- [x] Split large managers behind testable facades: `PrivilegedShell`, `PackageStateSource`, `AlarmScheduler`, `Clock`/`ScheduleTime`, `BackupCodec` and central protection/background/parser policy boundaries now isolate the named high-risk seams.
- [~] Keep `CHECK_MATRIX.md` status/evidence current after every high-impact change (refreshed through 2026-09-21; ongoing discipline).


### Maintainability evidence — 2026-09-05 to 2026-09-21

- Root-shell hardening is regression-locked in `fd1f533b8c5a8ea51a3ecbc413b2fe17bf1f0910`: all eight audited absolute `su` candidates and their fallback order are enforced, bare PATH-based `exec("su")` is rejected, and standard `35643580520` plus CodeQL `35643571945` passed. Real KernelSU/Magisk execution remains physical/root diversity evidence.
- Android 17/API-37 `ServiceRecord` runtime coverage is now deterministic without violating background-start policy. Diagnostic `35645713842` captured the real current AOSP `c:<recentCallingPackage>` suffix; final standard `35645966192`, CodeQL `35645972117`, targeted API-37 `35646059393` and full API-37 `35646519446` passed, with `OK (42 tests)`, separate-UID abuse-probe PASS and launcher smoke. The probe service exists only in the debug source set.
- Non-publishing signed-release preflight `35642800123` demonstrated fail-closed behavior on current `main`: source authorization passed, then execution stopped before key decode/build/publish because the stable signing identity secrets are not configured. Final production signing identity and rollback remain explicit release evidence rather than a CI implementation gap.
- Pinned Java/Kotlin CodeQL is now part of the permanent assurance line. Initial normal/CodeQL validation passed in `34672629853` / `34672626846`; actionable findings were then fixed without breaking root compatibility, with repaired-head normal `34673771828`, CodeQL `34673776649` / PR `34673964881`, and API-37 `34673781807` all passing.
- The unified user-facing On-demand mode is integrated and derives from the existing prevent-Shizuku-autostart + full-exit-on-Back controls rather than introducing a third lifecycle state. Normal `34674754810`, CodeQL PR `34700033242` and Android 17/API-37 runtime `34700010704` passed; Help documentation was propagated across EN/DE/ES/RU/UK/ZH.
- Dependency/toolchain maintenance remained strict after the 2026-09-12 assurance refresh: Material 1.14.0 (`b186ed65687752c7dd4a2c5386f11575ab122e75`), AppCompat 1.8.0 (`bb28fbcbe9d6df7f9220427488f8f21deb059064`), Core KTX 1.19.0 (`8875aa7144ad5984430800a340637631b41c899a`) and Kotlin/Compose 2.4.10 (`0724d4ff6d22dd28d6338c7b895b3079b8cba492`) were integrated with locked/verified metadata. Kotlin 2.4.20 was deliberately not accepted while CodeQL 2.27.0 could not analyze it. The final Kotlin 2.4.10 line passed standard `34784329095`, CodeQL `34784307666` and API-37 `34784334462`.
- Root-shell resolution no longer relies on PATH discovery and production debug logging was first disabled in `67ed945e38287eea295d1a84de46235e103c7835`; standard `34738300120`, CodeQL `34738294468` and API-37 `34783416166` passed. The obsolete user-facing Debug settings/help were then removed in `522d175ad86c09d9971ce68e61cae0171af8c1d7`, validated by `34785500319` / `34785475308` / `34785505288`.
- The remaining internal `AppDebugManager`/SQL-debug subsystem and debug-only scaffolding were removed in `e51873e38944e21726b96009ea23f2678fda6dfd`. Final product-head validation passed standard `34907347300`, CodeQL `34907355609` and Android 17/API-37 `34907362534`; the API-37 lane reported `OK (41 tests)`, a passing separate-UID abuse probe (`binderExposed=false`, `bootBroadcastDenied=true`) and a clean launcher smoke test. Functional scheduling counters were explicitly preserved.
- CodeQL alert #9 (`java/android/missing-certificate-pinning`) remains an explicitly accepted medium warning for the credential-free public `api.github.com` release-metadata check. Fixed certificate pins against GitHub's externally managed/rotating certificate infrastructure would add update-availability fragility; standard platform TLS validation remains the deliberate policy unless the update transport changes.
- Scheduling facade gate `33946729221` passed unit tests, lint, AndroidTest compilation and debug APK build before integrating `Clock`/`ScheduleTime` and `AlarmScheduler`.
- BackupCodec gate `33946888672` compiles the focused Android codec tests and passes the same application validation before integration.

- App Behavior exposes the exact active continuity blockers from `BackgroundWorkPolicy` rather than duplicating feature-state logic in the UI.
- `Clock` plus pure `ScheduleTime` make daily scheduling deterministic under JVM tests, while `AlarmScheduler` centralizes AlarmManager availability and exact-vs-best-effort behavior.
- `PresetManager` and `RestrictionsScheduler` keep their existing public constructors but receive clock/alarm dependencies through internal injection points.
- `BackupCodec` now owns bounded/versioned JSON envelope decode/encode while `BackupManager` retains the transactional storage/rollback side effects; the named facade extraction item is closed.
- `PackageStateSource` owns read-only `ps` collection/parsing for `BackgroundAppManager`, replacing four duplicated parsing paths with one JVM-testable contract; run `34138482661` passed the full normal gate.
- `AppLaunchTriggerPolicy` now owns accessibility app-launch eligibility and duplicate suppression as a pure JVM-testable contract without changing event ordering or AutoKill side effects; run `34153818522` passed full normal validation.
- Final cleaned-head validation `34154071232` passed after removing the blocked synthetic A04 service harness; the guarded runtime probe now represents only the actually proven real API-36 `ProcessRecord` contract.
- CM-P2-05's original SQL-bind redaction (`34065691224`) is now superseded by complete removal of production SQL query debug logging, `SqlQueryLogFormatter`, its formatter-only test and the obsolete `AppDebugManager` path in `e51873e38944e21726b96009ea23f2678fda6dfd`; final standard `34907347300`, CodeQL `34907355609` and API-37 `34907362534` passed.
- `ExportedComponentsParityTest` prevents silent growth/drift of production `exported=true` surfaces relative to `EXPORTED_COMPONENTS.md` (`34138775145`).
- The original “no automated test tree” finding is closed: the branch now has broad JVM policy/parser/security tests plus Android instrumentation for restore/migration, boot/restart, permission/death/rebind, process topology/process death, shortcut abuse and real privileged commands. Remaining matrix gaps are surface-specific runtime diversity.
- `ShortcutEntryPolicy` makes the exported shortcut principal boundary deterministic and JVM-testable; run `34154664082` passed.
- `AccessibilityServicePolicyTest` locks the minimal accessibility metadata contract; run `34156391150` passed.
- `ReleaseAssetPolicy` and `ReleaseWorkflowPolicyTest` bind updater/release behavior to the fork, stable numeric tag, exact APK asset and tag/source/APK version identity while preserving least-privilege publish separation.
- `PresetInputPolicy`, `BackupCollectionPolicy` and bounded `BackupFileStore` reuse close avoidable untrusted-import amplification paths without changing normal backup semantics.
- `NavigationManifestPolicyTest` protects activity parent relationships. After lint caught an unintended older-manifest overwrite, `f426b55c` restored the reviewed capability manifest while retaining the intended LogDetail parent fix; full validation `34163189731` passed.
- `ManifestCapabilityPolicyTest` now fails early if the reviewed `QUERY_ALL_PACKAGES` justification, no-`PACKAGE_USAGE_STATS` contract, Leanback banner or optional touchscreen/Leanback declarations drift; full validation `34163432363` passed.
- PR #4 removed obsolete minSdk branches and corrected the foreground-service type boundary so `SPECIAL_USE` is referenced only on API 34+; repaired-head normal validation `34168563677` passed.
- PR #5 made machine-readable system/dumpsys token casing deterministic with `Locale.ROOT` across 20 parser paths and retired the matching lint exceptions; run `34259194668` passed.
- PR #6 made app-label search/sorting explicitly user-locale aware while package identifiers use `Locale.ROOT`; run `34259963846` passed.
- Backup v6 manual-restriction snapshot semantics have real API-36 execution evidence: run `34259632923` installed both APKs and passed `BackupManagerRestoreTest` 12/12.


## Phase 9 — Unified per-app lifecycle policies

Goal: replace overlapping global Auto-Kill / Smart Lifecycle ownership with one canonical per-app policy model. Every package has at most one lifecycle strategy; presets are reusable templates, not a second execution engine.

- [x] Define canonical Room-backed `AppPolicy` model with the execution strategies `UNMANAGED`, `PROTECTED`, `SMART` and `IMMEDIATE`. `Custom` is a presentation state for a policy that differs from its preset, not a fifth engine.
- [x] Add reusable `PolicyPreset` storage in the same schema migration so the foundation does not require another immediate Room version bump.
- [x] Add central `AppPolicyResolver` with explicit-policy precedence and a bounded legacy fallback. During transition, Smart Lifecycle owns legacy-blacklisted packages before Immediate Auto-Kill so one app cannot be controlled by both engines at once.
- [x] Add JVM regression coverage for explicit precedence, Smart-vs-Immediate conflict resolution, whitelist protection and blacklist targeting.
- [x] Add Room 11 -> 12 migration and extend the supported v2 migration chain to validate the new policy tables while preserving existing statistics.
- [x] Build and activate retry-safe legacy migration from whitelist/blacklist, Smart Lifecycle, active/permanent/still-owned Sleep Mode state and background restrictions into canonical per-app policies. Migration-owned rows carry explicit provenance and are fingerprint-bound to the live legacy snapshot; stale rows are ignored immediately while explicit user-owned rows always win.
- [x] Route Auto-Kill and Smart Lifecycle execution through the policy resolver. A current migration snapshot disables direct legacy fallback entirely; a stale snapshot re-enables bounded legacy fallback until the next process-start reconciliation, preventing dual ownership without making old UI edits stale.
- [x] Add per-app Policy Editor reachable from the main app list with strategy, preset, delays, kill method, boot cleanup, background restriction, protection toggles and triggers. Explicit saves override migrated legacy ownership and preserve preset/customized provenance.
- [x] Seed stable built-in policy presets (Never touch, Messenger, Media, Balanced, Rarely used, Aggressive), expose localized display names, and support user-created presets via `Save as preset`.
- [x] Rename the existing two time-window Auto-Kill presets to Automation Schedules and keep them separate from reusable per-app Policy Presets.
- [x] Add `PACKAGE_ADDED` handling with a durable setup queue: `Ask after install`, `Apply default preset`, or `Leave unmanaged`; use a notification/deep link instead of launching an Activity over the foreground app.
- [x] Add main-list policy badges and filters for Managed, Smart, Immediate, Protected and Needs setup.
- [x] Extend versioned backup/restore to include app policies, policy presets and new-app defaults transactionally.
- [x] Remove obsolete blacklist/whitelist/Smart Lifecycle settings UI only after migration and execution parity are proven.
- [~] Validate the completed model on API 37 plus physical/OEM devices, including install-notification flow and conflicting legacy configurations. Deterministic API-37 completed-model coverage passed in run `37149945404`; physical/OEM release-diversity evidence remains open.

### Phase 9 migration evidence — 2026-09-26 to 2026-10-01

- Legacy policy migration is now activated on normal-process startup and remains retry-safe. Room schema 13 adds policy provenance: pre-existing schema-12 rows default to `SOURCE_EXPLICIT`, while only migration-generated rows are tagged `SOURCE_LEGACY_MIGRATED` and therefore eligible for replacement.
- A deterministic SHA-256 fingerprint covers every legacy input that changes lifecycle ownership, Immediate trigger bits, Smart delays/boot cleanup or restriction strength, including active legacy preset state and still-owned frozen Sleep Mode apps. Runtime verifies that fingerprint before treating migration-owned rows as canonical.
- If the legacy UI, an old preset path or restore changes effective state after migration, the fingerprint mismatches immediately. Auto-Kill and Smart Lifecycle then ignore only migration-owned rows and use the bounded live legacy resolver; explicit policies remain authoritative. The next normal-process startup rebuilds the legacy-owned rows and commits the new fingerprint only after the Room transaction and a post-transaction snapshot recheck succeed.
- A valid migration snapshot disables direct legacy fallback for packages without a policy. This preserves whitelist materialization semantics: apps installed after migration stay unmanaged until the dedicated new-app default/setup flow exists.
- Active timer Sleep Mode, permanent freezes and still-owned frozen timer apps take lifecycle precedence and become `PROTECTED`; the existing Sleep Mode preference data remains the freeze source of truth for now.
- Background restriction strength migrates independently with MANUAL > HARD > MEDIUM > SOFT precedence; manual AppOps/bucket/whitelist detail remains in its existing exact per-package preference keys until the Policy Editor/backup block moves that detail.
- Six stable built-in preset IDs are seeded idempotently. Legacy-derived policies intentionally start as Custom (`presetId=null`, `customized=true`) instead of pretending to match a reusable preset.

### Phase 9 Automation Schedule separation evidence — 2026-10-01

- PR #65 / merge `f37505e6035b67a30170a625e51d2994a8b75e32` separates the two legacy time-window Auto-Kill schedule slots from Room-backed reusable `PolicyPreset` templates in UI/API terminology.
- New UI code goes through `AutomationScheduleManager`; the historical `PresetManager` and `PresetModel` remain the compatibility implementation/serialized model only.
- Existing installs keep their schedule state unchanged: `preset_1_prefs`, `preset_2_prefs`, `PRESET_ACTIVATE` / `PRESET_DEACTIVATE`, `preset_backup_` and legacy serialized fields were deliberately not renamed.
- Historical default names such as `Preset 1` / `Preset 2` are displayed as localized Automation Schedule names without requiring a storage migration; new JSON exports use `automation-schedule-N.json`.
- Source-authoritative regressions lock the compatibility identifiers and verify that Room `PolicyPreset` remains free of time-window fields.
- Final-head validation passed: standard `36862912195`, CodeQL `36862911361`, GitHub Advanced Security `36862917104`; PR review threads were empty.
- No Room schema or automation execution behavior changed in this block.

### Phase 9 new-app setup evidence — 2026-10-01

- PR #66 / merge `93502e2bd44c50dac5a2c9b2432258a928da9c0b` adds a manifest-declared `PACKAGE_ADDED` receiver and a durable SharedPreferences-backed Needs-setup queue without changing Room schema 13.
- The default mode is `Ask after install`. Ask and `Leave unmanaged` write an explicit canonical `UNMANAGED` policy, preventing stale legacy fallback or migration-owned rows from claiming a newly installed package. Only a user-selected `Apply default preset` mode may automatically write a managing policy.
- Reinstalls are protected from legacy capture: an existing policy blocks setup only when it is explicitly user-owned. Migration-owned legacy rows may be replaced by the new-app safety/default decision.
- `PACKAGE_ADDED` ignores `EXTRA_REPLACING`, validates package names, rejects system/persistent/protected packages, uses `goAsync()`, and never launches an Activity over the foreground app.
- Ask uses a notification/deep link to the per-app Policy Editor when notification permission is available. A dedicated New app setup settings page also exposes mode/default-preset controls, pending count and a review-next action so the queue remains usable when notifications are denied or dismissed.
- Pending entries replay after legacy migration on normal process startup, survive process death/reboot, prune missing packages, and clear only after an explicit editor save, automatic preset application or Leave unmanaged resolution.
- The exported-component security review now documents `PackageAddedReceiver`; source/JVM regressions cover receiver action/replacement filtering, explicit-Unmanaged safety, migration-owned reinstall handling and migration-before-replay ordering.
- Final-head validation passed: standard `36919753802`, CodeQL `36919752974`, GitHub Advanced Security `36919755791`; PR review threads were empty.

### Phase 9 main-list policy status evidence — 2026-10-01

- PR #67 / merge `c4bdeeae1a87cf8343410fcd83c30bf134f5e78d` surfaces effective lifecycle ownership in the main running-app list without changing execution or Room schema 13.
- One background `AppPolicyListSnapshot` is captured per app scan, reading Room and relevant legacy/new-app preference state once; row binding and filtering are then in-memory only.
- Status resolution reuses the production migration freshness boundary and `AppPolicyResolver`: explicit policy precedence remains intact, stale migration-owned rows fall back to live legacy state, and a current migration snapshot suppresses direct legacy fallback.
- Compact badges expose `Managed · Smart`, `Managed · Immediate`, `Protected` and `Needs setup`; unmanaged rows intentionally remain visually quiet.
- The existing sort dialog now also filters by `Managed`, `Smart`, `Immediate`, `Protected` and `Needs setup`. Managed means exactly Smart or Immediate, matching `AppPolicyResolver.isManagedStrategy()`; multiple filters use OR semantics.
- `Needs setup` comes only from the durable new-app queue. Protected/persistent fail-safe status has priority over queue presentation.
- Policy-filter selection is persisted independently of the legacy whitelist/blacklist/Smart controls, which remain available during the transition.
- Final-head validation passed: standard `36926009009`, CodeQL `36935664025`, GitHub Advanced Security `36935665475`; PR review threads were empty.

### Phase 9 backup v7 evidence — 2026-10-01

- PR #68 / merge `227f11057a16c8f8b4bfc63217cb06dbfb449631` advances the bounded backup contract to v7 and adds canonical Phase 9 state without exporting migration-owned compatibility rows.
- Backups include explicit `AppPolicy` rows, user-created `PolicyPreset` rows, new-app setup mode/default preset and the durable Needs-setup queue. Built-in preset IDs remain code-owned and are not imported as user data.
- Restore validates package identifiers, strategies, preset relationships, trigger masks, lifecycle delays, restriction strength, timestamps and queue invariants before the first durable write.
- Main preferences plus Phase 9 Room state are snapshotted and roll back together; an injected `AFTER_PHASE9_DB_COMMIT` failure proves recovery after the database commit. v6 and older backups preserve existing Phase 9 state rather than erasing data they could not encode.
- The output path now enforces the same 2 MiB bound as input, preventing a locally generated configuration from bypassing the backup envelope limit.
- Validation passed: standard `36937049584`, CodeQL `36937623249`, GitHub Advanced Security `36937624956`; PR review threads were empty.

### Phase 9 legacy settings UI retirement evidence — 2026-10-02

- PR #69 removes the user-facing kill-mode, whitelist, blacklist and global Smart Lifecycle controls after migration, execution, editor, new-app and backup parity were established. Historical preference keys remain compatibility/migration inputs and are not destructively deleted.
- Canonical SMART policies no longer depend on the hidden legacy `KEY_SMART_LIFECYCLE_ENABLED` switch. Smart execution resolves effective Room ownership first and uses legacy blacklist state only as a bounded stale-migration fallback.
- Smart WorkManager scheduling is reconciled from effective SMART ownership on process startup, Policy Editor saves, new-app decisions and backup restore/rollback. Devices with no effective SMART policy do not retain the 15-minute periodic worker.
- A Smart pass computes the effective SMART package set before resolving shell permission or issuing dumpsys calls, avoiding privileged/background work when no package is owned by Smart.
- The legacy Smart flag no longer counts as a main-process continuity blocker, so an invisible migrated setting cannot disable On-demand behavior after its UI is retired. Auto-Kill, Sleep Mode, active Automation Schedules and restriction schedules retain their existing continuity semantics.
- Source-authoritative regressions lock both halves of the transition: retired controls cannot reappear accidentally, while legacy keys remain available to the migration/restore compatibility layer.
- Implementation-head validation on `c7220467470540d04944674f24b2314d090f2430` passed: standard `36947595322`, CodeQL `36947595766`, GitHub Advanced Security `36947596442`; PR review threads were empty.

### Phase 9 migration principles

- Existing installs must keep their current effective behavior until an explicit policy is written or legacy migration completes.
- A package may never be owned by Smart and Immediate automation simultaneously.
- Policy presets describe per-app behavior; Automation Schedules describe when policies/triggers are active. These concepts stay separate.
- Newly installed apps default to no privileged mutation unless the user selected an automatic default preset.
- System/persistent/protected packages continue to fail safe regardless of policy data.

## Phase 10 — Software-only release and compatibility evidence

This phase separates tests possible in hermetic CI/emulators from external release acceptance. A green emulator test must never be reported as physical/OEM, KernelSU/Magisk, production-key custody or genuine rollback evidence. Each item is independent and must be closed by branch-exact CI results.

- [x] Prove in-place update semantics on API 24 with two *different-versionCode* APKs signed by the **same runner-local debug key**: PR #85 / merge `f72032e613436697e567bb8dd6a3658f38b20164`, run `37804848630` built v28 then v29 from the same source, verified identical SHA-256 signing certificates, installed v29 over v28 without uninstall, and passed `API24_SAME_KEY_UPDATE_DATA_PASS` for the original explicit Room policy and SharedPreferences marker. This does **not** validate production signing, physical devices or rollback.
- [x] Reject mismatched signatures and versionCode downgrades on an API-24 emulator while preserving data: PR #87 / merge `845838d7530bc61603710f51be278770588de0d9`, run `37818081642` first passed the previous same-signer 28→29 update, then Android returned `INSTALL_FAILED_UPDATE_INCOMPATIBLE` for a newly generated alternate test key and `INSTALL_FAILED_VERSION_DOWNGRADE` for the original v28 APK. The installed v29, SharedPreferences marker and explicit Room `PROTECTED` policy survived **both** refused installs; each stage passed `OK (1 test)`. No `-d`, uninstall or data clear was used. Standard `37818073462` and CodeQL also passed. No physical-device/release-key or rollback evidence implied.
- [x] Harden offline updater metadata against stale/unordered, malformed and hostile GitHub release records: PR #89 / merge `8a839fc0be991fb141b3ea6c7e9360b81a160696`, standard `37827922518` and CodeQL `37827914054` passed. `ReleaseMetadataPolicy` selects the highest **numeric stable** version regardless of list order, retains only exact fork-owned APK URLs, refuses alternate metadata redirects, bounds input to 1 Mi chars before JSON parsing, limits release/asset iteration and changelog length. Pure-JVM fixtures cover draft/prerelease, malformed/overlong tags, foreign/mismatched APK links, missing/rolling-only releases and oversize rejection. This is local parsing/build validation, not a live network compromise test or release-signature proof.
- [~] Expand bounded API-24 policy-editor and new-app setup coverage: PR #90 passed 12 queue/persistence tests (`37833638389`), PR #91 passed 14 cancel/Review-next tests (`37836914489`) and PR #92 passed 16 tests for explicit PROTECTED editor-save/reopen, isolated queue clearing and pre-Oreo notification PendingIntent-to-editor routing (`37841075862`), with same-signer update and rejected-signature/downgrade guards still green. Broader notification-denial/replay recovery, API-37 cross-version behavior, OEM/physical diversity and real release signing remain separate evidence.
- [~] Add deterministic background recovery/failure-injection coverage: PR #93 validates retry-safe installed-app inventory merges and unique periodic WorkManager identity; PR #94 tests API37 denied-to-granted exact-alarm access; PR #95 verifies same-UID SIGKILL/relaunch preserving a real WorkManager UUID on API24/API37. PR #96 proves **actual Result.retry() followed by real Worker execution and Result.success() after app PID loss** using an isolated **debug-variant-only** Worker, with matching request UUID and output marker (API24 `37861331700`, PID 4142->4204; API37 `37861336462`, PID 4946->5024). Source-only standard build `37861273310` and CodeQL `37861329026` passed. PR #100 adds a **debug-only post-reconciliation exception** to the **actual production NewAppInstallReconcileWorker class** (release no-op), then proves a real WorkManager retry, same request UUID and successful second execution across same-UID SIGKILL/relaunch: API24 `37875292241`, API37 `37875296175`. Standard CI with explicit nonpublishing release Java compile `37875726931` and CodeQL `37875724510` passed. **Still partial:** no injected failure *inside* the per-package handler, deterministic 15-minute periodic dispatch, low-memory/OEM eviction, physical-device/root/Shizuku continuity or production signing/rollback.
- [~] Recheck backup-v7 validation, restore rollback and ContentResolver stream boundaries. PR #97 passed malformed/inconsistent v7 rejection, denied unexported content-URI read/write and AFTER_MAIN_COMMIT preference rollback (API24 `37864015667`, API37 `37864020498`). PR #98 passes **real ContentResolver read of >8 KiB from an androidTest-only reliable-pipe provider followed by closeWithError**, proving IOException propagation and no returned partial/valid import String; normal truncated EOF is rejected without durable writes and a complete fixture decodes. Final runs: API24 `37866118734` (3/3 streaming tests + same-debug-key update/wrong-signer/downgrade checks); API37 `37866124868` (3/3 streaming tests + security/launcher checks); standard `37866129166`, functional CodeQL `37866103997` green. Snapshots compare all backup-relevant preferences and existing Room counts; only asynchronous, **nonportable** `new_app_known_packages` is excluded from preference equality to avoid an unrelated WorkManager inventory race. **Partial:** no real SAF/DocumentsUI user grants or persistable-URI expiry, OEM/physical provider diversity, or newly tested AFTER_PHASE9_DB_COMMIT rollback.
- [ ] Review software-only CI/audit coverage for app-list filters, Policy Editor accessibility, notification fallback and exported-component denial. Add targeted regressions for verified gaps only.
- [ ] Keep action/dependency locks, no-secret validation, pin review, CodeQL and release provenance current; avoid speculative dependency upgrades with uncontrolled transitive churn.

### Outside software-only scope — hardware and controlled release authority

- [~] Physical/OEM Phase-9 matrix, real Magisk/KernelSU backend, OEM Accessibility/Doze/launcher/provider differences: **NOT RUN** until an authorized disposable real device is available.
- [ ] Stable signing identity, secure offline keystore custody, actual same-key production release update, data-survival acceptance and tested rollback/forward-fix path: **BLOCKED** on release-key ownership and device access.
- [ ] Publishing a stable release is prohibited until the external checks and required identity/signature evidence are complete.

## Phase 11 — Product and UX follow-up (software-testable candidates)

Scope new product work from a reproducible user problem or documented usability audit. Do not auto-enable privileged actions, silently change existing user policies or reintroduce parallel lifecycle ownership.

- [ ] Audit the main app list, policy badges/filters and Policy Editor for unambiguous effective-versus-requested state, stable ordering, accessible touch targets and empty/error states; close findings individually.
- [ ] Review new-app setup UX including multiple pending installs, denied notifications, review-next navigation, restore and lifecycle replay; add deterministic editor/notification tests.
- [ ] Audit Automation Schedule terminology, clock edge cases, timezone/DST transitions and consistency with reusable Policy Presets; use pure JVM clock tests and emulator alarms where possible.
- [ ] Profile in-memory app-list snapshot generation, repeated app scans and background work with synthetic workloads; optimize only documented regressions and preserve canonical policy ordering.
- [ ] Extend localized/a11y runtime checks for EN/DE/ES/RU/UK/zh-CN where tool support exists; avoid adding user-visible strings without localization.
- [ ] Triage outstanding maintainability issues and dependency updates in bounded PRs, preserving security and migration guarantees.

## Stable-release gate

A candidate is not stable until all P0 findings are `PROVEN`, no P1 is unowned, reboot/permission/migration paths have repeatable Android evidence, update provenance is correct, exported privileged boundaries are reviewed, and release artifact/signing identity + rollback path are recorded.
