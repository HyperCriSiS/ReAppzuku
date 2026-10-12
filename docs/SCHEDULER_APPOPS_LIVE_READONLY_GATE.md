# Real AppOps read-only evidence — Android 17 emulator

Date: 2026-10-11 · Issue: [#130](https://github.com/HyperCriSiS/ReAppzuku/issues/130) · PR: [#151](https://github.com/HyperCriSiS/ReAppzuku/pull/151)

## Exact evidence

| Gate | Run | Outcome |
|---|---|---|
| JVM tests, lint, Room schema, AndroidTest compile/debug build | [38179684631](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38179684631) | PASS |
| CodeQL | [38179669307](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38179669307) | PASS |
| Android 7/API24 full smoke, retry and upgrade | [38179689779](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38179689779) | PASS; **not live UID-shell proof** |
| Android 17/API37 real AppOps shell + external abuse probe + launcher | [38179694249](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38179694249) | PASS, `OK (3 tests)`, no ignored live test |

Exact implementation head: `35f68cd22882ae7b1121519a7e3f531c960fe99f`. Squash merge: `873aa9632bbd39844ce7eaf77b1ec72af43db1fd`.

The API37 emulator's **instrumentation output** explicitly contains:

```
RZ_LIVE_APPOPS_API37_PACKAGE_QUERY=EXIT_ZERO_NONEMPTY
RZ_LIVE_APPOPS_API37_RESULT=CONSISTENT_DIAGNOSTIC_ONLY
RZ_LIVE_APPOPS_API37_SCOPE_COUNT=2
RZ_LIVE_APPOPS_API37_RESTORE_ALLOWED=false
OK (3 tests)
```

This is actual Android instrumentation shell output (not the earlier synthetic AppOps test). Two named operations (`WAKE_LOCK` and `RUN_IN_BACKGROUND`) were queried through distinct package and numeric UID scopes. The typed catalog was consistent over the bounded double-read window, with real installed signer/UID identity before and after. The test does **not** log individual modes and therefore does not claim which exact mode each operation held, or that an unreported mode originally had no persisted entry.

## Why the shell invocation changed

The first live test had a misleading **green** API37 job: a JUnit `AssumptionViolatedException` caused an `INSTRUMENTATION_STATUS_CODE: -4` skip. It was detected by reviewing the raw instrumentation records, rather than trusting the GitHub workflow result. The origin was the mistaken assumption that `UiAutomation.executeShellCommand` itself interprets `;`, stderr redirection and shell variable expansion. [AOSP UiAutomationConnection](https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/app/UiAutomationConnection.java) uses `Runtime.exec(command)`, so the test now invokes an explicit `sh -c` script passed as one token (spaces in fixed validated command arguments are replaced with shell `${IFS}`). The script returns an exit-code marker; errors, missing marker, nonzero exit and excessive output are rejected. Emulator identity is asserted, not treated as an optional skip. A `false` command is used as a harmless negative exit control. The corrected run had no `-4` skips.

## Safety and remaining uncertainty

- `SchedulerAppOpsReadOnlyShellSource` is **not connected** to `RestrictionsScheduler`. Its `ShellManager` constructor is available for future diagnostic use only; production Root/Shizuku readiness and permission behavior are not proven by instrumentation-shell rights.
- The strict catalog can detect inconsistent *observations* but cannot produce an atomic snapshot or capture unseen shared-UID state.
- `NOT_REPORTED` means not reported by that command, **not** proven original `ABSENT`. A `Default mode:` hint is not an explicit stored `MODE_DEFAULT`.
- Supported shell APIs provide no proven narrow inverse for originally absent package operations; **UNSUPPORTED → REVIEW_REQUIRED** remains binding. Do not use broad `appops reset` or substitute `set allow`/ `set default`.
- Full authenticated recovery (signer/reinstall continuity, OEM physical devices, production Shizuku/root, reboot/flash-power-loss, rollback/fault injection and v1/RZJ2 format segregation) is **not** released by this test. Phase 11 remains PARTIAL and #130 stays OPEN.


## Single-backend provenance for read-only AppOps (PR #153, 2026-10-12)

The ordinary `ShellManager.runShellCommandForResult()` can intentionally retry a failed Root command using Shizuku. This is inappropriate for a supposedly coherent single-source AppOps snapshot. The new `ShellManager.runPinnedReadOnlyAppOpsGet()` restricts arguments to one AppOps GET (no setters, resets, shell operators or extra flags), pins the chosen Root or Shizuku executor, and rejects a lost/changed backend before or after the command. The detached `SchedulerAppOpsReadOnlyShellSource.fromShellManager()` captures the initially ready backend and rechecks it around each query. A backend change, missing permission or failed command yields UNKNOWN/no partial catalog.

**Exact final-head evidence:** standard [38198814652](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38198814652), CodeQL [38198807225](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38198807225), API37 [38198824472](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38198824472), full API24 retry [38199353396](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38199353396): PASS. First API24 `38198819845` stalled in unchanged upgrade step and was cancelled. API37 live UiAutomation logging again showed `CONSISTENT_DIAGNOSTIC_ONLY`, two operations, `RESTORE_ALLOWED=false` and `OK (3 tests)`.

**Evidence boundary:** Root/Shizuku loss and switching are synthetic **JVM injected state** tests. Neither backend has yet been independently tested under real revocation/service death on a physical/production device. API37 instrumentation uses `UiAutomation`, not production Root/Shizuku. Non-atomic reads, unseen shared UID data and absent package-op inverse remain blockers. Do not connect this to `RestrictionsScheduler`, v1 or RZJ2 recovery; #130 remains OPEN.


## Actual API24 Java catalog probe (PR #155, 2026-10-12)

The former API24 matrix established host-shell raw observations but had **not** run the Java catalog with real API24 shell responses. The nonmutating `SchedulerAppOpsApi24LiveReadOnlyInstrumentationTest` now runs on a verified disposable Android 7/API24 emulator. It uses real `UiAutomation` shell output and two independent package/UID target operations; it never changes any AppOp or app policy. The CI gate requires `OK (2 tests)`, rejects `INSTRUMENTATION_STATUS_CODE: -4` **within this targeted step**, and checks typed markers. The remaining broad API24 test suite can contain unrelated intentionally ignored version-gated tests.

| Actual API24 instrumented signal | Observation |
| --- | --- |
| Package-targeted AppOps GET | `EXIT_ZERO_NONEMPTY` |
| Numeric-UID `WAKE_LOCK` | `UNKNOWN` |
| Numeric-UID `RUN_IN_BACKGROUND` | `UNKNOWN` |
| Complete two-operation catalog | `QUERY_UNKNOWN` |
| Partial catalog records | **0** |
| Privileged restore permitted | `false` |
| Targeted tests | `OK (2 tests)`, no targeted JUnit skip |

**Exact tested implementation head:** `4568021f582112216d5d39a5022be21254a15049`. PR #155 squash `24cc0ac42a95421ac7e5d6fb49eba1c44e9a1672`. Passed runs: [standard 38200901024](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38200901024), [CodeQL 38200886337](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38200886337), [full API24 38200905055](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38200905055), [real targeted API37 38200909331](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38200909331). API37 still reported `CONSISTENT_DIAGNOSTIC_ONLY` / scope count 2 / `RESTORE_ALLOWED=false` and `OK (3 tests)`.

**Interpretation:** The API24 Android platform's numeric UID AppOps CLI response cannot be trusted as complete original-state provenance in this lane. `UNKNOWN` is **not** an original absent UID override; no layer is promoted to explicit mode, no partial record is stored and no fallback rollback is permitted. This does not certify API24 production Root/Shizuku semantics or physical-device/OEM AppOps implementations. Original ABSENT package-level per-op inverse remains **UNSUPPORTED**, and issue #130 remains OPEN.


## Full installed-identity source binding (PR #157, 2026-10-12)

A source constructed for an old installed app instance must NOT be reused to diagnose the new instance simply because the numeric UID was recycled or unchanged. `SchedulerAppOpsReadOnlyShellSource` now exposes its complete immutable installed identity through `IdentityBoundSource.matchesBoundIdentity`. The detached catalog and single-op diagnostics check exact bound-source equality against their fresh independent PackageManager observations before any AppOps shell command, and again after their postflight checks. The bound fields are package name, numeric UID, user, signer SHA-256, version, first install time, last update time and the observed unique visible UID association. Failure yields `IDENTITY_CHANGED` with no partial data or recovery authorization. The tests use injected mismatch fixtures, not a live reinstall attack, and a finite pre/postflight observation is NOT a lifetime guarantee.

**Implementation-head evidence:** standard [38203165738](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38203165738), CodeQL [38203152468](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38203152468), full API24 [38203170068](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38203170068), API37 targeted live shell [38203174204](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38203174204): all PASS. API24 actual numeric-UID shell mode responses remain UNKNOWN; its AppOps-specific test passed without skipping, reporting `QUERY_UNKNOWN`/0 scopes. API37 actual shell produced `CONSISTENT_DIAGNOSTIC_ONLY`, 2 operations and `RESTORE_ALLOWED=false`; all three targeted tests executed. This remains **read-only instrumentation shell**, not proof of production Root/Shizuku, real reinstall race control, originally-ABSENT package AppOps inverse, atomic global snapshot, flash durability or safe privileged scheduler restoration. Issue #130 stays OPEN.


## Per-UserService Shizuku session continuity for read-only AppOps (PR #159, 2026-10-12)

The previously pinned `SHIZUKU_READY` **enum** alone cannot prove that two AppOps queries used the same underlying Shizuku UserService: the service can disconnect and reconnect before the next state read, leaving the exact same readiness enum. PR #159 makes the dedicated read-only `ShellManager.AppOpsReadOnlySession` pin hold a reference to the already-connected `IShellService` and a monotonic epoch incremented on Shizuku binder arrival/death, UserService connection/disconnection and explicit unbind. The pin is captured without requesting permission or binding any new service; its checks reject missing permission, dead/replaced Binder, epoch change, or backend change. The one allowed `cmd appops get` runs directly on the **pinned service** and is rechecked after execution; no generic Root→Shizuku fallback and no `awaitUserService()` rebound are permitted.

**Exact-functional-head CI:** [standard 38299999355](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38299999355), CodeQL PASS, [API24 38300006593](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38300006593) and [API37 38300014161](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38300014161): all PASS. API24 actual numeric-UID queries remain UNKNOWN and return no partial modes; API37 real UiAutomation still reports two coherent read-only operations and `RESTORE_ALLOWED=false`. Four new JVM scenarios address synthetic READY→READY service exchange, mid-command exchange, unready source construction and stable controls. There is **NO claim** that Shizuku died/restarted on the disposable emulator.

**Remaining acceptance blockers:** production Shizuku UserService kill/binder death/regrant and actual Root privilege loss were **not** tested live; no complete shared-UID or atomic original capture, no proved single-op inverse for originally ABSENT package AppOps, no signed reinstall continuity/physical OEM/flash power-loss or authorized automatic privileged recovery. RZJ2/v1 remain evidence only; Issue #130 stays OPEN / Phase 11 PARTIAL.


## Timeout-bound parallel-drain Root AppOps GET evidence (PR #161, 2026-10-13)

The generic `ShellManager.executeRootCommandForResult` drains stdout before stderr and has no timeout; a sufficiently noisy or stalled root process can deadlock. This method and its existing consumers remain unchanged. **Only** `ShellManager.runPinnedReadOnlyAppOpsGet` for a pre-captured **Root** pin now delegates to `RootAppOpsReadOnlyExecutor`, which rejects anything other than one validated `cmd appops get --user ...` operation *before creating su*, limits the complete operation to eight seconds, drains stdout/stderr concurrently and caps captured stdout at 4096 bytes. Any stderr, failed exit, missing/empty/oversize output, interruption or timeout returns no usable read. It destroys the child best effort on failure, without claiming that detached descendant processes are always terminated. Shizuku's pinned UserService path is unchanged and no automatic fallback is allowed.

**Exact functional-head PASS:** [standard 38323574002](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38323574002), [CodeQL 38323565784](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38323565784), [targeted API37 38323584435](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38323584435), and [full API24 repeat 38324026404](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38324026404). The first API24 run [38323579229](https://github.com/HyperCriSiS/ReAppzuku/actions/runs/38323579229) encountered a preexisting changing pending-queue Policy Editor navigation test; an identical-head retry passed the complete emulator workflow with no source modifications. API37 live shell reports `CONSISTENT_DIAGNOSTIC_ONLY`, two operations and `RESTORE_ALLOWED=false` with three real executed tests.

**Evidence boundary:** JVM tests use fake Process objects; Android instrumentation uses real **UiAutomation**, NOT real privileged `su` or Shizuku. No live Root timeout/permission denial was tested, and this work is not a release gate for full privileged recovery, atomic identity/state capture, original ABSENT package-mode deletion, cross-process or flash power-loss persistence, physical hardware or production signing. Issue #130 OPEN, Phase 11 PARTIAL, v1/RZJ2 remain evidence only.
