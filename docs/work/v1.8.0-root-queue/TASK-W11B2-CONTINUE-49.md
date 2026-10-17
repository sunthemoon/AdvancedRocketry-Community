# TASK W11b2, continuation 49: the lease fix review's LR1 (checked conflict exclusion) and LR2 (F6 under the per-rocket lease)

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W11B2-CONTINUE-14](TASK-W11B2-CONTINUE-14.md)
and the documents it names first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-c49` (new, at the integrated W11b2 head `2ed7f18e` plus this task) |
| Branch | `codex/v1.8.0-w11b2-c49` (local commits only; Root pushes and merges) |
| Input | The lease fix review, `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-fix-review-lease/REVIEW.md` (SHA-256 `d1d513c2cc68e431c35becd0a0870b371668989884764def769030325fe0f3ce`), sections 1 and 3, and its reproduction `reviewer-tests/.../ReviewerPendingRemovalLeaseTest.java` in the same folder |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`); R1 to R18, especially **R17 item 3** (`D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/ROOT-DECISION-R17.md`, SHA-256 `c31254f82e023247b8d9d1df5a89d6919cec87bfce0635e5a7ad79f5e6e664c1`); continuation 47's record `records/W11B2-C47.md` |
| Record | `records/W11B2-C49.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C49/` |

The integrated head `2ed7f18e` has continuations 44 to 47 merged. The independent review of continuations 47 and 44
returned FIX-FIRST on two Medium findings; everything else it checked is closed. Continuation 48 works in parallel in
`D:/GitHub/arce-v180-w11b2-c48` on the pre-F2 release-test probe and runner only; a separate review of continuation 46
may add findings in other files.

## Root's reading of R17 item 3 for this task

- The lease's conflict exclusion ("no record naming T or the serial") is a **checked** exclusion: it must hold in the
  last acknowledged journal as well as in RAM. A conflicting record of another rocket that has been removed in RAM
  but whose removal is not yet acknowledged still refuses the lease; the refusal ends when that removal is
  acknowledged. Unrelated records of other rockets, acknowledged or not, still never revoke L's lease.
- R17 item 3 also covers **F6's capture and barrier callback**, which precede the F7 write it lists: a healthy F6 proof
  of L is kept while L's own checked lease holds, and is discarded only by L's own change, L's unacknowledged change,
  a conflict as above, a failed write, or the existing native-proof, passenger and bay prerequisites.

## What to do (one commit per item, each with its tests)

1. **LR1.** Make `RocketJournalLeases` refuse a lease while any record other than L's exact expected record names T or
   the serial in either the current records or the last acknowledged generation, as the review suggests (an immutable
   acknowledged conflict index, or equivalent retained removal information, published with the view). Add the
   reviewer's two failing cases, a failed-removal case and the eventual-acknowledgement case to the repository tests;
   keep the third reviewer case (own-record appear, remove and acknowledge). Keep the view immutable and readable by the
   writer off the owner thread, and keep the history bound.
2. **LR2.** Use L's checked lease and per-rocket authority, instead of `journal.actionAuthorityAvailable()`, at F6's
   entry, barrier callback and checkpoint diagnostic (`EscrowGovernedTerminal`, the lines the review names). Unit-test
   an unrelated pending journal change during the barrier (proof kept) against L's own change (proof discarded).
3. Run the whole unit suite after each item, then one full GameTest with the unchanged log checker at the end, and
   rerun the C0 to C8 reconciliation cuts (`scripts/run_v180_escrow_reconciliation.py`) once, because item 1 changes
   their lease.

## Write scope and boundaries

`RocketJournalLeases`, `RocketTransferSavedData`'s lease publication, `EscrowFlightJournal`, `EscrowGovernedTerminal`'s
journal checks, their tests and the record. No change to an acknowledgement order, a proof at capture or replacement
beyond the two items, a schema, the replay exclusion, the log checker or its expectations, and no relaxed budget,
limit, timeout or assertion. Stop only for a change outside these two items: commit what you have with your record,
and report.

## Commands

As TASK-W11B2-CONTINUE-14, with evidence in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C49/`. Other workers share
the heavy-JVM lock (continuation 48 runs native cuts); wait for it as WORKER-RULES says, and never remove a lock you do
not own. If the tool policy refuses to let you remove your own lock, world or server copy, write the exact path in your
record and final answer and continue; Root removes it. If a GameTest run crosses midnight, check every rolled log part.

## Not in this task

Continuation 48's pre-F2 probe and runner; continuation 46's rider, passenger, transient-observation and executor-hold
logic; the benchmark; production admission; ADRs, the risk register, the R-035 text, status files and the ledger (Root).
