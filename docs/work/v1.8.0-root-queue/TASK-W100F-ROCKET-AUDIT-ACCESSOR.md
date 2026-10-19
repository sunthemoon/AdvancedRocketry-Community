# TASK W100F: the loaded rocket audit must use the rocket-side endpoint (W100-R1)

Root: Claude. Date: 2026-10-19. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session. This follows
[TASK-W100](TASK-W100-CARGO-C-K-ESCROW-KINDS.md); its independent review returned FIX-FIRST for one finding.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w100` (existing, clean, branch `codex/v1.8.0-w100` at `d9871ede`); commit on top of it |
| Review | `D:/GitHub/ARCE-Task-Evidence/root-queue/W100-review/REVIEW.md`, finding W100-R1 (read it first) |
| Design (frozen) | [records/W11CD-DESIGN.md](records/W11CD-DESIGN.md) section 3.2 item 1 (the rocket-side accessor) |
| Record | append a "W100F" section to `docs/work/v1.8.0-root-queue/records/W100.md` |
| Evidence | `D:/GitHub/ARCE-Task-Evidence/root-queue/W100F/` |

## The finding

`RocketEscrowAudit.observe` selects a record by its guard's logical rocket, then checks placement and invalidates the
monitor with `op.destination()`. For `FLUID_CARGO_UNLOAD` the rocket is the source and the destination is the block
device, so a correctly placed source rocket yields `INVALID_ENDPOINT`, which `observe` persists as a sticky REPAIR.
`RocketEscrowAudit.attempt` looks up `op.destination().identity()` as an entity, so it never audits the source rocket.
Production cargo admission is still off, so this is latent, but C-K's accessor conversion is not complete without it.

## What to do

1. In both methods, use one `op.rocket()` reference for the identity selection, the placement check, the monitor
   invalidation and the entity lookup, as section 3.2 item 1 says. Keep fuel fills' behaviour byte for byte (for a fill
   the rocket is the destination, so `op.rocket()` equals `op.destination()`).
2. Search the rest of the escrow package for any other call site that still assumes the rocket is the destination
   (W100 converted bindings, floors, guard checks, the capacity preview and O4; the review found only these two). Fix
   any you find the same way and list each in the record; if there is none, say how you searched.
3. Tests: load and unload audit coverage for both methods, including a correctly placed rocket (no transition), a
   wrong or relocated identity (the existing reason), and a manual or settling record (still sticky). The new tests
   must fail on `d9871ede`; show that with a temporary revert you do not commit. Rerun the six cargo test classes, the
   whole `*resourceescrow*` package and the whole unit suite once, on committed source. No GameTest is needed unless
   you change GameTest code.

W100-R2 (the busy message) is not in this task; it goes to the cargo localization work before enablement.

## Write scope

`RocketEscrowAudit` and any call site step 2 finds in `resourceescrow`, their tests, new tests, and the record. No
change to receipts, schemas, limits, the closed `Fill` enum, proof or lease code, admission enablement or the design.

## Commands and not in this task

As WORKER-RULES says. Other workers also run tests; take the heavy-JVM lock only if you run a GameTest. Remove your own
temporary files afterwards; if the tool refuses, write the paths in the record and final answer at once.

Not in this task: C-P, C-E, W100-R2, ADRs, the risk register, status files and the ledger (Root).
