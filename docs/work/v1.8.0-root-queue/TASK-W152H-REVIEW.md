# TASK W152H-review: independent read-only review of W152H and W152I

Root: Claude. Date: 2026-10-26. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w152h` at `983584934`: 12 commits on `c54de4b0e` (a docs-only commit after the W152 merge `6f00cbf5e`). Read `git diff c54de4b0e..983584934`. Review worktree `D:/GitHub/arce-v180-w152h-review`, a detached checkout of `983584934` |
| Tasks it answers | [TASK-W152H](TASK-W152H-HANDOFF-EXPIRY-AND-CROSS-GUARD.md) (R110) and [TASK-W152I](TASK-W152I-CROSS-GUARD-CONTROL-AND-BOUND.md) (R112, which restates the R4 bound) |
| Previous review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152-rereview-2/REVIEW.md` (W152-R4 to R6) |
| Design | ADR-065's N3-B2 section (with the R109 and R110 proof-admission bullet), R-035's N3-B2 part, [records/W80-DESIGN.md](records/W80-DESIGN.md) for the release fence |
| Record | the W152H and W152I sections of `docs/work/v1.8.0-root-queue/records/W152.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152H/` and `.../W152I/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W152H-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **The cross-guards.** Release planning and its boundary refuse a rocket that sealed staging claims or freezes,
   and sealed staging refuses a rocket with a release fence, each before any claim, carrier, fence, sidecar or journal
   write, using the existing refusal results. Both directions, at both entry points.
2. **W152-R5.** A request without a retained U checks its validity, cancellation and the unchanged 200-tick expiry
   every service tick, also while its proof start backs off, and ends at the existing boundary. A retained U is never
   released or emptied because its proof cannot start.
3. **W152-R4 under R112.** A waiting ordinary return gets its Level's first native capture opportunity at or after
   the active sealed attempt ends (plus the 2-tick service lag), and no sealed attempt reserves or captures before it;
   the reverse holds too. Look for a sequence of arrivals and callbacks that starves either class. R109's
   active-proof priority and the fairness among sealed candidates are unchanged. The turn state is RAM-only. No
   quota, deadline, retry, inspection allowance or pending bound changes.
4. **W152-R6 and wide plans.** The 21-slot comment and record wording; a new over-limit request is refused before
   any claim, carrier or journal write with `PROOF_PLAN_LIMIT` and one INFO; an existing over-limit U is retained with
   one WARN per record per load; the case is not reported as `BUSY`; manifest entries are exact.
5. **The W152I fixture.** The cross-guard GameTest installs its own release owner bound to the installed journal.
   Check that it cannot take over another release and always restores the previous owner. Check the diagnosis
   that production never replaces `RocketTransferSavedData` within a server session, so a release owner's
   constructor journal cannot go stale outside tests.
6. **Nothing weakened.** The 26 W152, W152F and W152G unit classes, the native quota, the inspection budget, the log
   checker and the existing manifest entries are unchanged, except for additions you can justify line by line. The
   proof-port constructor change in `b26ed6dc9` keeps the old behaviour for existing callers. New tests fail on the
   old code where the record says so.
7. **Native evidence.** W152H ran the eight W152 native cases at `b26ed6dc9`. Confirm that no production source
   changed between `b26ed6dc9` and `983584934`.
8. **Merge.** List conflicts with the shared head (`git -C D:/GitHub/AdvancedRocketry-Community rev-parse HEAD`) and
   any overlap with W160F (branch `codex/v1.8.0-w160f` at `2fad9b8f1`), which merges after this.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`:
- the five W152H classes and `EscrowBarrierNativeQuotaBoundTest` alone;
- the 26 W152, W152F and W152G classes, then the `resourceescrow`, `rocket.server` and `rocket.sealed` packages;
- `tests/test_check_gametest_log.py`.

**Take the heavy-JVM lock for every Gradle run**, with the atomic pattern and the 90-second hand-off; the W160F
review shares it. No GameTest or server of your own. Check 6 GiB free memory first. Delete the worktree's `build`,
`.gradle` and `logs` afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W152H-R1, ...), severity, file and line,
problem and suggested change; the commands you ran and their results; the SHA-256 of REVIEW.md on the last line.
