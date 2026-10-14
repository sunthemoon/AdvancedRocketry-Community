# TASK W54: make three escrow writer tests independent of machine load (parallel bug)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w54-escrow-timing` (from the shared branch) |
| Branch | `codex/v1.8.0-w54-escrow-timing` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W54.md` (new) |
| Source | `records/W52.md` (on `codex/v1.8.0-w52-item-icons`), lines 107-117 |

## The failures

W52's whole unit suite, its isolated run and its locked build all failed on the same three tests
while other workers ran GameTests on the machine:

- `EscrowAdmissionAuthorityTest.realUnknownCaptureKeepsSharedCapacityAcrossRetriesAndExactAcknowledgement`
  (line 110): the writer did not become unavailable within the five-second polling deadline;
- `EscrowFloorsTest.collectionWaitsForBothJournalAndLedgerAcknowledgementsAndFailureKeepsTheFloor`
  (line 105): the journal did not acknowledge within the five-second deadline;
- `RocketEscrowAuditTest.failedRepairWriteRetainsAll64LockedOperationsUntilAnExactRetry` (line 99):
  the writer did not become unavailable within the five-second deadline.

Root reran the three classes on the shared branch and on the W52 branch on a lighter machine, and all
passed. So the cause is real-time polling of off-thread writers under load, not a code change.

## What to do

- Make these tests deterministic. **Do not** extend a timeout, relax an assertion or remove a test;
  AGENTS.md section 5 forbids it.
  - For example, drive the off-thread writer through an injectable executor or a latch that the
    test controls.
  - Or wait on the writer's own completion signal rather than wall-clock polling.
- Keep each test's intent and every assertion.
- Change production code only for a test seam that is inert in production. Say exactly what it is.
- Check every other test that polls an off-thread escrow, journal or ledger writer against a
  wall-clock deadline. List them, and fix the same pattern where it exists.
- **Prove it under load.** Run the three classes while a GameTest server runs (take the heavy-JVM
  lock for that GameTest), and once without it. Record both results.

## Commands

- The affected test classes alone, then the whole unit suite once.
- At the end, delete any generated world with a plain `rm -rf` and release the heavy-JVM lock. If the
  tool refuses, record the exact paths; Root deletes them.

## Not in this task

- Any production escrow behaviour.
- Status files and the ledger (Root).
