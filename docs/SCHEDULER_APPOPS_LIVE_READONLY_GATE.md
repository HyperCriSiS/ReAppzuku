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
