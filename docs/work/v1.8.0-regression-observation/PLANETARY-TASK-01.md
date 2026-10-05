# Planetary route failure observation

Date: 2026-10-06. Task: V180-PLANETARY-OBS-01. Status: implemented-unverified.
Owner: delegated planetary investigator. Independent reviewer: a different agent.
Integrator: Root, sole committer and central/status writer.

Published source and pending hosted execution are recorded in the
[source checkpoint](SOURCE-INTEGRATION-01.md); no original failure is closed.

## Outcome and boundaries

Retain a bounded per-leg state observation before an existing planetary route
assertion fails and the fixture cleans its journal/entities. This changes only
diagnostics, not production transfer or route acceptance. The
[preceding result](../v1.8.0-ci/RESULT-07.md) remains FAILED.

Source preimage: `058cd67dacac4ac43ca6373d039fff1a2822e426`.
Isolated worktree: `D:/GitHub/arce-v180-planetary-observation-20261006`.
Branch: `fix/v1.8.0-planetary-failure-observation`.
Exclusive source scope:
`src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/PlanetaryWorldGameTests.java`.
The author may also create its own PLANETARY-PROGRESS/HANDOFF files in this
directory; task, central files, other fixture and sealed evidence are read-only.

Observe only when an existing per-leg assertion fails, using already collected
matches and initialized immutable journal lookups. Include bounded identity,
phase/state/clocks and exact-origin loaded/entity/ticking results when available.
Do not invoke transfer inspection paths that load origin chunks. No new world
scan, chunk request, ticket, prewarm, manager tick or mutation. Bound observation
lines and bytes; no arbitrary NBT or exception text. Observer failure cannot
replace the original exception or interfere with existing cleanup.

Preserve the Mars/Venus/Earth route, 270-tick checks, 1000-tick timeout, original
assertions for logical ownership, landing/body, typed target, fuel and blocks,
and original cleanup. No production, registry, asset, save, network or Gate
change. No unique causal conclusion is selected by the assignment.

## Verification

Independently review the actual diff and invert diagnostics to recover the exact
fixed preimage. Check bounded fields/counts, immutable/non-loading accessors,
failure identity and cleanup. A bounded Java 17 cached-dependency compile needs
Root's execution slot and cannot prove clean/native correctness. Root commits
reviewed source and normally pushes the unchanged unfiltered hosted regression;
its actual run/source identity and failures are retained separately.

All scratch/helpers/logs use a new owned directory under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0`, including process temp variables. No local
full Gradle/native execution while C is below 10 GB. Do not delete shared caches,
old refused cleanup targets or another agent's files. Own outputs/worktree are
cleaned only after integration and reader release.

- [ ] Isolated actual source delta and bounded controls.
- [ ] Different-agent actual-diff review and applicable compilation.
- [ ] Root source commit/push and exact-source hosted result.

Cold-route reliability, recovery, ledger delivery and all v1.8 Gates stay open.
