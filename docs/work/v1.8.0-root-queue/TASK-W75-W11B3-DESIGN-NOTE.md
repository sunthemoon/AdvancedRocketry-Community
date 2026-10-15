# TASK W75: design note for W11b3 (fluid fuel loader, the linker's rocket-binding half, production admission)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. This is design work
only: write no production or test code.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w75-w11b3-design` (from the shared branch) |
| Branch | `codex/v1.8.0-w75-w11b3-design` (local only; Root merges) |
| Plan row | [records/W11A-DESIGN.md](records/W11A-DESIGN.md) section 8, the W11b3 row and the paragraph after the table |
| W11b2 state | Branch `codex/v1.8.0-w11b2-rocket-integration` at commit `d178e727` (not merged yet; continuation 16 is working in its worktree). Read it only through Git at that commit, for example `git show d178e727:<path>` and `git grep <pattern> d178e727`. Do not open, edit or build in `D:/GitHub/arce-v180-w11b2-rocket-integration` |
| W11b2 contract | At `d178e727`: `docs/work/v1.8.0-root-queue/records/W11B2-DESIGN.md`, `records/W11B2-DESIGN-2.md` (frozen) and `records/W11B2.md` (what is built and what is still open) |
| Deliverable | `docs/work/v1.8.0-root-queue/records/W11B3-DESIGN.md` (three to five pages) and a short record `records/W75.md` |

## Why

W11b3 is the next slice of the escrow chain after W11b2. It is the first slice that turns production
admission on. AGENTS.md section 13 requires a design note and one read-only review before any code of a
slice that touches saved data, admission or save refusal. Writing the note now, while W11b2 finishes,
lets W11b3 start as soon as W11b2 merges.

## What the note must settle, citing code and documents

1. **Scope against the ledger.** Name the ledger rows (`docs/work/v1.8.0-content-ledger.csv`) that
   W11b3 delivers, and the ones it leaves to W11c, W11d, W11e and the bay slices. Say what
   `fluid_fuel_loader` is in relation to the existing fuel loader (`FuelLoaderBlockEntity`, ADR-027) and
   to `block_variant:loader/5`.
2. **Production admission.**
   - The exact condition that turns admission on, from W11A-DESIGN section 8: the required D3-A S2 cuts
     pass on the integrated source. List those cuts.
   - Which device admits, through which W11b2 entry point, with which preflight and reservation (include
     Root's reservation decisions R1 to R4 in
     `D:/GitHub/ARCE-Task-Evidence/root-queue/W11b2-C09-review/ROOT-DECISIONS.md`).
   - How a refused admission looks to the player (status member, text), with no fuel moved.
3. **The items that ADR-065 says are open before W11b3 or any admission:** F3 (per-record scheduling),
   F8, F9, N1 and N2 (ADR-065, the paragraph "Open before W11b3 or any admission"). For each one, say
   whether W11b2 at `d178e727` already closes it (cite the commit and test), or how W11b3 closes it.
4. **W13 N1** (ADR-065, the W13 known-limitation paragraph): decide between the two fixes named there,
   or say why neither is needed for W11b3.
5. **The linker's rocket-binding half:** what it binds, where the binding is stored (schema version and
   size limit), what the server checks on each C2S packet (AGENTS.md section 3.3), and how a stale
   binding is refused.
6. **The device:** block, block entity, menu, capabilities, loot, recipe, translations and art. For the
   art and any upstream file, say where it comes from and which provenance row it needs
   (`docs/08-ASSET-LICENSE-AND-PROVENANCE.md`); copy nothing in this task.
7. **W02 F3's refill**, as the plan row's paragraph describes it, and the per-device limits stated
   there (16,000 mB tank, 8 KiB native bound, 1,000 mB per device per 20 ticks, AMBIGUOUS_BANK).
8. **One admission authority** shared with the satellite bay (TASK-W27A2 and the W27b merge): how the
   loader and the bay use the same authority, with the mixed-store tests.
9. **Tests and evidence:** the A0 and A1 tests, the S2 cuts with the production loader and two
   competing devices, the old-world restart, S1 sidedness, and the benchmarks. Mark which need the
   heavy-JVM lock.
10. **Risk and ADR text:** the R-035 changes and the ADR-065 note text that Root will add at merge.
    Write them as proposals in the note.
11. **Open questions for Root or the owner**, each with a recommended answer.

## Commands

- No Gradle, GameTest or JVM run is needed. `git show`, `git grep` and reading files are enough.
- `python -B scripts/validate_repository.py --require-approved-identity` once at the end, if your
  record links to files.

## Not in this task

- Any production or test code, and any asset copy.
- W11c, W11d, W11e, the gas charge pad (owner decision D4) and the bay slices W27c1 to W27e.
- ADRs, the risk register, status files and the ledger (Root).
