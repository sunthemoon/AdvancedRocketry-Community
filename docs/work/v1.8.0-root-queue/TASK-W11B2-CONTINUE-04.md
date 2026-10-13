# TASK W11b2, continuation 04: merge the shared branch, then finish the integration

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session. Your context is the
W11b2 record, [records/W11B2.md](records/W11B2.md) (at `5e0c7562`). Read it in full,
including the Continuation 01 to 03 sections.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (continue on top of `5e0c7562`) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges into the shared branch) |
| Record | Add a "Continuation 04" section to `records/W11B2.md`. Keep the existing text |

## 1. Merge the shared branch first (Root's instruction)

Merge `codex/v1.8.0-classic-content` (`58ca2269` or later) into this branch. It brings:
- W28: the GameTest scheduling guard (`GameTestSchedulingSourceTest`) and tick continuations
  (`GameTestTickDriver`);
- W29: per-slot stability in the Anvil reader;
- W32 and other slices.

Eight files conflict:
- `scripts/gametest_expected_log.json`;
- `V180LanguageProvider`;
- `MoonArrivalGameTests`;
- `RocketFlightGameTestFixtures`;
- `RocketPassengerContinuedUseGameTests`;
- `RocketPassengerPersistenceGameTests`;
- `RocketTransferRecoveryGameTests`;
- `TauCetiPathGameTests`.

Resolve them so that both of these hold:
- **Your semantics.** Your asynchronous journal waits and assertions keep their meaning.
- **W28's rule.** No GameTest scheduling from inside a scheduled callback. Use
  `GameTestTickDriver` and tick continuations, and keep `GameTestTickPacer` pacing.

Then check:
- `GameTestSchedulingSourceTest` must pass;
- every assertion from both sides is kept;
- for the log manifest, keep both sides' exact expectations. Re-check the pinned totals in
  `tests/test_check_gametest_log.py`;
- for the language provider, keep both sides' keys, then run `runData` under the lock.

Commit the merge on its own, with a message listing each conflict and how you resolved it.

## 2. Then finish the record's open items

- **Credited-rocket reconciliation.** Close "credited-world recovery remains unsafe", or state
  exactly why it stays unsafe.
- **Durable operator settlement and tombstones.** `accept-current` and `abandon` never release
  an unproved credit. Run forced-stop cuts before and after each acknowledgement.
- **The full native matrix:**
  - R1 to R3;
  - O1 to O4 and the O-veto;
  - G1 and G2;
  - the failed committers;
  - collection;
  - the repeated integrated K and C cuts.

  Each runs with two restarts, pinned to the source and jar.
- **Server-thread validation.** The record says candidate validation still encodes on the
  server thread. Measure it in the 8-transfer cycle. If it exceeds the docs/17 budgets, move it
  into the writer's capture or make it incremental. Never drop the validation.

W27a round 2, the satellite bay redesign, is being written now. It may name a hook it needs in
your recovery fences. Root will tell you in a later task. Do not add one speculatively.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts above, with evidence under 100 MB.

## Not in this task

- W11b3 (`fluid_fuel_loader`, the linker) and production admission.
- ADRs and the risk register (Root).
- Status files and the ledger.
