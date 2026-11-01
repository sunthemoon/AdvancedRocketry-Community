# TASK W242-fix2: the review's changes to the bay safe exit

Root: Claude. Date: 2026-11-02. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w242` (existing; branch `codex/v1.8.0-w242` at `f0c622a30`, whose sharded run 41341526011 passed all 1,641 GameTests) |
| Findings | `D:/GitHub/ARCE-Task-Evidence/root-queue/W242-review/REVIEW.md`, SHA-256 `bfcc2e8e10c1c46cc2e07462a63572f34facff8f0b1b89b52f06f1bd75bf603c`: W242-R1 and R3 to R10 |
| Task it answers | [TASK-W242](TASK-W242-BAY-EXIT-S2.md), [TASK-W242-FIX](TASK-W242-FIX.md) and [W242-REVIEW](TASK-W242-REVIEW.md); Root decisions R254, R264 and R274 |
| Record | Append a "W242-fix2" section to `docs/work/v1.8.0-root-queue/records/W242.md`; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W242-fix2/` |

**Root decision R274**, under the owner's delegation:

1. **W242-R1 (Medium), option (a).** First add a read-only, test-only probe that records, when the rocket's location
   hook is entered, whether the rocket's column is in `ServerChunkCache`'s recent-chunk cache and whether vanilla's
   dismount read added an UNKNOWN level-33 ticket. Run the bay-exit batches focused, repeated (at least five
   cohorts), before the change. Then override `RocketEntity.removePassenger`: call `super` (which dispatches the
   dismount game event) and then, on the server only, a non-creating `getChunkNow` of the rocket's own column, so
   vanilla's following dismount read finds it in the cache. Keep the exact ticket-map and loaded-count assertion
   unchanged. Drop the capture-time prime if the override replaces it. Repeat the same cohorts after the change and
   report both sets of probe results. No assertion may be widened (option (b) is not chosen).
2. **W242-R5.** Use `getChunkNow` (never waits) for the column checks, the window reads and the prime; a column not
   yet FULL counts as unavailable and fails closed.
3. **W242-R3.** Wrap the trusted detaches in `SafeCelestialTravel` (line 83) and `EscrowGovernedPhases` (line 159) in
   `RocketRuntime.internalPassengerMove`, as W230 section 5 asks, with a test that a trusted move from a bay-held
   rocket creates no exit row.
4. **W242-R6.** Every selector status other than SAFE and NO_SAFE_EXIT grants nothing and has its own message key;
   a refused live slot and a refused permit send a message (W230 section 4.3's `EXIT_CAPACITY` and
   `EXIT_AUTHORITY_UNAVAILABLE`); an exhausted budget no longer says "No safe position found". English and Chinese.
5. **W242-R8.** The fallback without the exit service returns `rocket.position().add(1.5, 0, 0)`, the old position.
6. **W242-R9 and R10.** An `@AfterBatch` cleanup for the bay-exit cases (so a timed-out case leaves no forced column,
   player, listener, transfer record or claim), a guarded `...ForTests` hook instead of `setAccessible` on
   `BayTransferRecovery.queue`, the two Chinese strings, and no "Flight request accepted" line beside the exit
   message after a held menu LEAVE.
7. **W242-R4 is decided:** in S2 the mod's own blocks stay UNKNOWN contact (fail closed). A reviewed table of the mod's
   full-cube structural blocks belongs to a later slice. Say so in the record.
8. **W242-R7.** Write a new dated risk draft beside `RISK-DRAFTS-R264-FINAL.md` that adds the review's five residuals;
   Root adopts the text into the risk register when W242 merges.

W242-R2 (the local unfiltered run's double-chest timeout) is not in this task; [W251](TASK-W251-CHEST-TEST-POSITION.md)
settles it.

## Verification

- New unit tests for items 3 to 5, failing first where they can; the bay-exit unit classes and the whole unit suite.
- The repeated focused bay-exit cohorts of item 1, under the heavy-JVM lock, plus the batches of any test that item 3
  touches. No full GameTest run is needed.
- The repository validator.

Commit on the branch; do not push. Root pushes for a sharded run, and a fresh session of the other engine rechecks.

## Not in this task

W242-R2, W230-S3, bay admission, status files, the ledger and the risk register itself.
