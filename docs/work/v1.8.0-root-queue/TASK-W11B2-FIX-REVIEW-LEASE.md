# TASK W11b2-fix-review-lease: independent read-only review of continuations 47 and 44 (per-rocket lease, writer accounting, settlement fixes)

Root: Claude. Date: 2026-10-17. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and a reviewer: you
change nothing in any repository worktree.

The final independent review of W11b2 ran in two parts. Part A (settlement, receipts, capacity, operator) returned
MERGE-WITH-FOLLOWUP; continuation 44 (Claude) fixed its F2 to F5 and I1, I2 and I4. Part B (the governed flight path)
returned FIX-FIRST; Root answered it with R17 and R18; continuation 47 (Claude) implemented R17 items 3 and 4 and part
B's F8 to F10, and continuation 46 the rest (reviewed separately,
[TASK-W11B2-FIX-REVIEW-FLIGHT](TASK-W11B2-FIX-REVIEW-FLIGHT.md)). This review comes from a different session and model
than the implementers (AGENTS.md section 13). It decides whether part B's F4, F5, F8, F9 and F10 and part A's F2 to F5,
I1, I2 and I4 are closed, and whether Root's integration is sound.

| Item | Value |
|---|---|
| Subject | The integrated W11b2 head `2ed7f18e` |
| Continuation 47 | `git diff 894ef3ae..2c1b5e6a` (record `records/W11B2-C47.md`); merged as `184e4ccc`, with one conflict in `EscrowFlightWrites` that Root resolved by keeping continuation 45's timing hook and continuation 47's recapture |
| Continuation 44 | `git diff 613f4e83..8a90abd1` (record: the "Continuation 44" section of `records/W11B2-F7F8.md`) |
| Root's commits | `7efe2af2` (merge of continuation 45's benchmark harness, `693a4d9d..fb0f1bec`), `809e5fdb` (continuation 45's admission diagnostic changed to R17's rule), `858c148e` (merge of continuation 46) and `2ed7f18e` (a log expectation) |
| Your checkout | `D:/GitHub/arce-v180-w11b2-fix-review-lease`, a detached worktree at `2ed7f18e`. Read it and run tests there; do not commit, switch or reset it |
| Reviews | Part A `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-final-review-A/REVIEW.md` (SHA-256 `4d9d827380980d7ef69f6e33a0302d039404c356043bbde4b5c50654157d72a2`) and part B `W11b2-final-review-B/REVIEW.md` (SHA-256 `60581547d83db3bf70b1b1d3d17eecf140df73afcaaa74144f930e8d2b30b0bd`) |
| Contract | `records/W11B2-DESIGN-2.md` (frozen at `238f4b9a`); R1 to R16; **R17** `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C46/ROOT-DECISION-R17.md` (SHA-256 `c31254f82e023247b8d9d1df5a89d6919cec87bfce0635e5a7ad79f5e6e664c1`) |
| Output | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-fix-review-lease/REVIEW.md`, and its SHA-256 on the last line of your answer |

## What to check

1. **R17 item 3 and part B F4.** The checked lease of logical rocket L (`RocketJournalLeases`,
   `RocketTransferSavedData.logicalLease`, `EscrowFlightJournal`) holds exactly while the journal has L's exact
   acknowledged record instance or its checked absence, L's acknowledged guard is unchanged, no other record names T
   or the serial, and no change of L is unacknowledged; another rocket's change never revokes it. Check every path R17
   lists (F1, F7, F8, the pre-F2 cancellation, settlement O3 and O4, reconciliation C3 to C7) and continuation 47's
   extension to C2. Check the immutable view the writer reads off the owner thread, the 1,024-entry history floor and
   its conservative fold, and whether any path R17 lists still uses the journal-wide lease. Check the batch
   recapture: only the refused request receives `false`, the others are captured again, and nothing loops.
2. **R17 item 4 and part B F5.** `GUARD_REFUSED` is produced only for a guard refusal before any replacement attempt
   with no earlier unknown outcome, never counts towards unavailability, and is handled wherever
   `FAILED_BEFORE_REPLACE` is (admissions, flight batches, settlement); autosave runs while the writer is unavailable.
3. **Part B F8 to F10.** The gate wiring test (real player list, connections and `server.properties`); a written
   PREPARED launch stays pending when the F2 callback queue is full, and F3 still starts its countdown; retry
   histories retained once per tick.
4. **Part A F2 to F5, I1, I2 and I4 (continuation 44).** The retry cooldown keyed by the settlement intent's digest;
   the provenance GameTest's coverage of rejected stacks; the completion probe waiting for the product pruning cursor
   instead of pruning; the `ARCE_ESCROW_PRUNING_REFUSED` WARN (one per group, endpoint and reason transition, bounded
   state, no effect change) and `inspect`'s `pruning=` field; I1, I2 and I4.
5. **Root's integration.** Continuation 45's timing hooks (`EscrowTimingSink`, `EscrowLedgerWriter.snapshot`,
   `EscrowFlightWrites`, `EscrowRuntime`, `FirstEventCutSeams`) only observe; the `EscrowFlightWrites` conflict
   resolution; and `809e5fdb`, which replaced an assertion that pinned the journal-wide refusal with R17's rule. Is
   that change justified by R17, and does it still prove the refusal for a rocket's own record?
6. **Tests.** Continuation 47 changed three existing assertions to R17's rule; check each keeps a refusal for L's own
   change. Look for weakened or removed assertions and probes that do the product's work.
7. **AGENTS.md section 3.3.** Update part B's section 3.3 items 2, 7 and 8 and part A's pruning item as the code now
   stands.
8. **Rerun** in your checkout, with `JAVA_HOME` set to `C:/Program Files/Java/jdk-17.0.7`:
   `gradlew.bat --offline test --tests '*RocketJournalLease*' --tests '*RocketTransferSavedData*' --tests '*EscrowFlightJournal*' --tests '*EscrowFlightWrites*' --tests '*EscrowLedgerWriter*' --tests '*EscrowGovernedLaunchCallback*' --tests '*EscrowGovernedPreparation*' --tests '*EscrowGovernedDispatch*' --tests '*EscrowSettlement*' --tests '*EscrowOperator*' --tests '*EscrowReservationWalk*' --tests '*EscrowStoreRefusalBusy*' --tests '*EscrowFlightBenchmark*' --tests '*ReleaseTestPlayers*' --console=plain`
   and `python -B -m unittest discover -s tests -p "test_escrow_reconciliation_runner.py"` and
   `python -B -m unittest discover -s tests -p "test_v180_governed_benchmark.py"`. Do not run GameTests, native runs
   or the whole suite, and take no heavy-JVM lock.

## Output format

- A verdict: MERGE-READY, MERGE-WITH-FOLLOWUP, FIX-FIRST, or BLOCK.
- For each of part B's F4, F5, F8, F9 and F10 and part A's F2 to F5, I1, I2 and I4: CLOSED, PARTLY CLOSED or OPEN,
  with the evidence.
- New findings, each with an ID, a severity (Critical, High, Medium, Low, Info), the file and line at `2ed7f18e`, the
  failure scenario and a suggested fix.
- The updated section 3.3 items from item 7.
- The commands you ran and their results.
- The SHA-256 of REVIEW.md on the last line of your answer.

## Not in this task

Any edit, commit, push or merge in a repository worktree; any other agent; GameTests, native runs and benchmarks;
continuation 46's rider, passenger, transient-observation and executor-hold changes (the other review).
