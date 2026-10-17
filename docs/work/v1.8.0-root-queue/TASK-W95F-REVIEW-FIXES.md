# TASK W95F: the W95 review's follow-ups (B3-E)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W95](TASK-W95-B3E-LOADER-ADMISSION.md) and
[records/W95.md](records/W95.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w95f` (from the shared branch, which has W95 merged at `444f2f79`) |
| Branch | `codex/v1.8.0-w95f` (local commits only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W95-review/REVIEW.md` (fresh Claude session, MERGE-WITH-FOLLOWUP, SHA-256 `6ed0b81d511d28acb6a5d8fc1de6c376f983702f43cac7bf413c25f74f577912`), findings W95-R1 to W95-R5 |
| Record | `records/W95.md` (add a W95F section) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W95F/` |

## Root decisions on the review (recorded here)

- The two forms of the gated switch (per device in GameTests; a system property behind the release-test hooks on a
  dedicated server), the chain rule and the creation-failure choices in the W95 record are accepted.
- W95-R3 is accepted as a format change: settlement records and receipts that name a loader now persist the
  `FLUID_TANK_V1` signature kind, and an older jar refuses a ledger that holds one. No existing world can hold such a
  record while admission is disabled. Correct the record's "no saved format changed" statement.
- Root records R-035's scope as "present, disabled" at this merge.

## What to do

1. **W95-R1 (Medium):** fix the device progress status as the review suggests: compute "applied" (the device's
   section holds this operation's marker) before the availability test; when the coordinator is not operational or
   the writer is unavailable (add a package-private accessor), return `ESCROW_PENDING` when applied and
   `LEDGER_UNAVAILABLE` otherwise. Extract a pure function and pin every combination with a unit test.
2. **W95-R2 (Low):** add `@AfterBatch` cleanup to both new GameTest classes, as `BlackHoleGeneratorGameTests` and
   `ElevatorCapsuleGameTests` do, so a failed or timed-out test restores the shared ledger and its file, closes the
   device gates, releases the tickets and clears the fixtures; when a retarget refuses because work is held, log one
   distinctive line naming the batch.
3. **W95-R4 (Low):** add a test that the loader path stays closed with the release-test gate closed (no ledger created,
   no fill queued, no resource moved, the expected status shown).
4. **W95-R5 (Info):** a loader must not show READY when the 256-entry queue was full and it was not queued; fix or
   explain. Record the whole unit suite's test count in the evidence log.
5. Run each changed class alone, the whole unit suite, and one full GameTest with the unchanged log checker (add an
   expectation only for a line your new code drives). Do not add a GameTest batch unless needed (379 batch names after
   W92; about 384 reshuffles the run order).

## Write scope and boundaries

`resourceescrow/EscrowLoaderAdmission`, the coordinator or writer accessor the fix needs, the two W95 GameTest classes,
new tests, and the record. No change to proofs, acknowledgement orders, leases or saved formats, no enabled production
admission, and no relaxed assertion, limit or timeout.

## Commands

As WORKER-RULES says. Other workers (W91D, W94B) also run tests; take the heavy-JVM lock for every GameTest run and
release it between runs when another worker waits. When your heavy runs end, remove your own lock with a plain
`rm -rf` of exactly `D:/GitHub/ARCE-Task-Evidence/locks/heavy-jvm` after checking that its `owner.txt` names you; if the
tool refuses, write the path in your record and final answer at once. Check that C: and D: have more than 10 GiB free
before each heavy start.

## Not in this task

B3-S1 to B3-S6, W91, W92, W94B, enabling production admission, ADRs, the risk register, status files and the ledger
(Root).
