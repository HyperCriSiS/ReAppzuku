# AI Session State

Updated: 2026-09-25

## Last completed work block

**UX: unify app management settings and surface app relaunches**

Merged PR: #57  
Merge commit: `04c33ed32ba7c01dcf27e5f567f78e571fa4ce72`

### Product changes

- ReAppzuku's own runtime/service behavior stays in the separate **App stability** card.
- Auto-Kill management and the former **Advanced tools** top-level card now live under one **App management** card.
- The former advanced section remains visible inside App management as **Restrictions & lifecycle**.
- Existing preference keys are unchanged.
- Existing Room schema 11 is unchanged.
- Existing `AppStats.relaunchCount` / `lastRelaunchTime` telemetry is now fed by ReAppzuku-owned kill paths.
- Relaunch detection deduplicates multi-process apps so one observation increments one relaunch per package.
- Statistics now contains a dedicated **Restarted after kill** list with count and latest observation.
- Relaunches are reported through the existing service notification when available, otherwise by a long Toast.
- Wording intentionally reports that an app was detected running again after being stopped; it does not claim ReAppzuku can prove what initiated the restart.

### Validation evidence

- Standard validation: `36117950978` — unit tests, lint, AndroidTest compilation, Room schema check and debug APK build/upload passed.
- CodeQL: `36117913887` — Java/Kotlin analysis passed.
- Android 17 / API 37: `36117955513` — build/install, instrumentation, external component abuse probe and launcher smoke passed.
- GitHub's separate agentic security reviewer `36117921559` failed before analysis with HTTP 400 because its requested model `claude-opus-5` was unsupported; this was not a product finding.

### Follow-up

- Evaluate the new Settings grouping on-device; if the single App management card feels too long, split it into visually distinct subsections without restoring the old top-level category boundary.
- Observe real relaunch detections on physical devices, especially OEM/system apps and the difference between `am force-stop` and `am kill`.
- If false-positive provenance becomes material, tighten batch Auto-Kill accounting so relaunch checks are tied to per-package confirmed shell success rather than the current existing batch-success semantics.
