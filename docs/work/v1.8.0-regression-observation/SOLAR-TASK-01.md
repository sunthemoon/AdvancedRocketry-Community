# Solar surface failure observation

Date: 2026-10-06. Task: V180-SOLAR-OBS-01. Status: planned.
Owner: delegated Solar author. Independent reviewer: a different agent.
Integrator: Root, sole committer and central/status writer.

## Outcome and boundaries

Identify which existing surface-publication assertion fails and preserve its
bounded scalar observation before the fixture restores the world. This is a
diagnostic change, not a correction to generation or acceptance of a failed run.
The [preceding result](../v1.8.0-ci/RESULT-07.md) remains FAILED.

Source preimage: `058cd67dacac4ac43ca6373d039fff1a2822e426`.
Isolated worktree: `D:/GitHub/arce-v180-solar-observation-20261006`.
Branch: `fix/v1.8.0-solar-failure-observation`.
Exclusive source scope:
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/SolarGeneratorGameTests.java`.
The author may also update its own new PROGRESS/HANDOFF files in this directory;
the task, central files, other fixture and sealed evidence are read-only.

Use fixed stage labels for the three existing awaits. Only an existing
publication assertion failure may trigger its observation. Read existing
day/game-time and darkness/weather scalars and one fixed-position SKY light
lookup; no world traversal, chunk request, ticket, producer tick, light-engine
drain or brightness update. Bound fields and application-line bytes/counts.
Diagnostic failure cannot replace the original exception or prevent restoration.

Preserve the original publication assertion text, predicates, business oracles,
40-tick timeout, retry condition, scheduling, setup and restoration. Do not
change production code, assets, registry, save formats, network or Gate states.
No unique causal conclusion is selected by the assignment.

## Verification

Compare the actual diff with the fixed preimage and invert the observation to
recover the exact original. Independently check field/count/byte bounds and
non-loading reads. A bounded cached-dependency Java 17 compile requires Root's
execution slot; it is not a clean/native pass. Root subsequently commits the
independently reviewed bytes and normally pushes an unfiltered hosted build,
unit, repeat-DataGen and GameTest cohort. Keep all original failures.

All scratch/helpers/logs use a new owned directory under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0`, including process temp variables. No local
full Gradle/native execution while C is below 10 GB. Do not delete shared caches,
old refused cleanup targets or another agent's files. Clean own completed
outputs and retire the clean worktree only after integration and reader release.

- [ ] Isolated actual source delta and bounded controls.
- [ ] Different-agent actual-diff review and applicable compilation.
- [ ] Root source commit/push and exact-source hosted result.

Native recovery, Solar delivery, ledger closure and v1.8 G0-G9 remain separate.
