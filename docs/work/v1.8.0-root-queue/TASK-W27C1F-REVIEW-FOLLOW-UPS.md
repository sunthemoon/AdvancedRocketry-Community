# TASK W27c1F: W27c1's review follow-ups before merge (the bay receipt store)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W27C1](TASK-W27C1-BAY-RECEIPT-STORE.md),
`records/W27C1.md` on the W27c1 branch, and the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W27C1-review/REVIEW.md`
(SHA-256 `3e9ca56863340dfc2a92cfb82e8fa156260ccc50ce5dfdfd6cc8518d56ef6255`, verdict APPROVE-WITH-CHANGES; its probe
sources are in the same folder) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w27c1` (W27c1's; its worker has ended and the tree is clean at `f3e86bcf`) |
| Branch | `codex/v1.8.0-w27c1` (continue it with local commits only; Root merges) |
| Record | Append a section "W27c1F" to `docs/work/v1.8.0-root-queue/records/W27C1.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W27C1F/` |

## Root decision R37 (recorded here, 2026-10-20)

1. **W27C1-R1:** while the store is unavailable and no write is in flight, it writes one generation with no new changes
   at most every 200 ticks; an exact commit makes it available again, as the escrow ledger recovers through its
   autosave (R17 item 4). Definite and unknown failures both count towards the three; guard refusals do not.
2. **W27C1-R2:** at most one in-flight change per `op` and per satellite, and uniqueness is checked against every state
   (durable, queued, captured, unknown), not only RAM, so cancelling a definitely failed generation is exact.
3. **W27C1-R5:** the retire command writes its audit line (operator, key, `op`, `outcome=REQUESTED`) when it queues the
   removal, so the operator's action is always audited, and a second INFO line with the final outcome. Root reads the
   design's "one audit line" as at least one line; the outcome line is in addition.
4. The Low findings R6, R7, R8, R9 and R10 are fixed here as the review suggests (R10: freeze phase and key for
   released receipts in the store). R6's sibling in `EscrowLedgerWriter` is fixed here too, in its own commit, with a
   unit test; it is outside W27c1's original scope but in the same writer pattern.
5. R3 and R4 (R-039 and ADR-065's note) are Root's; Root applies the review's corrected text after this task.

## What to do, each in its own commit with a test

1. W27C1-R1 under R37 item 1, with a test that repairs the storage after three definite failures and sees the store
   become available without a new change.
2. W27C1-R2 under R37 item 2; turn the review's probes 2 and 3 into regression tests that fail before the fix.
3. W27C1-R5 under R37 item 3.
4. W27C1-R6: wrap each IO task of `BayReceiptWriter` and `EscrowLedgerWriter` in `catch (Throwable)` and post an UNKNOWN
   completion with the candidate when one exists; fix `EscrowLedgerWriter`'s refused generation leaving `active` set
   (the review's sibling 1). The existing drain clock from W104 stays unchanged.
5. W27C1-R7: refuse the test index substitution unless the delegate is NO_FILE or LOADED, or restore the exact prior
   state; then rerun W95's native cuts `LF_M_N3_FULL` and `LF_M_N3_NOT_LOADED` once each with
   `scripts/run_v180_loader_cuts.py` (two restarts each), and say whether the oracle needs the `bay=` field.
6. W27C1-R8: require each charging result to equal its check; the record states that the checks reserve nothing.
7. W27C1-R9: one GameTest in the receipt batch that plants a real transfer record (with the receipt's transfer ID),
   an assembly record and a disassembly record, and expects each refusal through the dispatcher.
8. W27C1-R10: freeze phase and key for released receipts.

## Commands

Each new or changed test class alone, the whole unit suite once, and one full GameTest with the unchanged log checker
under the heavy-JVM lock on a **committed** SHA (a new expected line needs its own manifest entry with an exact
count); the native cuts of item 5 under the lock; `python -B scripts/validate_repository.py --require-approved-identity`.
Check that C: and D: have more than 10 GiB free before each heavy start; release the lock at once after each run; run
long heavy jobs as detached processes. At the end, delete your export trees, native copies and `build/gametest/world`,
and make sure the lock is released. If the tool refuses, record the exact paths.

## Write scope and not in this task

The bay receipt classes from W27c1, `EscrowAdmissionAuthority` (R8 only), `EscrowLedgerWriter` (R6 only), the W95 test
hook in `EscrowLoaderCutProbe` and `BayReceipts` (R7), their tests, the log expectation file for new lines, and the
record. No change to a hash, a saved format, a bound, the frozen `tryReserve` and `tryRetain` results or the no-release
rule, and no relaxed assertion, limit or timeout. Not in this task: W27c2 to W27f, ADRs, the risk register, status
files and the ledger (Root).
