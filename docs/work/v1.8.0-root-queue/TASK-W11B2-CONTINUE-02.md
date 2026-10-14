# TASK W11b2, continuation 02: finish the rocket integration

Root: Claude. Date: 2026-10-13. Read [WORKER-RULES](WORKER-RULES.md) and
[TASK-W11B2](TASK-W11B2-ROCKET-INTEGRATION.md) first. You are a new session. Your context is
the W11b2 record, [records/W11B2.md](records/W11B2.md) (at `c3501f92`). Read it in full,
including its Continuation 01 section.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w11b2-rocket-integration` (continue on top of `c3501f92`) |
| Branch | `codex/v1.8.0-w11b2-rocket-integration` (local only; Root merges) |
| Record | Add a "Continuation 02" section to `records/W11B2.md`. Keep the existing text |

## Finish the items that are still incomplete in the record

All of them fall under the original task's contract and the design review's R1 to R6:
1. **Real-rocket reconciliation.** Close "credited-world recovery remains unsafe", or state
   exactly why it stays unsafe and what blocks W11b3.
2. **Operator settlement and tombstones.** The durable settlement for `accept-current` and
   `abandon`. Never release an unproved credit, and test forced stops before and after each
   acknowledgement.
3. **Persisted relocation repair.** `/tp`, dimension travel and `/ride` are refused, or
   detected and turned into a repair transition.
4. **Native S1 and S2 cuts.** Run the cuts the design lists, on the final source, through the
   existing harness, with source and jar pins.
5. **Both journal benchmarks.**

## W11b1F

W11b1F is fixing four latent defects in the W11b1 core on branch
`codex/v1.8.0-w11b1-escrow-core`. Check that branch's `records/W11B1.md`:
- **It has a "Review fixes (W11b1F)" section.** Merge that branch into this branch **before
  item 4**, so the native cuts run on fixed code. Then adapt.
- **It does not have the section yet.** Do items 1 to 3 first, and check again before
  item 4.

Do not fix the W11b1 items yourself.

## Cleanup

The tool policy rejected your earlier recursive cleanup command. Root removed the generated
test world. Use plain commands that end by themselves. If a deletion is rejected, record it
and continue.

## Commands

- The whole unit suite once.
- `runGameTestServer` under the lock, then the log checker.
- The native cuts in item 4, with evidence under 100 MB.

## Not in this task

- `fluid_fuel_loader`, the linker and production admission (W11b3).
- ADRs and the risk register (Root).
- Status files and the ledger.
