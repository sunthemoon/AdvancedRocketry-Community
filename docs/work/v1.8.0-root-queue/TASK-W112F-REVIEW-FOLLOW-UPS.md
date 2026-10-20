# TASK W112F: W112's review follow-ups before merge (Root decision R38)

Root: Claude. Date: 2026-10-20. Read [WORKER-RULES](WORKER-RULES.md), [TASK-W112](TASK-W112-CARGO-C-P2-RESUMABLE-PREPARATION.md),
the branch's `records/W112-PLAN.md` and `records/W112.md`, and the review
`D:/GitHub/ARCE-Task-Evidence/root-queue/W112-review/REVIEW.md` (SHA-256 `cb2217f3…eae4`) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w112` (existing; branch `codex/v1.8.0-w112` at `160b5971`, clean) |
| Record | add a "W112F" section to `docs/work/v1.8.0-root-queue/records/W112.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W112F/` |

The independent review answered APPROVE-WITH-CHANGES: hashes, codec witness, budget accounting and publication hold,
but the lock and the cursor's lifetime have gaps. Fix each item in its own commit with a test that fails before the fix
where a test can show it.

## Root decision R38 (2026-10-20)

1. **R1, a real unload.** Discard the cursor and release the lock on the vanilla unload path (`onRemovedFromWorld`, or
   `RocketEscrowInterlocks.left` through `cargoRemoved`), and make `discardFinished()` close any active ticket that is
   not `valid()` (this also covers dimension changes and teleports). Test with `setRemoved(UNLOADED_TO_CHUNK)` or a real
   chunk unload, and check that a reloaded entity with the same logical UUID is not locked.
2. **R2, terminal successors.** Both: `begin` refuses a rocket that is not `RocketEscrowGate.idle` (design 2.2's
   receipt-backed refusal), and the gate's `resourcesIdle` (or `RocketTransferService.resourceMutationAllowed`) also
   requires no cursor, as a second line. GameTest on the terminal-lease fixture: `begin` refuses, and with a cursor
   forced on an idle rocket, disassembly, refuel and launch refuse.
3. **R3, a ticket's lifetime.** A ticket that has made no progress for **40 server ticks**, or that is READY and has not
   been published for 40 server ticks, is closed and its lock released, with one INFO line naming the logical rocket,
   the ticket's stage and the reason (at most one line per closed ticket). Move `prepared.discard()` in `prepareCargo`
   into a `finally`. Test that an abandoned ticket stops locking after the limit and not before. The prior snapshot
   stays authoritative throughout; this discards only RAM state.
4. **R4.** Add A1 cases for `nativeCapture` on the fixture rocket (ELIGIBLE and STALE) and for a publication through
   `beginReplay`.
5. **R5.** Map staged preparation failures exactly as `EscrowCargoEligibility.check` does (`CARGO_TOO_LARGE`,
   `INVALID_DATA`), throw `RocketSnapshotException(BLOCK_ENTITY_DATA_TOO_LARGE)` for the payload bound, close the cursor
   on failure, and add the 1 MiB boundary to the staged tests.
6. **R6.** Guard `cargo.close()` in `crashStop` and `invalidate` like `fills`, so an off-thread crash stop never skips
   `settlement`, `barrier`, `data` or `bay`.
7. **R7.** Cursors are reachable only through `Ticket` (no public `RocketCargoPreparation` constructor for production
   callers), and the work budget is the runtime's session budget, not a parameter.
8. **R8.** Move the codec witness (`verifies`, `canonicalEncoding`) into a small companion class with a
   package-visible decode hook, so `RocketSnapshotNbtCodec` is back under 500 lines.
9. **R9.** Correct the record's three overstated sentences.

No hash value, saved format, bound, proof, lease or replay exclusion changes; no existing assertion, limit or timeout is
relaxed; stop and report if one seems needed.

## Verification

Each changed test class alone; the whole unit suite; one full GameTest with the unchanged log checker on committed
source, under the heavy-JVM lock (W111 may hold it; wait). Release the lock and delete `build/gametest/world` in the
`finally` of the same detached script that took the lock (WORKER-RULES). Check C: and D: above 10 GiB first. Memory is
tight: run one Gradle process at a time. Not in this task: C-E, devices, ADRs, the risk register, status files and the
ledger (Root).
