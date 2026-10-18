# TASK W83b: an arrival waits at PREPARED until its footprint's entity sections are loaded (W13-N1; Root decision R32)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W83](TASK-W83-ARRIVAL-ENTITY-READINESS.md)
(the problem, items 2 to 4 and its tests still apply) and the W83 record at commit `a072e3ec`
(`git show a072e3ec:docs/work/v1.8.0-root-queue/records/W83.md`, why the first attempt stopped) first. You are a new
session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w83b` (new; from the shared branch at `b5cdcdef`, which has W90a, W90b and W91 merged) |
| Branch | `codex/v1.8.0-w83b` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W83.md` (new on this branch) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W83B/` |

## Root decision R32 (recorded here)

The first attempt stopped because placement runs in the acknowledgement callback after `DESTINATION_SPAWNED`, so the
same-tick check TASK-W83 asked for would need a new post-acknowledgement retry policy. Root withdrew that requirement
(QUEUE, the W83 row) and now decides, on the merged code:

1. **The wait is at the existing PREPARED wait** in `RocketTransferService.spawnDestination`, before the
   `DESTINATION_SPAWNED` record is created: every chunk column of the destination's checked column set
   (`RocketDiscoveryColumns`, W90a; at most 16 columns, which covers the footprint and the occupancy census) must have
   its entity sections loaded (`ServerLevel.areEntitiesLoaded`) as well as today's readiness; the check reads only and
   never loads or forces a chunk. The wait has its own reason and English and Chinese text (for example
   `message.advancedrocketrycommunity.pad.reason.entities_loading`), logged once per transition, with no new timeout.
2. **Tickets so that the wait ends:** for every flight, legacy included, the destination ticket covers each column of
   the destination's checked column set instead of only its origin, from the PREPARED acknowledgement until the
   ticket release that exists today; the source end keeps today's tickets (governed records keep W90a's margin
   tickets). This is an explicit budget change for legacy flights: at most 16 destination tickets per transfer instead
   of 1, 1,024 at 64 transfers. A destination whose set is not admissible keeps today's origin ticket and today's
   behaviour. Release must remove exactly what was added, in the destination's own Level.
3. **W91's placement callback stays as it is.** It already rechecks entity readiness and occupancy after the
   acknowledgement (`RocketTransferService` around `:695`) and drops the transfer from live dispatch for recovery when
   they fail. Describe in the record what a player sees in that short window, and keep the recovery rebuild path's
   missing occupancy guard and the off-footprint limitation (TASK-W83 item 3) as stated residuals; do not change them.
4. No saved format change and no change to an acknowledgement order. Stop and report if one seems needed.

## What to do

1. Recheck R32 against `b5cdcdef` (W91 changed the transfer service; W90a added the column helper and governed margin
   tickets). Stop and report if a difference changes the decision.
2. Implement R32 items 1 and 2, with the language entries.
3. **Tests:** TASK-W83's two GameTests (a parked rocket in a footprint chunk whose entity sections are not yet loaded:
   the arrival waits with the new reason, then refuses as occupied once they load, and is never placed over it; all
   footprint chunks ready and free: placed as before); a legacy flight whose destination footprint crosses a chunk
   border, with no player near the destination, that lands; and unit tests of the ticket add and release sets. Run each
   new class alone, the whole unit suite, `runData` with a clean diff, and one full GameTest with the unchanged log
   checker (add an expectation only for a line your new code drives), all on committed source and serially. Say
   whether you add a GameTest batch (about 444 batch names; a run-order reshuffle comes past 768).

## Write scope

`RocketTransferService` (the PREPARED wait only; a few lines), `RocketTransferEntities` (the destination ticket set),
the pad occupancy and selector classes if the reason needs them, the request codes or reasons, the language providers,
new tests, and the record. No change to W91's scheduling, a proof, a lease, the replay exclusion, or W90's governed
rules; no relaxed assertion, limit or timeout.

## Commands and not in this task

As WORKER-RULES says. Other workers (W94B, W91R, W91K) also run tests; take the heavy-JVM lock for every GameTest,
`runData` and native run, and release it between runs when another worker waits. Check that C: and D: have more than
10 GiB free before each heavy start. When your heavy runs end, remove your own lock and your `build/gametest/world`
with a plain `rm -rf` after checking that the lock's `owner.txt` names you; if the tool refuses, write the paths in the
record and final answer at once.

Not in this task: a persisted parked-region index (W13-N1's second option), the recovery rebuild guard, W90's measured
costs, ADRs, the risk register, status files and the ledger (Root).
