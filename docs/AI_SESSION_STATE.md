# AI Session State

Updated: 2026-09-26

## Last completed work block

**Phase 9 foundation — Unified per-app lifecycle policies**

Merged PR: #58  
Merge commit: `00316ab0e85faf394040d76e30384d0faa05dcd6`

### Product / architecture state

- `docs/ROADMAP.md` now contains **Phase 9 — Unified per-app lifecycle policies**.
- Canonical per-app execution strategies are:
  - `UNMANAGED`
  - `PROTECTED`
  - `SMART`
  - `IMMEDIATE`
- **Custom is not a fifth execution engine.** It is a presentation state when an app policy differs from its selected preset.
- Room schema is now **12**.
- Added `app_policy` storage for one canonical policy per package.
- Added `policy_preset` storage for reusable built-in/user policy templates.
- Added `AppPolicyResolver` as the central ownership rule:
  - explicit Room policy wins;
  - during legacy fallback, Smart Lifecycle owns a blacklisted package before Immediate Auto-Kill so one app is not controlled by both engines simultaneously;
  - whitelist mode maps listed apps to Protected and unlisted targets to Immediate;
  - blacklist mode maps listed targets to Immediate.
- Added resolver JVM regression tests.
- Added and validated Room migration `11 -> 12`; the existing migration chain now reaches schema 12.
- The generated schema 12 is committed.
- Existing runtime engines **do not read the new policy tables yet**. Existing installations therefore keep their current runtime behavior in this foundation block.

### Validation evidence

- Standard validation: `36268228765` — unit tests, lint, AndroidTest compilation, Room schema export verification and debug APK build/upload passed.
- CodeQL: `36268254104` — Java/Kotlin analysis passed.
- Android 17 / API 37: `36268231636` — build/install, full instrumentation including migration coverage, external component abuse probe and launcher smoke passed.
- GitHub's separate agentic security reviewer failed before code analysis because its requested `claude-opus-5` model was unsupported; this was not a ReAppzuku finding.

## Next work unit

**Legacy policy migration + execution ownership preparation**

1. Build an idempotent one-time migration from current whitelist/blacklist + Smart Lifecycle + Sleep Mode + background-restriction state into explicit `AppPolicy` rows while preserving effective user intent.
2. Define/seed the built-in Policy Presets needed by the new model (Never touch, Messenger, Media, Balanced, Rarely used, Aggressive).
3. Add migration regression coverage for conflicting legacy configurations and repeated execution.
4. Only after migration behavior is proven, route Auto-Kill and Smart Lifecycle execution through `AppPolicyResolver` and retire direct shared-blacklist ownership.
5. Later units: per-app Policy Editor, reusable user presets, Automation Schedule rename/separation, `PACKAGE_ADDED` setup queue + notification/deep link, badges/filters, backup/restore, then removal of obsolete legacy UI.

### Guardrails

- Never allow Smart and Immediate automation to own the same package simultaneously.
- Newly installed apps default to no privileged mutation unless the user explicitly selects an automatic default preset.
- Protected/system/persistent packages must continue to fail safe regardless of stored policy.
- Keep legacy behavior available until migration/execution parity is validated; do not remove old UI early.
