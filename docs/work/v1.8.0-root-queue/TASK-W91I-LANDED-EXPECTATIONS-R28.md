# TASK W91I: W92's four landed expectations under R23 (Root decision R28), then the regression and W92's native cuts

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W91H-MERGE-AND-LANDED-PORT](TASK-W91H-MERGE-AND-LANDED-PORT.md),
[records/W91.md](records/W91.md) (its R23 decision and the W91H section, in particular the "whole-journal stop" table),
[records/W85-DESIGN.md](records/W85-DESIGN.md) and `records/W92.md` first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (unchanged; at W91H's record `95cf30dc` plus this task) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Record | `records/W91.md` (extend it) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/landed-w91i-20261018/` |

## Root decision R28 (recorded here)

W91H found four W92 expectations that assume the old whole-journal acknowledgement:
`RocketLandedDisassembliesTest.aLaterAcknowledgedGenerationDiscardsTheRemovalContinuationIntoAWriteBack`, the second
half of `aNotifierFailureInTheDiscardPathsIsContainedAndTheWriteBackGoesOn`, and the writer-batch GameTests G8
(`unknownRemovalThenLaterGeneration`) and G11 (`committedRemovalWithADelayedSuccessor`).

Root accepts R23's behaviour for them. A committed generation captured after T's removal leaves the disk without T,
exactly as the removal's own acknowledgement would, so the removal is durable and its continuation may run. This keeps
Design M's invariant that blocks are restored only after T's absence is durable. A definite failure or an UNKNOWN write
keeps the continuation until such a generation commits (R23, W86 G2 and G3). An unrelated dirty change, such as an
outbox-only change, does not hold L's continuation; holding it was P1-F3's defect. Reason: R23 is the accepted
scheduling rule, and these four tests encode the rule it replaced.

What changes and what must stay:

1. In each of the four tests, replace only the expectation that the continuation is discarded or waits for the whole
   journal. The new expectation is that it runs once, after the first committed generation captured after T's
   removal, and assert the full outcome: exactly one restoration, T absent from the committed file, no write-back, no
   duplicate rocket or blocks, the pad and claim released as M2 releases them, and the report the restoration gives.
   Keep every other assertion in those tests.
2. Add one unit case for the boundary R23 relies on: the generation already in flight when T is removed, captured
   before the removal, commits, and L's continuation does not run; it runs only after a later generation commits.
3. Keep the discard path covered by the causes that R23 still has: the continuation's own refusal (for example the
   confirmation lifetime, a changed E, or W92-R2's transaction-journal check), an action that throws or is refused, a
   stop or an abort, and a change of L's own record. Name the test that covers each cause in the record, and add a
   case for any cause that has none, as a unit test where possible.
4. List each changed test with the old expectation, the new one and this decision, as R26 did.

Root adds the note to ADR-065's P1-F8 implementation note and to `records/W85-DESIGN.md` at the merge; do not edit
those files.

## What to do

1. Apply R28 as above.
2. Run W92's landed classes and W91's scheduling classes alone, the whole unit suite, `python -B
   scripts/validate_repository.py --require-approved-identity`, and one full GameTest with the unchanged log checker,
   all on committed source and serially.
3. Rerun W92's native landed cuts with `scripts/run_v180_landed_disassembly_cuts.py` on a jar built from your last
   committed source: K1, K2, K4, K5, K9 and K10 with two restarts each, as `records/W92.md` ran them. Compare each cut's
   outcome with W92's table; any difference is a finding to report, not to adjust.
4. State in the record that the merged diff (shared `0a3964e7` to your last commit) is ready for R23 item 7's
   independent review.

## Write scope

The four tests, the new unit cases, any new discard-cause cases, and the record. No production change; if one seems
needed, stop and report. No change to a saved format, an acknowledgement order, a proof, a lease or the replay
exclusion, and no relaxed limit or timeout.

## Commands and not in this task

As WORKER-RULES says. Other workers (W94B, W90a, W91N) also run tests; take the heavy-JVM lock for every GameTest and
native run, and release it between runs when another worker waits. Check that C: and D: have more than 10 GiB free
before each heavy start (C: has about 13 GiB). After each native session, delete its server copy and exported tree.
When your heavy runs end, remove your own lock and your `build/gametest/world` with a plain `rm -rf` after checking that
the lock's `owner.txt` names you; if the tool refuses, write the paths in the record and final answer at once.

Not in this task: W91N's native timing, the F3 and N2 cuts, the independent review, ADRs, the risk register, status
files and the ledger (Root).
