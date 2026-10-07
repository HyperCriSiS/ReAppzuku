# Physical device and stable-release validation

**Status: NOT RUN.** This is a human/device acceptance plan, not evidence that any physical device has passed it.

Use this with [CHECK_MATRIX.md](CHECK_MATRIX.md), [QUALITY_GATES.md](QUALITY_GATES.md) and [RELEASE_SIGNING.md](RELEASE_SIGNING.md). Android 17/API 37 emulator runs `37149945404` and `37553682771` already passed; repeatable **real-device** behavior is the missing evidence.

## Scope and safety

- Test at least one **non-Pixel/OEM physical Android device**, documenting model family, Android API, OEM build/skin, ReAppzuku source commit, APK identity and backend version. A second manufacturer improves diversity but does not replace exact behavior checks.
- Make a configuration backup first. Use a **disposable third-party test package** for install, restriction, freeze and lifecycle operations; never change launcher, keyboard, System UI, device-admin or security apps.
- Use APKs with a **known signing identity**. Random runner debug certificates are not stable release keys; never uninstall a valuable existing installation merely to make another debug-signed APK install.
- Keep full logs/screenshots local. Redact serials, account details, installed package lists, device fingerprints and personal activity before attaching evidence.
- **Do not use** `adb shell am force-stop com.gree1d.reappzuku` to simulate normal background process eviction. Force-stop places the app into the stopped state and suppresses jobs until explicit launch; that cannot prove periodic WorkManager delivery.

## 1. Baseline and provenance

Run with the Android SDK platform-tools and an explicitly authorized physical device:

```sh
adb devices -l
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell pm path com.gree1d.reappzuku
```

On the host, record the tested APK SHA-256 (`sha256sum app.apk` or PowerShell `Get-FileHash app.apk -Algorithm SHA256`). Check its certificate with `apksigner verify --print-certs app.apk`; compare with the expected channel fingerprint. Store only a sanitized device summary.

Note **fresh fork install**, **upgrade from a previous fork**, and **upgrade from historical upstream** as different test cases. Do not mix their evidence.

## 2. Canonical Phase 9 scenarios

| ID | Real-device action | Acceptance criterion |
|---|---|---|
| P9-01 | Configure **Ask after install**; install a disposable app with ReAppzuku running | Exactly one Needs-setup entry; no privileged automatic mutation before explicit choice |
| P9-02 | Grant notification permission; install another disposable app; tap setup notification | Opens that package's Policy Editor; a successful save clears its pending entry/notification |
| P9-03 | Deny notification permission and install a disposable app | Pending setup remains reachable through New app setup / Review next; no false success |
| P9-04 | With auto-management opt-in disabled, reinstall the disposable package after legacy-owned state | It remains canonical UNMANAGED pending user choice; stale legacy state cannot silently claim it |
| P9-05 | Restore a safe test configuration with conflicting legacy Smart/Immediate ownership | Exactly one effective canonical owner; an explicit Room policy overrides migrated legacy ownership |
| P9-06 | Change a disposable package SMART -> IMMEDIATE -> UNMANAGED | Smart periodic scheduling reconciles after edits; no simultaneous Smart/Immediate execution or duplicates |
| P9-07 | Install a new package while the UI is closed; allow normal OS eviction, not force-stop; later reopen ReAppzuku | Inventory reconciliation creates at most one setup decision; 15-minute periodic work is **not** an exact notification deadline |
| P9-08 | Backup v7 -> edit disposable policies/preset/setup choices -> restore | Explicit policies, user presets, new-app setup state and pending queue round-trip; no dual ownership |
| P9-09 | Reboot/unlock with a disposable policy and backend available late | Persisted desired behavior recovers; no action reports success before ROOT_READY/SHIZUKU_READY |
| P9-10 | Exercise app-list badges/filtering, editor, notification deep link, accessibility controls and launcher | No wrong-package routing, crashes, inaccessible controls or silently applied privileges |

Record **PASS**, **FAIL**, **NOT RUN** or **N/A** separately for every case, with concise observed behavior and reproduction steps. Restore disposable package state after each mutating test. A screenshot supports—but does not replace—observed behavior.

## 3. Separate root and OEM tracks

Shizuku runtime success does not prove KernelSU/Magisk. Exercise root through a real root backend only when knowingly opted in, using reversible typed mutations on a disposable package. Observe both the effect and rollback, and test backend unavailable/late-ready/death recovery separately.

Record independent OEM observations for reboot/background restrictions, Accessibility binding, Doze/exact-alarm fallback, launcher/widget host, backup document-provider UI and ActivityManager process text parsing. Treat any newly observed OEM parser variant as a minimized fixture candidate, not permission to broaden the parser heuristically without evidence.

## 4. Signing, updates and rollback are a different gate

1. Generate and back up the release keystore offline. Configure the five secrets described in [RELEASE_SIGNING.md](RELEASE_SIGNING.md); **never** commit credentials.
2. Dispatch the signed-release workflow with **publish=false** and verify actual APK certificate SHA-256 and artifact SHA-256.
3. Test two sequential APK versions signed with the **same stable key**, confirming in-place update preserves configuration and canonical policies.
4. Explicitly test the selected rollback plan. Android may reject APK downgrades, and uninstall/reinstall deletes app data. A tested forward-fix or validated backup/restore path is required; do not assume reversible updates.
5. Publish a deliberately tagged stable release only after all preceding acceptance evidence has been recorded.

## 5. Evidence template

Save one sanitized record per device/build pair:

```text
Date / tester:
Device family / OEM / Android API / OEM skin:
Build commit / versionName / APK SHA-256:
Signing certificate SHA-256 (public fingerprint only):
Backend and version (Shizuku / Magisk / KernelSU):
P9-01: NOT RUN
P9-02: NOT RUN
P9-03: NOT RUN
P9-04: NOT RUN
P9-05: NOT RUN
P9-06: NOT RUN
P9-07: NOT RUN
P9-08: NOT RUN
P9-09: NOT RUN
P9-10: NOT RUN
Root-specific reversible mutation/recovery: NOT RUN
OEM Accessibility/Doze/launcher/provider/parser checks: NOT RUN
Same-key installed update/rollback: NOT RUN
Observed failures / reproduction:
Sanitized logs / artifact checksums:
```

Only change the applicable matrix cells to **PROVEN** when the tests actually ran and their outcomes satisfy the invariant. Writing this plan does **not** close physical/OEM, root or release-signing evidence.
