# TASK W119-review: independent read-only review of W119 (W27c2, the satellite registry proof) before merge

Root: Claude. Date: 2026-10-21. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. Codex
wrote W119 and both Claude slots are busy, so this is a fresh Codex session with no context from the author (as for
W109-review and W114-review).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w119` at `e92c602e` (base `dcebe3a8`). Your review worktree `D:/GitHub/arce-v180-w119-review` is a detached checkout of `e92c602e`; read the diff with `git diff dcebe3a8..e92c602e` there |
| Task it answers | [TASK-W119](TASK-W119-W27C2-REGISTRY-PROOF.md) |
| Contract | [records/W27A-DESIGN.md](records/W27A-DESIGN.md), frozen: section 3 ("The registry proof"), section 7 (the orbit checkpoint and the production launch service, N8), the W27c2 row of section 9 with its F2 and F4 matrices and the registry transport matrix (N2 cases), and section 10's ADR-050 and ADR-049 implementation notes |
| Builds on | [records/W27C1.md](records/W27C1.md) (the receipt store and index) and R-039 in `docs/11-RISK-REGISTER.md` |
| Record | `docs/work/v1.8.0-root-queue/records/W119.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W119/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W119-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The epoch rule** (section 3 and the ADR-050 note): E advances once, to the carried epoch, only when a capture
   commits within its own call with no mutation since capture; a definite failure or an UNKNOWN outcome keeps E and the
   dirty and flush-pending state; a later capture carries an epoch above every candidate whose outcome is unknown, so no
   carried value is reused; frozen bytes are never rewritten; no delayed readback advances E; no snapshot epoch; no
   mutation is refused while an outcome is unknown. A committer throw is resolved by a bounded readback in the same
   call, compared by `equals`. Try to construct a sequence (throw, retry, concurrent mutation, restart) that reuses an
   epoch or advances E without a durable file containing the stamps.
2. **The saved format**: what section 3 adds to `SatelliteMissionSavedData`, its schema and bounds, and what an older
   jar does with a file that contains it (refused as invalid or future data with the bytes kept, as the existing
   whole-world refusal requires, or silently accepted?). Do CELESTIAL and ENDGAME, which share `AtomicSavedData`, behave
   exactly as before?
3. **The fences**: launch and decommission refuse with `DEPLOYMENT_PENDING` while a bay deployment of that satellite is
   unresolved and before the receipt index is loaded, failing closed; nothing else is refused.
4. **N8 and the shared launch service**: the replay tuple (satellite UUID, owner, definition, kind; never the current or
   retained initial mission) is checked before catalogs, targets, stats, research and quotas for every kind; a data
   package whose definition or target was removed now returns IDEMPOTENT and is extracted; a fresh data launch still
   creates its initial mission atomically. Terminal parity: do the terminal and the service give the same result?
5. **The bay checkpoint** forces the directory where supported and degrades as the design says elsewhere.
6. **Tests and limits** (AGENTS.md section 5): list every edited pre-existing test or fixture (the record names one
   adapted fixture) and say whether any assertion is weaker or only changed as N8 requires, with the design line; no
   limit, budget or timeout relaxed; classes over 500 lines.
7. **The full GameTest failure**: `PlanetaryAdmissionGameTests.closedOrbitStationCanDepartButStillRequiresLiveMembership`
   ("Authorized closed-source departure did not enter countdown") failed in W119's run, as it did in W98's, W118's and
   W117's local runs (QUEUE lists it). Can anything in W119's diff (the registry composition, station or celestial
   saved data, the launch service) reach a station departure's countdown? Say what you find; do not run a GameTest.
8. **Merge with the shared head** (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`, which since W119's base
   added W118 and Root's records): preview with `git merge-tree` and say what the merge must keep.
9. **The record** against the code, including its interrupted and resumed runs.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`: W119's
new unit classes, then the satellite, station and celestial persistence tests (find them by package), then the whole
unit suite once. You may add a probe test to check a finding; keep each probe in the output folder. Before each Gradle
run, check that at least 6 GiB of memory is free and wait if not; run one Gradle process at a time, and no GameTest,
`runData` or server; take no heavy-JVM lock. Delete the worktree's `build` and `.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W119-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
