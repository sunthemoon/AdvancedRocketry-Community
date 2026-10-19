# TASK W98F: W98's review follow-ups before merge (P1-N3 leaf N3-A1)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W98](TASK-W98-N3-A1-SOURCE-RETURN.md) (Root
decision R34), `records/W98.md` on the W98 branch, and the review `D:/GitHub/ARCE-Task-Evidence/root-queue/W98-review/REVIEW.md`
(SHA-256 `493187a77ccc393c585203f7af42176f4ac14716ff393cec137acae7e086042b`, verdict APPROVE-WITH-CHANGES) first. You are
a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w98` (W98's; its worker has ended and the tree is clean at `4c0d034d`) |
| Branch | `codex/v1.8.0-w98` (continue it with local commits only; Root merges) |
| Record | Append a section "W98F" to `docs/work/v1.8.0-root-queue/records/W98.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W98F/` |

## Root decision R35 (recorded here, 2026-10-20)

1. **W98-R2:** when an escrow ledger exists but is not operational (`ResourceEscrowSavedData.existing(server)` is
   non-null and not `operational()`), the barrier never ticks, so a proof can never complete. The ordinary coordinator
   then uses R5(c)'s ordering for that world: it removes T after the in-memory return, with one WARN per world per
   session naming the reason, and starts no proof. An attempt already in flight when the ledger stops being operational
   ends at its deadline and then follows the same fallback. Holding every ordinary return in the world would be worse,
   for the reason R5(c) gives. `EscrowRuntime` is not changed.
2. **W98-R1** (an ordinary capture can invalidate a governed proof of the same entity slot) and **W98-R3, R6, R10 and
   R12** are not fixed here. Root records W98-R1 as a named R-035 residual until governed admission opens; the others go
   to the later leaf N3-A2.

## What to do, each in its own commit with a test where one is practical

1. **W98-R4:** move the ordinary coordinator's wiring line in `RocketTransferService` after `governedCleanup.tick`, so
   governed dispatch and cleanup take the tick's capture first.
2. **W98-R2** under R35 above.
3. **W98-R5:** before an attempt's first capture, release the token and wait one tick when `availableOrdinary` is false
   (keep the fail-back's first-tick deferral); count token-carrying entries in `attempting()`. A wait must never keep a
   journal reservation (R34's R1).
4. **W98-R9:** check `noSave()` before each `beginOrdinary`, not only at the attempt's start, and fail with `SAVE_OFF`.
5. **W98-R11:** for ordinary proofs, treat only the exact vanilla `EntityStorage` class or the installed decorator as
   supported; anything else uses R5(c).
6. **W98-R7:** a census slot past `MAX_SLOTS` holds the return with one WARN instead of being skipped.
7. **W98-R8:** run `effects.discarded()` for a nudge's unused effects, and make `recoverNext` skip any rocket the
   coordinator already holds (`retirements.pending(L)`).

## Tests and commands

- Each changed or new test class alone, then the whole unit suite once.
- GameTests under the heavy-JVM lock on a **committed** SHA with the batch filter
  (`-Dadvancedrocketrycommunity.gameTestBatches=...`, as W98's `run-gametest.ps1` does): `ordinary_source_retirement`,
  `landing_pads_cancel`, and the governed flight and escrow batches that the R4 change can reach (name them in the
  record). A new WARN that a GameTest drives needs its own manifest entry with an exact count and batch.
- `python -B -m unittest discover -s tests -p "test_*gametest_log*.py"` and
  `python -B scripts/validate_repository.py --require-approved-identity`.
- Check that C: and D: have more than 10 GiB free before each heavy start; other workers take the lock too, so release
  it at once after each run. Run long heavy jobs as detached processes, not as tool background jobs (the tool stops
  background jobs after two hours, which left a lock behind in W98).
- At the end, delete your export trees and `build/gametest/world`, and make sure the lock is released. If the tool
  refuses, record the exact paths; Root deletes them.

## Write scope and not in this task

`RocketTransferService` (the one wiring line), `RocketOrdinaryRetirement`, `RocketRetirementProof`,
`RocketTransferRecoveryService` (W98-R8 only), the smallest read-only check the R11 change needs in `EscrowChunkStore`,
their tests, the log expectation file for new lines, and the record. No change to a governed path, a proof's meaning, a
lease, the replay exclusion or `EscrowRuntime`, and no relaxed assertion, limit or timeout; stop and report if one seems
needed. Not in this task: W98-R1, R3, R6, R10 and R12 (N3-A2), N3-B, the Space sleep test (Root fixed it separately),
ADRs, the risk register, status files and the ledger (Root).
