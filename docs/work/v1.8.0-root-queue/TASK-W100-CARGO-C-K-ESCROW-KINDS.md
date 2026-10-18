# TASK W100: cargo leaf C-K, the cargo kinds and the rocket-side accessor in the escrow core (W11c)

Root: Claude. Date: 2026-10-18. Read [WORKER-RULES](WORKER-RULES.md) and [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md)
(frozen at `49f7e08b`; sections 3.1 to 3.3, section 6's C-K row and the open questions, whose recommendations Root
accepted when it froze the design) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w100` (new; from the shared branch at `38b437a7`) |
| Branch | `codex/v1.8.0-w100` (local commits only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W100.md` (new) |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W100/` |

## What to do

1. **Recheck sections 3.1 to 3.3 against `38b437a7`** (the design cites `646bf9ee`; W11b2's continuations, W93 and W95
   with W95F have since changed the escrow core). Note moved sites; stop and report if a difference changes the
   design.
2. **C-K's scope as the row and section 3.2 items 1 to 4 give it:** the kinds `FLUID_CARGO_LOAD` and
   `FLUID_CARGO_UNLOAD` (and the reserved item names, unused until D-K); the fluid cargo cursor; the
   direction-independent rocket-side accessor used by the binding, the floors, the guard checks and the capacity
   preview; composite bindings and floors; the endpoint unit `TANK`; one live cargo operation per rocket (Q4) with the
   mirror refusal in W11b3's fill admission (`ROCKET_BUSY`) and the bounded RAM turn token for
   `OTHER_TRANSFER_PENDING`; `EscrowCoordinator.prepareTransfer`, with `prepareFill` becoming a call to it and W11b3's
   tests unchanged; the marker-section binding and the five-marker preview for native rocket endpoints of either
   kind; and settlement's admission of cargo members where it accepts fills only, keeping section 3.3's
   representation (O1 to O4, receipts and the closed dictionary unchanged, O4 through the rocket-side accessor).
3. **The size report** of section 3.3 ("Size measurement prerequisite") with the real encoders, both directions,
   including the five-marker verdict; a candidate that does not fit is refused before any side effect, without
   raising a bound. Report it in the record.
4. **Tests (A0)** as the C-K row lists them: codecs, direction, typed projections, the frozen O4 derivation without
   live inputs, normalization and pruning, ACCEPT_CURRENT and both ABANDON survivors, unchanged fuel and floor-retire
   receipt bytes and inspector output, the turn token's lifetime, same-capture markers, and the R6, R8 and R9 tests
   with cargo. Run each new class alone, the whole unit suite, and one full GameTest regression with the unchanged log
   checker, all on committed source and serially.

Not in this leaf (section 6 assigns them later): the tank host and the permit-gated apply (C-P), the devices (C-A), the
fair queue, the readback proofs, production and restart reconciliation and the per-kind gate (C-E), data and art
(C-D), and every S2, V and B leaf. Cargo admission stays unreachable in production.

## Write scope

The escrow core classes that section 3.2 items 1 to 4 and section 3.3 name, new tests, and the record. The escrow root
is unpublished (R-035: production admission is disabled), so adding operation kinds needs no migration; say so in the
record. No change to the fuel path's behaviour, the receipt dictionary, a proof's meaning, a lease or the replay
exclusion, and no relaxed assertion, limit or timeout. Stop and report if one seems needed.

## Commands and not in this task

As WORKER-RULES says. Other workers (W97, W98, W99) also run tests; take the heavy-JVM lock for every GameTest and
release it between runs when another worker waits. Check that C: and D: have more than 10 GiB free before each heavy
start. When your heavy runs end, remove your own lock and your `build/gametest/world` with a plain `rm -rf` after
checking that the lock's `owner.txt` names you; if the tool refuses, write the paths in the record and final answer at
once.

Not in this task: ADRs, the risk register (Root records the cargo protection scope before C-P), status files and the
ledger (Root).
