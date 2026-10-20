# TASK W116-review: independent read-only review of W116 (P1-N3 leaf N3-A2) before merge

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents. A Claude
session wrote W116, so this is a fresh Codex session with no context from the author (AGENTS.md section 13).

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w116` at `efd60c80` (base `31e796a1`; tested source `82905b4b`). Your review worktree `D:/GitHub/arce-v180-w116-review` is a detached checkout of `efd60c80`; read the diff with `git diff 31e796a1..efd60c80` there |
| Task it answers | [TASK-W116](TASK-W116-N3-A2-RETIREMENT-FOLLOW-UPS.md): W98-R3, R6, R10 and R12 |
| Design and findings | [records/W96-DESIGN.md](records/W96-DESIGN.md) (with Root decision R34), [records/W98.md](records/W98.md), and the W98 review `D:/GitHub/ARCE-Task-Evidence/root-queue/W98-review/REVIEW.md` (findings W98-R1, R3, R6, R10, R12) |
| Record | `docs/work/v1.8.0-root-queue/records/W116.md` on the branch |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W116/` |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W116-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

**Root decision R42 (2026-10-20), for context.** W116 withdrew W98-R6 (`82905b4b`) because its pad rule made two
existing recovery tests fail: a source that recovery already returned to its pad, or a stale COUNTDOWN image, looks
like one that never left. Root keeps today's behaviour (recovery waits for an offline crew) and registers W98-R6 as a
named R-035 residual; a durable cancellation intent is reconsidered with N3-B. Do not review R6's withdrawn rule except
to confirm the withdrawal is complete.

## What to check

1. **W98-R3** (`832a2c08`). Each attempt refreshes the existing ordinary flight ticket on every census column, one per
   discarded end, and adds no governed or margin ticket. Can a ticket outlive its return, leak on a failed or abandoned
   attempt, or load a column the census did not name? Does the new GameTest fail without the fix for the stated reason?
2. **The barrier's mixed batches** (`0ec1b6d2`, `EscrowBarrier`). An ordinary store or force failure no longer fails the
   governed attempts or counts in the governed `ioFailures`. Is that safe: can a failure that also affects a governed
   slot (the same IO worker, the same region file, the same force) now let a governed proof confirm? Do all-governed
   and all-ordinary batches run exactly the old code? Are `stageForTests`, `attempts(L)` and `reason(L)` reachable only
   from tests?
3. **The R10 refactor** (`a853a23e`, `0ac54f4d`, `52ce6a65`, `4af046e3`). Behaviour must not change: every check keeps
   its order, reasons and logs through the new `Port`; the token-aware overload in `RocketTransferEscrowRules` is the
   one rule both callers use; the release-test pause is gated off in production. `RocketTransferService` is unchanged;
   flag any class over 500 lines.
4. **W98-R12 coverage** (`a4e11910`, `71f3192f`, `4ffa8c56`). Do the unit tests drive the real coordinator and journal
   through the fake port, and do the GameTests drive the real paths they name? `4ffa8c56` changed W116's own new
   GameTests after a targeted failure: confirm it changed no production code and relaxed nothing.
5. **R6's withdrawal.** `82905b4b` must remove the rule and its tests completely; the two existing
   `RocketResourceRecoveryGameTests` are unchanged and pass.
6. **Tests, limits and the manifest.** No existing test, assertion, limit or timeout was removed or relaxed (AGENTS.md
   section 5); list every edited pre-existing test and fixture file. The five new `gametest_expected_log.json` entries
   must be exact and batch-scoped; no existing expectation may change. No saved format, schema, hash, bound, proof rule,
   lease, budget or timeout changed.
7. **Merge with the shared branch.** W116 will be merged into the shared head `2e44edcc` or later, which since its base
   added W111 (`ead8d323`, escrow GameTest fixture areas), W114 (`4153204e`) and W115 (`f8ddfc25`). Preview with
   `git merge-tree` and say what the merge must keep from each side.
8. **The record** against the code, including section 7.3's observation of W98-R1.

## Rerun

In your review worktree only, with JDK 17.0.7 (`C:/Program Files/Java/jdk-17.0.7`) and `gradlew.bat --offline`:
`RocketOrdinaryRetirementTest`, `EscrowMixedBatchTest`, the existing `EscrowBarrier*` and `RocketTransfer*` unit tests,
then the `resourceescrow` and `rocket.server` packages. You may add a probe test to check a finding; keep each probe in
the output folder. Before each Gradle run, check that at least 6 GiB of memory is free and wait if not; run one Gradle
process at a time, and no GameTest, `runData` or server; take no heavy-JVM lock. Delete the worktree's `build` and
`.gradle` folders afterwards.

## Output format

- A verdict: APPROVE, APPROVE-WITH-CHANGES, or BLOCK.
- Findings, each with an ID (W116-R1, ...), a severity (Critical, High, Medium, Low), the file and line, the problem,
  and a suggested change.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.
