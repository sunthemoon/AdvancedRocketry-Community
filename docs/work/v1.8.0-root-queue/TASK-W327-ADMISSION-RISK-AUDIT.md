# TASK W327: read-only audit of R-042, R-043 and R-044 for the five loader switches

Root: Claude. Date: 2026-11-09. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session and an auditor: review
only; do not edit, commit or push anything in any checkout.

| Item | Value |
|---|---|
| Checkout | `D:/GitHub/arce-v180-w327r`, detached at `70dd5d43e` (the code of candidate C1), read-only |
| Authority | [OWNER-DECISIONS-20261109](records/OWNER-DECISIONS-20261109.md): the owner's three checks, and Root decision R389 |
| Sources | `docs/11-RISK-REGISTER.md` (R-035, R-040, R-041, R-042, R-043, R-044, and R-038 and R-039 where they apply), ADR-065, [W27A-DESIGN](records/W27A-DESIGN.md), [W11CD-DESIGN](records/W11CD-DESIGN.md), [W230-DESIGN](records/W230-DESIGN.md), W231, W238, [W176](records/W176.md), and the code under `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/` (`resourceescrow/` and the bay, receipt, registry and journal code it uses) |
| Your output | `D:/GitHub/ARCE-Task-Evidence/v18-integrated-20261109/risk/AUDIT.md`, plus any notes in that folder |

## The five switches

The satellite bay's deployment admission (`block_variant:loader/1`) and the four cargo kinds `ITEM_CARGO_LOAD`
(`loader/3`), `ITEM_CARGO_UNLOAD` (`loader/2`), `FLUID_CARGO_LOAD` (`loader/5`) and `FLUID_CARGO_UNLOAD` (`loader/4`).
The escrow fuel loader is handled separately and is out of scope, except where a cargo or bay risk is shared with it.

## What to answer

1. **R-042: effects beyond one rocket.** For every R-042 condition, say whether it affects only the operation, the
   rocket, a chunk, a Level, other players or the whole server, for how long (one tick, until a repair, until restart,
   until an operator acts), what the logs show, and how it is recovered. In particular: the NOT_LOADED lock (does it
   lock every rocket on the server for the session, and what triggers it?), the shared pending pool (64 per server, 8
   per rocket, 16 per owner) and what holds it after landing, unbounded repair or registry waits, and an unreadable
   transaction journal. Cite the code (file and line at `70dd5d43e`) for each answer; mark anything you could not
   confirm in code.
2. **R-043: waiting times.** List every wait in the bay's D3 to D7 and recovery: what it waits for, whether it has an
   upper bound, and whether its stated duration (for example the D6 queue of about 36 minutes for the last of 64
   worst-sized claims) is a bound proved by code, an estimate, or a measurement, with its source. Say which waits a
   native run could measure, and how, without production instrumentation.
3. **R-044: who it belongs to.** R-044 covers loader save protection and downgrade. Say exactly which devices and
   switches its conditions apply to: the fuel loader, each cargo device, the bay, or every guarded chunk. Do the three
   failed downgrade rows of W176 involve cargo or bay data, or only the fuel loader? Where a condition belongs to
   another register entry (R-038, R-039, R-041), or is missing from all of them, say so.
4. **A draft per-switch table.** For each of the five switches, list the register entries and named residuals that
   apply, with scope, duration and recovery, and the evidence each needs before the owner decides (native cuts, which
   W325 and W326 run; A1 GameTests; and V1, V2 or the reference node, which wait for the final campaign). Keep the five
   columns separate even where they are identical.

## Findings

Rate each discrepancy between the register, the ADR text and the code as High, Medium or Low, with the exact location.
A register statement that understates an effect is at least Medium. End with the verdict line `AUDIT COMPLETE` and the
number of findings at each level.

## Not in this task

Editing the register, ADRs, the status file or code (Root does that from your report); the fuel loader's own risks;
running native servers or GameTests (W325 and W326 run them); real clients and the reference node.
