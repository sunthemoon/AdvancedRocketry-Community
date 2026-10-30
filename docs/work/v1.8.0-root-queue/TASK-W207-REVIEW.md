# TASK W207-review: independent read-only review of the W90 request cost remedies

Root: Claude. Date: 2026-10-30. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w207` at `e2dbbb74c`, cut from W206's `24a076f9c` (merged on the shared head at `c01c37336`); the slice is `24a076f9c..e2dbbb74c` (13 commits, 27 files). Production: `ab83aa651`, `93a0b69b8`, `51fee6e75`. Review worktree `D:/GitHub/arce-v180-w207-review`, a detached checkout of `e2dbbb74c` |
| Task it answers | [TASK-W207](TASK-W207-W90-REQUEST-COST.md), Root decision R177 |
| Record | `docs/work/v1.8.0-root-queue/records/W207.md` on the branch (the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W207/` |
| Hosted run | the run on `e2dbbb74c` (Root pushed the branch at 08:14); read its result if it has finished |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W207-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

W207 reports cold request ticks down from 458 to 872 ms to 52 to 70 ms, and the refused ninth allocation from 210 ms to
1.7 ms; the checkpoint-bearing P99 stays at 119 to 164 ms, over docs/17.

## What to check

1. **Asynchronous terrain preparation (`51fee6e75`, `RocketPadTerrainPreparation`, `RocketTransferService`,
   `RocketLaunchPadSelection`, `RocketLandingPadSelector`).** A governed request now returns `DESTINATION_LOADING`
   while terrain is prepared within the warm-up quotas. Check that: no check is skipped or reordered unsafely (station,
   named pad, reservations, kept pad, scheduled arrival); no chunk is loaded or ticketed beyond today's; the 600 and
   400-tick lifetimes and every cap are unchanged; a waiting request gains no reservation or pad claim early, cannot
   be served twice, and is revalidated when it resumes; disconnect, replacement, expiry and restart leave nothing
   behind; non-governed requests behave exactly as before. What does a player see?
2. **The cheap refusal (`ab83aa651`).** Refusing before terrain loads at capacity keeps the behaviour for an
   already-ready destination, the per-player and global caps and their feedback codes.
3. **The F1 projection reuse (`93a0b69b8`, `EscrowGovernedLaunch.record`).** The cached record is returned only for an
   ARMED value equal in every field to the one it was built from; `RocketTransferRecord` and its envelope are truly
   immutable; no caller mutates it or treats it as acknowledgement; why `synchronized` is needed, which threads call
   it, and whether it can block the server thread. The caller still proves the arm belongs to an acknowledged root.
4. **The changed GameTests** (`EscrowGovernedMarginGameTests`, `EscrowGovernedWarmupGameTests`,
   `EscrowGovernedWarmupRefusalGameTests`) and test support: no assertion weakened, no timeout or bound enlarged;
   each change is required by the new waiting behaviour and still checks the old outcome.
5. **The measurement.** The observer calibration (`ef6ee93b8`), the before and after runs (same harness and entry
   points), the fresh request after a process restart (W206-R1), and the remaining checkpoint-bearing P99: where it
   goes, from the retained JFR.
6. **Nothing weakened:** formats, bounds, budgets, quotas, caps, lifetimes, timeouts and assertions.
7. **Risk text:** which sentences of R-035 or the W90 design these changes make wrong, for Root.
8. **Merge conflicts** against the shared head (`origin/codex/v1.8.0-classic-content`) and W208 (`codex/v1.8.0-w208`).

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: the new and changed unit classes and the
`rocket.transfer`, `rocket.server` and `resourceescrow` packages; the W206 and W207 Python tests. Targeted unit runs
need no heavy-JVM lock. Check 6 GiB free memory first. No GameTest, native case, runData, full build or server. Delete
the worktree's `build`, `.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W207-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
