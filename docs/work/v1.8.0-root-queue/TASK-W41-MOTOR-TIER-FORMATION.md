# TASK W41: C16a-07b, formation with every motor tier

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w41-motor-tiers` (from the shared branch) |
| Branch | `codex/v1.8.0-w41-motor-tiers` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W41.md` (new) |
| Specification | ADR-064 section 2 (legacy layouts: "a structure accepted any motor tier") and section 4.1 (the four motors and the `advancedrocketrycommunity:motors` tag) |

COMPLETION-PLAN item C16a-07b is open: "全部电机等级的机器形成验证" (formation verified with every
motor tier).

## What to do

1. List every v1.8 multiblock whose pattern has a motor cell. Cite the pattern catalog or code for
   each.
2. Add native GameTests that build each of those machines four times, once with each of `motor`,
   `advanced_motor`, `enhanced_motor` and `elite_motor` in every motor cell. Each must form through
   the production formation path, the same one a player's wrench or controller use reaches. In
   each case, check the formed state the existing formation tests check.
3. For each machine, add one negative case: a non-motor block in the motor cell does not form.
4. If a tier does not form where ADR-064 says it should, stop and report it in the record before
   changing production code.

Use the existing multiblock GameTest fixtures and builders. Do not copy whole fixtures. Under the
W28 rule, no GameTest callback may schedule another. Keep the batch small enough that it does not
add more than a few seconds to the suite. Say in the record how long it took.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- Any change to patterns, tags, recipes or motor behaviour.
- Status files, the ledger and COMPLETION-PLAN (Root).
