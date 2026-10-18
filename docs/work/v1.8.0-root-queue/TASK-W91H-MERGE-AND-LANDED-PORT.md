# TASK W91H: merge the shared branch into W91 and port W92's landed disassembly to the per-record journal API

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [records/W91.md](records/W91.md) (its R23 to R26
decisions and the W91F and W91G sections), [records/W85-DESIGN.md](records/W85-DESIGN.md) (W92's frozen Design M) and, after the merge, `records/W92.md`
(W92 and W92F) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (unchanged; at W91G's record `1ee010d0` plus this task) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Merge input | the shared branch `codex/v1.8.0-classic-content` at `0a3964e7` (W92, W92F, W93, W94, W95 and W95F merged) |
| Record | `records/W91.md` (extend it) |

## Root's trial merge (2026-10-18, W91 `b65c2dc0` into shared `80ff9155`)

- Text conflicts: `RocketTransferService` (W92's `landed` field and W91's `continuations` field: keep both; W92 moved
  `releaseLandedReservation` into `RocketLandedDisassemblies.releasePlain`, while W91 added `actionable(L)` and a
  reservation before the mutation there) and `tests/test_check_gametest_log.py` (both sides added four ERROR entries:
  keep both comments; the total is the sum).
- Compile break: W91 removed the no-argument `RocketTransferSavedData.canQueueEffect()`. W92 calls it at
  `RocketLandedDisassemblies.java:138` and `gametest/RocketLandedDisassemblyGameTests.java:357`. `compileTestJava` was
  not reached, so more test sites may exist. These line numbers are from `80ff9155`; W92F has since moved the
  first call into `RocketLandedDisassemblies.admission(...)`.

## What to do

1. **Merge** `0a3964e7` into this branch with a normal merge commit. Resolve the text conflicts as above.
2. **Port W92's landed disassembly to R23's per-record API**, keeping W85 Design M's semantics (the order, the
   restoration latch, T kept with its locks until the write-back is acknowledged, the pending entry P):
   - `releasePlain` gets W91's change to `releaseLandedReservation`: `actionable(L)` and a reservation taken before
     `journal.remove`, then `afterAcknowledged(server, reservation, ...)`;
   - `begin`'s admission uses L's own `canQueueEffect(L)` reservation, held through the mutation it guards, instead
     of the whole-queue check, and refuses before any change when it is absent (N2's rule);
   - the removal and the write-back queueing use a reservation taken before their mutation; `Port.queue` takes it;
   - classify every journal call in `RocketLanded*` as per-record (`actionable(L)`) or whole-journal (`clean()`) as R23
     says, in a table in the record. Stop and report if a W92 path needs whole-journal semantics that R23 does not give.
3. **Tests:** keep every W92 and W91 assertion. Change a test only where the API change forces it (for example the
   GameTest at line 357), list each such change with the reason, and add a unit case for each new refusal before
   mutation (no reservation: nothing changes). Run W92's landed classes and W91's scheduling classes alone, then the
   whole unit suite, `python -B scripts/validate_repository.py --require-approved-identity`, and one full GameTest
   with the unchanged log checker, all on committed source and serially. Say whether a GameTest batch is added.
4. State in the record that the merged diff (shared `0a3964e7` to your last commit) is ready for R23 item 7's
   independent review.

## Write scope

The merge resolution, `rocket/server/RocketLanded*`, `RocketTransferService` (the landed delegation only), the forced
test adaptations, new unit cases, `scripts/gametest_expected_log.json` and its pinned total only as the merge requires,
and the record. No saved-format change, no change to an acknowledgement order, a proof, a lease or the replay
exclusion, and no relaxed assertion, limit or timeout.

## Commands and not in this task

As WORKER-RULES says; heavy-JVM lock for every GameTest, released between runs when another worker waits; clean up
your own lock and `build/gametest/world` after checking `owner.txt`, or write the paths in the record and final
answer if the tool refuses. W91N works in its own worktree (`arce-v180-w91n-native`) and does not touch this one. Not in this task: W91N's native timing, the F3 and N2 cuts, the review, status files, the
ledger, ADRs and the risk register (Root).
