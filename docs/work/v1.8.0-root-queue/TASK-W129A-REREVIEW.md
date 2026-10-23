# TASK W129a-rereview: focused read-only re-review of W129aF (W129a-R1 to R3)

Root: Claude. Date: 2026-10-23. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, and change nothing outside your own review worktree and output folder. Do not start other agents.

| Item | Value |
|---|---|
| Subject | Branch `codex/v1.8.0-w129a` at `f8d2b42c` (W129aF on top of W129a's `66b27c04`, with the shared head `2426f00d` merged in `97f4f6e2`). Review worktree `D:/GitHub/arce-v180-w129a-rereview`, a detached checkout of `f8d2b42c`; read `git diff 97f4f6e2..f8d2b42c` |
| The first review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129a-review/REVIEW.md` (BLOCK on W129a-R1 to R3; SHA-256 `d2da5ec4…dbec70`) |
| Task it answers | [TASK-W129aF](TASK-W129AF-REVIEW-FOLLOW-UPS.md), with Root decisions R61 and R63 (QUEUE) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129aF/` (`SHA256SUMS.txt`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W129a-rereview/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **W129a-R1:** does B3a now hold the escrow ledger and bay receipt roots at representative, stated sizes with their
   existing writers' paced traffic (mutation, write, publication and readback) beside the bound-sized transaction and
   transfer journals, for at least 50 rounds on the monotonic surrogate, and is the composition documented? Is R63's
   reading right that no stated budget missed (tick work P99 14.65 ms, max 94 ms; peak RSS 4.41 GiB), with the pause
   tail and the escrow writer's own server-thread capture recorded as residuals?
2. **W129a-R2:** does every missing, failed, timed-out or unavailable measurement, and a missing or NaN tick figure,
   give `STOP_RULE` with its reason, in the main measurement and in the comparison? Do the verdict tests cover each?
3. **W129a-R3:** is the hash correction traceable without changing the original evidence?
4. **Nothing else changed:** no production code beyond W129a's reviewed diff, no weakened assertion, budget or timeout;
   the merge of the shared head kept both sides.
5. **Item 5:** W129aF could not run the v1.0 transaction harness (W140 owns that); confirm the reason recorded.

## Rerun

In your review worktree only, with JDK 17.0.7 and `gradlew.bat --offline`: `RocketWriterOverlapVerdictTest` and
`EscrowWriterBenchmarkAccessTest` alone, then the `persistence` and `rocket.persistence` packages. No benchmark,
GameTest or server; no heavy-JVM lock. Check that 6 GiB of memory is free first. Delete the worktree's `build`,
`.gradle` and `logs` folders afterwards.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK); findings with an ID (W129a-RR1, ...), severity, file and line,
problem and suggested change; the commands you ran with their results; the SHA-256 of REVIEW.md on the last line.
