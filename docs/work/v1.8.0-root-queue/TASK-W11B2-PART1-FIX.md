# TASK W11b2 part 1 fixes: legacy fuel, dismount and lock scope, writer hardening

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-part1-fix` (at `3c47fa07` plus this task) |
| Branch | `codex/v1.8.0-w11b2-part1-fix` (local only; Root merges) |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-review/REVIEW.md`, SHA-256 `8c1da51138a655581c8a3256841615fd556c627eaed46edcf421b4fcc67de499`. Verdict BLOCK on F1 |
| Record | `docs/work/v1.8.0-root-queue/records/W11B2-P1F.md` (new) |

Root merges `0638009f..3c47fa07` as W11b2 part 1 only together with these fixes. Read the whole
review and the "Continuation 05" section of `records/W11B2.md`. This slice touches rocket and
journal persistence, so write a short design section at the top of your record **before** the
code. Root will have the result reviewed independently. Give each finding its own commit, with its
tests.

## First: merge the shared branch

Merge `codex/v1.8.0-classic-content` (`78c5f7dd` or later) into this branch before any change. It
brings W33, W34, W36 and W38. Keep every assertion. Commit the merge on its own. Do not rewrite
commits up to `3c47fa07`.

## Fixes (the whole scope)

1. **F1, critical: legacy scalar fuel.**
   - `RocketEntity.updateFlightData` and every other caller of `resourceSignature()` must not throw
     when either side has legacy fuel (no native propulsion vector).
   - For legacy fuel compare capacity, amount and debits. Legacy fuel never carries an escrow
     marker; say in the record why that keeps the guard's purpose.
   - Regression GameTests with a real legacy scalar-fuel rocket (decoded from a schema-1 or
     pre-propulsion compound, not built native): it boards and leaves, refuels from an INPUT fuel
     loader, launches and completes a flight, and recovers KEEP_SOURCE.
   - Correct the record: the guard came from `844a1836`, so it is not pre-existing.
   - A native cut on a world saved before native propulsion is welcome but optional. If you skip
     it, say so.
2. **F2, medium: the interlocks.**
   - Never cancel a dismount (`EntityMountEvent` with `isDismounting()`).
   - Mount, teleport, spreadplayers, dimension travel, `kill` and live `/data` interlocks consult only
     ledger protection: records, markers, tombstones or a bound section. A world with no ledger must
     keep the base behaviour for them.
   - Board, leave, refuelling and launch may still wait for the rocket's own journal record to be
     acknowledged where R1 needs that. They must not be locked by another transfer's write window.
     State in the design section exactly which operations still wait, on what, and why R1 holds.
   - A blocked journal keeps the base behaviour: launch and resource mutation are refused. Do not
     freeze mounting or dismounting.
   - Tests: dismount from a landed rocket during another transfer's unacknowledged write; board
     and refuel a landed rocket in a world with no ledger while another transfer writes; the blocked
     journal case.
3. **F5, low.** Force the directory after the atomic move in `RocketJournalCheckpoint`, as
   `EscrowLedgerWriter` does.
4. **F6, low.** `RocketJournalWriter` catches `Throwable` on the writer thread and publishes UNKNOWN,
   so `running` clears and `drain` cannot loop forever. Unit-test it with an injected `Error`.
5. **F7, low.** Finish the request-ledger entry when a JOURNAL_PENDING continuation is dropped (stop,
   identity change, `flightAllowed` refusal, a throw), so ACTIVE entries cannot leak. Unit-test the
   eviction.

Write F3, F8, F9 and F10 into the record as open follow-ups with one sentence each. Root carries
them into the risk register.

## Commands

- The whole unit suite once.
- `runData` under the lock, only if language strings change.
- `runGameTestServer` under the lock, then the log checker.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- O2 to O4 and the O-veto (continuation 07 on the W11b2 branch).
- F3's per-record acknowledgement redesign, F8 and F9.
- Production admission, ADRs, the risk register, status files and the ledger (Root).
