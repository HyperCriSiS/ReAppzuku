# ReAppzuku Red-Team Review — 2026-08-31

This is the second-pass adversarial review required by the Voice-platform matrix.

## Lens 1 — Scope pressure

ReAppzuku combines process inspection, privileged actions, background automation, presets,
schedulers, accessibility, statistics, backup and update behavior. The main risk is not feature
count itself but multiple controllers writing overlapping state.

**Finding:** central desired-state policy must expand beyond App Behavior and become the single
authority for service/work/alarm continuity.

## Lens 2 — Platform-policy pressure

**Findings:**
- both exact-alarm permissions are declared;
- exact alarm capability is not centrally checked;
- API 37 compatibility is not yet a lane;
- FGS `specialUse` remains store-policy sensitive and must stay justified.

## Lens 3 — Concurrency / isolation

**Findings:**
- Shizuku binder, permission and UserService readiness are asynchronous independent states;
- large managers use callbacks/executors without deterministic state-machine tests;
- CI-generated patch commits create another source of ordering complexity.

## Lens 4 — Hot/update pressure

**Finding:** current update checker targets upstream, so the fork can effectively update itself
back into a different product.

## Lens 5 — Resource contention

**Findings:**
- multiple monitoring/worker/service mechanisms can overlap;
- large shell/dumpsys scans should have explicit cadence/budget;
- Android 17 introduces broader memory-limit pressure that justifies a compatibility/perf probe.

## Lens 6 — Process death / reboot

**Findings:**
- service restart logic can defeat intentional disable;
- preset alarms are not rebuilt at boot;
- Shizuku service readiness after death/restart needs evidence;
- LOCKED_BOOT_COMPLETED behavior is not clearly aligned with credential-protected preferences.

## Lens 7 — Security / principal

**Findings:**
- exported `KillShortcutActivity` reaches privileged kill behavior;
- shell backend accepts arbitrary command strings from trusted app code;
- imported backup is untrusted input and should never influence raw shell construction without
  validation.

## Lens 8 — Storage semantics

**Findings:**
- backup restore can partially commit;
- backup version is not enforced;
- fork-owned durable settings are missing;
- Room destructive fallback violates preservation expectations;
- historical schemas are incomplete.

## Lens 9 — Truthfulness / UX

**Findings:**
- previous “Failed to get running Apps output” was actually a readiness-state problem;
- disabled App Behavior options should always explain which active automation blocks them;
- exact-alarm denial needs actionable rather than generic failure.

## Lens 10 — Evolution pressure

**Findings:**
- very large classes make behavior hard to isolate;
- patch-script-based CI means implementation lives partly in source and partly in generators;
- no regression suite protects refactors or Android-version changes.

## Red-team conclusion

The architecture is viable and the new Smart Lifecycle / on-demand process design are good
directions. The dominant next step should not be more features. It should be to convert hidden
state assumptions into explicit desired-state machines and repeatable evidence.

Priority order:
1. intentional-stop/service-restart invariant;
2. update provenance;
3. Shizuku readiness tests;
4. reboot/preset/exact-alarm correctness;
5. transactional backup + non-destructive migrations;
6. CI/testability/supply-chain hardening;
7. exported surfaces / accessibility / API 37 / i18n.
