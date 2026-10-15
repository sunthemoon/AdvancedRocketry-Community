# TASK W79: design note for the cargo loaders and unloaders (W11c fluid, W11d items)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. This is design work only: write
no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w79-cargo-design` (from the shared branch) |
| Branch | `codex/v1.8.0-w79-cargo-design` (local only; Root merges) |
| Plan | [records/W11A-DESIGN.md](records/W11A-DESIGN.md) section 8 (the W11c and W11d lines after the table); ADR-065, the cargo paragraphs (search for "cargo": the native cargo carrier, disassembly restoring cargo exactly once, the BE payload host adapter, and the coordinator for block-entity to moving-rocket cargo) |
| Contract it builds on | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md), revision 2 (frozen): the production admission entry point, the runtime-owned admission authority, the device and binding contract and the S2 cut families. Root's reservation decisions R1 to R9 in `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md` |
| W11b2 state | Branch `codex/v1.8.0-w11b2-rocket-integration` (continuation 18 is working there). Read it only through Git at a commit you name, for example `git show <commit>:<path>`. Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W11CD-DESIGN.md` (four to six pages) and a short record `records/W79.md` |

## Why

After W11b3, the escrow chain continues with the cargo devices: W11c (ledger rows `block_variant:loader/4`
and `/5`, the rocket fluid unloader and loader) and W11d (`block_variant:loader/2` and `/3`, the rocket item
unloader and loader). Both move player resources between a block entity and a docked rocket's cargo, so they
need the same crash-safety as fuel, and AGENTS.md section 13 requires a design note and a read-only review
before code. Writing it now lets both start soon after W11b3.

## What the note must settle, citing code and documents

1. **Scope against the ledger:** the rows each slice delivers, what stays for W11e (the full linker and the
   monitoring station), and what the old AR 1.12 devices did that v1.8 does not do.
2. **What cargo is.** Where a docked rocket's cargo Fluid and items live today (the rocket snapshot's block
   entity payloads, up to 128, and the native cargo carrier), and how a cargo endpoint is captured and
   signed, the way W11b3 captures a propulsion bank. Propulsion banks are never drained as cargo.
3. **Snapshot cursor:** W11A section 8 names a "pressurized-tank adapter with snapshot cursor". Define it:
   which payload a device addresses, how it stays stable when the snapshot changes, and its persisted form
   (schema version and size, depth and node limits).
4. **Admission and the escrow:** how each transfer direction (block entity to rocket, rocket to block entity)
   uses W11b3's `prepareFill` path or a sibling, the shared authority, the reservations (R1 to R9 as in force
   when W11b2 merges), and the settlement of a cargo operation. Say what is reused and what is new.
5. **Disassembly and launch:** ADR-065 requires that disassembly restore the latest cargo exactly once and
   that unloading followed by materializing the original snapshot cannot recreate cargo. Show how each device
   keeps that true, including a crash between steps.
6. **The devices:** blocks, block entities, menus, capabilities, loot, recipes, translations and art for the
   four devices, with the same C2S, ownership, binding and carrier rules as W11b3's revision 2 (sections 3 and
   4). Say where each asset comes from and which provenance row it needs; copy nothing.
7. **Items:** for W11d, how item stacks with NBT are bounded, compared and signed, and how a partial transfer
   is handled.
8. **Tests and evidence:** A0 and A1 tests, the S2 cut families from W11b3's revision 2 that apply (and only
   those), S1, V1 and V2, and benchmarks, as leaves with a "builds" column and JVM-start estimates.
9. **Risk and ADR text:** proposed R-035 changes and ADR-065 note text for Root.
10. **Open questions** for Root or the owner, each with a recommended answer.

## Commands

- No Gradle, GameTest or JVM run. `git show`, `git grep` and reading files are enough.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end. Link only to files
  Git tracks; write other paths as code spans.

## Not in this task

- Any production or test code, and any asset copy.
- W11b3 itself, W11e, the satellite bay slices and the gas charge pad.
- ADRs, the risk register, status files and the ledger (Root).
