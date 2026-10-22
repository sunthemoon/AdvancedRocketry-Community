# TASK W141: the warm-up refusal GameTests wait for cold pads without naming a leaked ticket

Root: Claude. Date: 2026-10-22. Read [WORKER-RULES](WORKER-RULES.md) and [records/W137.md](records/W137.md) first. You are
a new session. This is a parallel bug: take it in a free slot that does not delay progress tasks.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w141` (new; from the shared branch at the commit named in your prompt, which has W137 merged) |
| Branch | `codex/v1.8.0-w141` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W141.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W141/` |

W137 gave `EscrowGovernedWarmupGameTests` a precondition (`EscrowWarmupFixtures.requireReleasable`) that fails at once,
naming the ticket, when a ticket that never expires keeps a predicted pad warm, instead of waiting until the test's
timeout. `EscrowGovernedWarmupRefusalGameTests` waits for cold pads the same way, and both its tests timed out in hosted
run 39464852370 after an earlier failure. Apply the same precondition there, keeping the wait for a pad that is only
cooling down, so no new tick limit is added. If another GameTest class in the repository waits for cold pads the same
way, list it in the record (and apply the same change if it is the same mechanical update).

No timeout, tick limit or assertion changes; test code only.

## Verification

The changed class alone, then one complete GameTest with the unchanged log checker, under the heavy-JVM lock with the
atomic pattern and the hand-off rule in WORKER-RULES, releasing it and deleting `build/gametest/world` in the `finally`
of the same detached script that took it. Check C: and D: above 10 GiB and that 6 GiB of memory is free before each
Gradle run.

## Not in this task

Production code, W139's discard investigation, status files and the ledger (Root).
