# TASK W230-review: independent read-only review of the bay safe-exit design

Root: Claude. Date: 2026-10-31. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: do
not commit, change nothing outside your review worktree and output folder, and do not start other agents.

| Item | Value |
|---|---|
| Subject | `docs/work/v1.8.0-root-queue/records/W230-DESIGN.md` on branch `codex/v1.8.0-w230` at `8ff38121c` (design only, 510 lines), read against the source at `b6e13ac78`. Review worktree `D:/GitHub/arce-v180-w230-review`, a detached checkout of `8ff38121c` |
| Task it answers | [TASK-W230](TASK-W230-BAY-SAFE-EXIT-DESIGN.md), Root decision R223, owner decision D1b ("先设计安全退出位置与许可（简报推荐）") |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W230-review/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **Facts.** Every claim in section 2 about the current code is true at `b6e13ac78`: dismount handling, the seat
   that T keeps, the interlocks, the receipt and journal states, and the reconnect and passenger-transfer rules.
   Cite file and line.
2. **The constraints D1b sets.**
   - Dismount is never cancelled.
   - Playerdata authority does not change, and no new save root, schema or packet appears unless it is justified.
   - Only loaded chunks are used, nothing is force-loaded, and the per-tick work and memory are bounded.
3. **Position selection.**
   - The candidate order, the safety predicate and the budgets are concrete enough to implement.
   - A client cannot steer the choice.
   - A crowded or contested platform with two passengers resolves deterministically.
4. **The fallback (section 4).**
   - The one-descent fall safeguard cannot be used for anything beyond that one descent: it does not refresh, and
     it does not cover other damage, other players, other Levels or a later fall.
   - It clears on every listed event.
   - The residuals stated for Q1 are complete. Name any the design misses.
5. **Permit, identity and recovery.**
   - Logout, death, dimension change, restart and crash windows are each covered.
   - The table in section 6 is consistent with W27d's recovery rules and adds no derived authority.
6. **Risk, tests and slices.**
   - The R-042 proposal states scope, duration, log volume and recovery (AGENTS.md section 3.3).
   - The tests in section 9 would catch a wrong placement, a leaked safeguard, or a payload or seat change.
   - The slices respect the 500-line and 800-line thresholds.
7. **Q1 and Q2.** Say whether they are framed so that the owner and Root can decide them as written. Say whether
   any further decision is hidden in the text.

## Rerun

Reading only. You may run `git grep` and similar tools, and targeted unit tests that exist at `b6e13ac78` when they
help confirm a fact. Do not run Gradle builds of the whole project, GameTests, servers or clients.

## Output format

A verdict (APPROVE, APPROVE-WITH-CHANGES or BLOCK). Findings with an ID (W230-R1, ...), severity, section and line,
problem and suggested change. The commands you ran. The SHA-256 of REVIEW.md on the last line.
