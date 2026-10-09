# C18a-SEAL-SPATIAL-04: native selected-cell guard qualification

Date: 2026-10-09. Owner/author/integrator: Root. Status: in-progress.
Baseline: 69853a4efb3f3cf7ce7e3ade1ab536add68a28f2 on
`codex/v1.8.0-classic-content`. This continues the accepted detector
[task](TASK.md) and [ADR-067](../../decisions/ADR-067-V180-READ-ONLY-SEAL-DETECTOR.md),
not the unassigned off-world sleep implementation.

## Observable scope

Add finite native GameTest coverage of the existing local service's selected-cell
guards: foreign actual Level refusal before selected getters; a near hit paired
with a far unloaded target; and an out-of-build-height target that must not use
the loaded-cell adapter's OPEN fallback. Keep the admitted native item control
and existing actor/channel/cell/worker ownership and cleanup checks.

Use actual connected embedded ServerPlayers and actual ServerLevels. These are
direct Java local-reader fixtures, not TCP clients, installed lifecycle/restart
or claim/protection-mod proof. Distinguish constructor/setup loads, moves and
reads from each synchronous measurement. Do not infer absence of every world
access from unchanged manager metrics. No mocked Level or altered production seam.

## Writes and boundaries

Root creates a new isolated worktree `D:/GitHub/arce-v180-seal-spatial-boundary-20261009`
on `test/v1.8.0-seal-spatial-boundary` after this preparation is committed.
Only existing `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/
atmosphere/instrument/SealDetectorAdmissionGameTests.java` may be edited there.
Existing test assertions/timeouts, production behavior, registries, build, assets,
API, persistence, catalog/air truth, owner policies and sealed evidence stay
unchanged. No upstream code/assets are imported; the added tests are original.

Root alone owns this task, a new `SPATIAL-VERIFICATION-04.md`, the version work log
and current plan/status integration records on Main. User AGENTS and inherited
untracked work are excluded. Independent review is read-only, uses a fresh fixed
source checkout and its own Temp/evidence, and reruns relevant checks. HEAD moves
must not interrupt another agent's mutable input. No Claude is used.

## Verification and evidence

Source must be committed before authoritative command results. Check exact diff,
file size/responsibility and whitespace. Run fixed-source clean build, explicit
test, runData twice with generated diff checks, and unfiltered runGameTestServer;
collect actual JUnit XML/new-method execution/native terminal results and retain
project ERROR/FATAL counts separately. Run content/provenance and strict repository
checks without deleting failures or weakening assertions, bounds or deadlines.
Before each sustained command inspect disk; do not start below10 GiB. New command
TEMP/TMP/java.io.tmpdir live only under the owned D: evidence leaf.

Root evidence: `D:/GitHub/ARCE-Task-Evidence/v1.8.0/seal-spatial-boundary-root-20261009-04`.
Retain argv/cwd/UTC/exit/source/input hashes and raw logs/results within100 MiB;
each retained file is under50 MiB. No source/build/world archive. Only newly owned,
ended disposable outputs may be cleaned after containment/ownership/process checks.
Do not retry any inherited policy-denied cleanup.

Not completed by this slice: loaded/unloaded adjacent-chunk, exact all-query count,
installed precedence/lifecycle, unlock, reload/unload, packaged/restart, V1/V2,
survival progression or full detector delivery. All v1.8 Required Gates remain
open; the acceptance cursor remains v1.0.0. Report source/records SHAs, actual
commands and findings, failures/limitations, cleanup, evidence and remaining work.
