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
