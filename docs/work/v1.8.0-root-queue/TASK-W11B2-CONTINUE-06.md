# TASK W11b2, continuation 06: durable operator settlement and tombstones, then reconciliation

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session. Your context is the
W11b2 record, [records/W11B2.md](records/W11B2.md) (at `3c47fa07`). Read it in full, especially
"Exact consumer handoff and unfinished integration" in Continuation 05.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (continue on top of `3c47fa07`) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Record | Add a "Continuation 06" section to `records/W11B2.md`. Keep the existing text |

## Root's merge plan

- Root will have `0638009f..3c47fa07` reviewed independently and merge it as **W11b2 part 1**:
  - the hook;
  - the shared admission core;
  - the rocket integration;
  - production admission still disabled.
- Your work goes on top as **part 2**. Rules for it:
  - Do not rewrite or amend commits up to `3c47fa07`.
  - If the review asks for fixes to part 1, Root sends them as a separate task.
- The leftover 286 MB `build/gametest/world` from continuation 05 has been deleted by Root.

## 1. Durable operator settlement and tombstones (first)

Implement the settlement the record lists as unimplemented W11b2 work:
- **O1.** A preview-and-confirm digest with an actor audit, and a frozen connected-component
  intent.
- **O2.** A durable survivor or absence decision.
- **O3.** An acknowledged tombstone, and the matching journal removal.
- **O4.** An acknowledged release and floor retirement. Extend the operation representation so
  that it can carry the floor-retirement intent.
- **The O-veto**, for every choice and for connected chains.

Requirements:
- Never treat RAM absence as durable absence.
- Never drop a record so that an unproved returning identity could replay.
- Forced-stop cuts go before and after each acknowledgement. Each cut needs two restarts, pinned to
  the source and the jar.

Settlement is persistence and save-refusal work. Before the code, put a one-page design section in
the record. Root will have it reviewed together with the code.

## 2. Credited-world reconciliation (second)

Either implement the actual connected-prefix source and rocket adapter, with all of these:
- BEFORE and AFTER application with exact epochs and markers;
- missing, duplicate and orphan elections;
- joint durable endpoint proofs;
- acknowledged L2 and marker pruning.

Or, if this does not fit in this continuation, state exactly what remains, and keep the
scheduler from claiming a proved result. The second outcome is acceptable. Do not leave an unsafe
path that looks finished.

## 3. The native matrix (third, as far as time allows)

Run the remaining cases, each with two restarts, pinned to the source and the jar:
- R1 to R3;
- G1, with a genuine historical world and jar and both first-schema-save cuts;
- G2;
- the failed committers;
- collection and CF1;
- the repeated integrated K0 to K10 and C1 to C2.

Keep the evidence under 100 MB. Delete generated worlds at the end. A normal `Remove-Item` or `rm`
of your own `build/gametest/world` is allowed.

## Joint-proof flake

W36 fixed `jointTerrainSlotProofRetainsTheOtherEndpointLease` on its own branch. Root merges that
fix and applies the same one to the other barrier test. Do not change either test here. Report any
occurrence.

## Commands

- The whole unit suite once.
- `runData` under the lock.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts.

## Not in this task

- W11b3 and the bay slices W27b to W27e.
- Production admission.
- ADRs and the risk register (Root).
- Status files and the ledger.
