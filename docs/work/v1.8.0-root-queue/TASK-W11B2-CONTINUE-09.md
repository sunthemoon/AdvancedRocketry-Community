# TASK W11b2, continuation 09: implement design 2 (bound-rocket flights after release, settlement fixes)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (at `238f4b9a` plus this task) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Contract | [records/W11B2-DESIGN-2.md](records/W11B2-DESIGN-2.md), frozen at `238f4b9a`: Revision 1 closes the design-2 review (SHA-256 `658fdf2a…`), and Root re-read section 1 and section 5.4 |
| Record | Add a "Continuation 09" section to `records/W11B2.md`. Keep the existing text |

Implement design 2. The design is the contract. If the real code forces a change, write it with its
reason at the top of your section before coding around it. A change to an acknowledgement order, a
proof, a hold, or the replay exclusion needs Root first: stop and report.

## Order of work (commit each step with its tests)

1. **Merge the shared branch first** (`66be7b71` or later), in its own commit. It brings W37, W37b,
   W39 to W43 and W45 to W52. Keep every assertion. Regenerate the language files with `runData`
   under the lock.
2. **Settlement fixes.** Implement in place, with no migration chain, following design 2's M2 rule:
   - F3: the unbound chosen survivor;
   - F4: per-settlement marker pruning and the exact receipt the O4 callback prunes;
   - F5: unknown-loss accounting in receipts.

   Development data from before the change is refused fail-closed, and its raw file is preserved.
3. **ARMED and READY.**
   - The bounded `armed_flights` table (64 entries at most, 1 KiB each) and the `rocket_authorities`
     READY heads.
   - The server-issued serials, the replay exclusion, and the F1 source checkpoint (own-store, force,
     stable readback) before ARMED.
   - Section 1's boundary table for `RocketTransferFences.classify`, `RocketEscrowGate.idle`,
     `EscrowRecoveryFence.reason`, joins and callbacks, cancellation and failed landing.
   - The journal root stays at schema 2. Only governed records are schema 3.
4. **The flight order F0 to F8** (section 5.4), and the credited-world reconciliation order
   (section 5.3), with section 5.1's canonical signatures.
   - A missing credited source is REPAIR. Reconstruction is W27f's.
5. **The required test.** Strengthen `RocketSettlementIdleGameTests` exactly as section 1's "Required
   test" says:
   - two complete flights after settlement, and two complete flights after an ordinary L2 release;
   - no new log expectation for FLOOR_PROOF_REQUIRED, FLOOR_CONFLICT or ENDPOINT_JOIN_PROOF_REQUIRED.

   This closes continuation 08's N1. The log checker must pass unchanged.
6. **The A0 cases**, and as many of the section 5 S2 cuts as fit, two restarts each, pinned to the
   source and the jar. List what is left.
7. **F6 to F11.** Fold in section 6's dispositions, including the `inspect` and `retry` commands and
   the console-origin check.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock on a **committed** SHA, then the log checker.
- The native cuts, with evidence under 100 MB.
- At the end, delete your generated `build/gametest/world` and Temp copies with a plain `rm -rf`, and
  make sure the heavy-JVM lock is released. If the tool refuses, record the exact paths; Root deletes
  them.

## Not in this task

- Production admission, W11b3, the bay slices, W27f reconstruction, and signed per-fluid accounting.
- ADRs, the risk register, status files and the ledger (Root).

## Root decision (2026-10-15): native prefix identity across fuel banks

This answers the clarification in `records/W11B2.md`, "Continuation 09" (reproducer `a135d140`).

1. **The rule.** An endpoint's prefix identity follows the epoch scope of its escrow section.
   - A rocket section (`RocketEscrowSection`) has **one** epoch and **one** contiguous marker suffix
     for the whole physical rocket. Design 2 section 5.1 signs the whole `FUEL_VECTOR_V1` vector
     across both banks.
   - So the prefix identity of a rocket endpoint is the **physical rocket**, shared by PRIMARY and
     OXIDIZER.
   - `Unit` stays on each link as the operation's exact bank selector. It is no longer part of the
     rocket chain's identity.
2. **What follows for rocket endpoints:**
   - one contiguous chain over the rocket's epochs, whichever bank each link debits or fills;
   - classification compares the whole native-vector signature at each epoch;
   - frozen frontiers are per rocket;
   - partial retirement follows epoch order across banks;
   - settlement observations are deduplicated per rocket.
3. **Other endpoint kinds** whose sections keep a separate epoch per unit keep `Unit` in their
   identity. Before you change the equality:
   - list every endpoint kind and its section's epoch scope, with file and line;
   - scope the change to rocket endpoints, without changing `EscrowEndpoint` equality globally;
   - add a test that a block-entity endpoint with several units behaves exactly as before.
4. **Tests.** Your reproducer's two mixed-bank assertions (PRIMARY then OXIDIZER, and PRIMARY,
   OXIDIZER, PRIMARY) must pass, and its controls stay green.
   - Add a mixed-bank partial-retirement case.
   - Add a mixed-bank settlement-observation case.
   - Encode no ledger record differently, except where the M2 in-place schema rule already allows it.
     Say exactly what changes on disk, if anything.

Then continue the whole task in its order.
