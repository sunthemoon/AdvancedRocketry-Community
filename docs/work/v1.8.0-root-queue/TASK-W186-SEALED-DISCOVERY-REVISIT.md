# TASK W186: loaded sealed discovery finishes a chunk before it moves on

Root: Claude. Date: 2026-10-29. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w186` (new; from the shared head named in your prompt) |
| Branch | `codex/v1.8.0-w186` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W186.md` (new; the design note at its top) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W186/` |
| Diagnosis | [records/W184.md](records/W184.md), "Failure 1", with its instrumented run under `D:/GitHub/ARCE-Task-Evidence/root-queue/W184/diag-full/` |

**Root decision R146.** W184 found that `SealedLoadedDiscovery` publishes only one or two of a chunk's members per
sweep. A publication changes the chunk's block-entity map, the cursor meets `ConcurrentModificationException`, and the
sweep moves to the next chunk. On a server with about 10,000 loaded chunk holders, one sweep takes about 75 ticks, so a
sealed rocket with 100 members beyond the hint bound in two chunks needs roughly 1.5 to 3 minutes to finish. Root treats
that tail as a defect (W184's option C). The hosted timeout of
`SealedLazyGameTests.loadedDiscoveryFindsCarriersBeyondTheHintBoundWithoutReplayingNativeCells` is its symptom.

## What to do

1. **Design note first** (sealed publication is persistence-adjacent). Cover:
   - how the cursor revisits the same chunk after a publication changes its map, bounded by that chunk's block-entity
     count, instead of moving on;
   - how this stays inside the existing `SealedWorkBudget` (256 units per tick) and its alternation with attempts in
     `SealedLazyWork.tick`;
   - what happens when a chunk unloads, or its map keeps changing, during a revisit;
   - that no saved format, journal entry, bound or budget changes.
2. **Implement it** in `SealedLoadedDiscovery` (and `SealedLazyWork` only if the alternation needs it).
3. **Tests.**
   - An A0 test: several members beyond the hint bound in one chunk are all found in one sweep, and a map that keeps
     changing ends the revisit within its bound. It fails first on the unfixed code.
   - `SealedLazyGameTests` stays unchanged; it should now finish in fewer ticks. Record its tick count before and
     after in a full local run, where the world is large.

## Verification

- the new tests alone, failing first, then the whole unit suite;
- every sealed batch three times, and one complete GameTest, under the heavy-JVM lock.

An independent read-only review follows before merge.

## Not in this task

Releasing the GameTest harness's forced chunks (W184's option A), changes to the test fixture (option B), other sealed
work, ADRs, status files and the ledger.

## Review findings under Root decision R155 (2026-10-29)

The review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W186-review/REVIEW.md` (SHA-256 `813aebad…dcd15`), returned
APPROVE-WITH-CHANGES; its probe test is in the same folder. A fresh session continues on `codex/v1.8.0-w186` from
`e002c4b23` and appends a "Review fixes (R155)" section to the record:

1. **W186-R1.** The restart allowance counts any change to the chunk's block-entity map, so a chunk with no carrier
   and a busy map can hold the cursor: 1,000 block entities changed every tick held it 1,002 ticks, and 5,000 changed
   every 19 ticks held it 95,020 ticks. Grant a restart only after the lazy attempt publishes a carrier in the
   current chunk, capped at that chunk's block-entity count. The write scope widens to the one call this needs from
   `SealedDisassemblyCoordinator.attemptLazy`. Add the review's probe as a test that fails first: a busy chunk with no
   carrier keeps no more than its pre-fix cost.
2. **W186-R2.** State the time cost of the revisit in the record, including the busy-chunk case.
3. **W186-R3.** Make the unload test check that a revisit was under way, so it fails on the unfixed code.
4. **W186-R4.** Update the method comment to say that a step can be a restart.

Verification: the new tests alone, failing first; the whole unit suite; every sealed batch three times under the
heavy-JVM lock. Root's hosted run on the pushed head stands in for a complete GameTest.
