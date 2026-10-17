# TASK W91: part-1 N2, then per-record journal scheduling (P1-F3), from the frozen W86 design

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w91-p1-f3` (from the shared branch after the W11b2 merge) |
| Branch | `codex/v1.8.0-w91-p1-f3` (local commits only; Root merges) |
| Design (frozen) | [records/W86-DESIGN.md](records/W86-DESIGN.md) revision 2, frozen by Root on 2026-10-16 (merged `f09e5858`), and its record [records/W86.md](records/W86.md) |
| Leaf rows | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md) section 5: P1-N2 and P1-F3; section 1's P1-F3 and P1-N2 rows |
| Record | `docs/work/v1.8.0-root-queue/records/W91.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W91/` |

W11b2 (settlement, reconciliation and governed flights) is now on the shared branch (merge `eff6eff3`). Production
admission stays disabled.

## What to do

1. **Recheck the design against the merged code first.** W86 read W11b2 at `a8d81d24`; the merged code has moved on
   (continuations 26 to 54, journal leases R17 item 3, `RocketJournalLeases`, the governed executors, continuation
   53's pending pad reservations). Update W86's Appendix A site table against the current code in your record. If a
   site's behaviour differs from what the design assumed in a way that changes its rule, stop and report that site.
2. **P1-N2, the first self-contained commit** (W86 section 5): `failBackToSource` reserves its continuation in the
   two-slot queue before it changes or removes anything, so a full queue leaves the source, the record and the pad
   intact. Tests: G-N2 with its stop or abort variant, as W86 section 8 lists.
3. **P1-F3** (W86 sections 2 to 4 and Appendix B): per-record versions and absence leases on the one writer, so an
   unrelated journal write no longer pauses every transfer; `actionable(L)`, single-use reservations and bodies that
   recheck captured state; the conservative Moon-landing WAIT; the bound of 128. Keep everything W86 section 4 lists
   as unchanged (acknowledgement order of every record kind, proofs, holds, replay exclusion, saved format).
4. **Tests** as W86 section 8 and Appendix C say: the two-transfer delayed-write test, the A0 cases, the changed
   existing expectations (list each with the reason), and eight-transfer native timing with the escrow-consumer
   measurements. The hard criterion is "an actionable flight that did not step: 0".
5. Run the whole unit suite, one full GameTest with the unchanged log checker (add an expectation only for a line your
   new test drives), and the native timing run. If you add a GameTest batch, say so (about 376 batch names now; about
   384 reshuffles the run order).

## Write scope and boundaries

The transfer journal and transfer service scheduling (`rocket/flight/persistence/RocketTransferSavedData`,
`rocket/server/RocketTransferService` and the sites W86 names), the governed executors only where W86 names them, new
tests, changed tests that W86 Appendix C lists, and the record. No saved-format change, no change to an acknowledgement
order or a proof, and no relaxed assertion, limit or timeout. Stop and report if one seems needed.

## Commands

As WORKER-RULES says. **While `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` exists and its `owner.txt` names
`W11B2-C54`, start no Gradle and no Java at all** (continuation 54's benchmark needs a quiet machine); read and write
code meanwhile. After it is gone, compile and run unit tests freely, and take the heavy-JVM lock for
`runGameTestServer` and native runs. Check that C: and D: have more than 10 GiB free before each heavy start (C: has
about 13 GiB). If the tool policy refuses to let you remove your own lock or world, write the exact path in your record
and final answer; Root removes it.

## Not in this task

P1-F8 (W92), W90a and W90b, W11b3's other leaves, production admission, ADRs, the risk register, status files and the
ledger (Root).
