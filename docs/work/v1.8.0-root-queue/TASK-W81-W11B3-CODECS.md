# TASK W81: W11b3 leaf B3-M, the pure part (loader codecs and fill arithmetic)

Root: Claude. Date: 2026-10-16. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w81-w11b3-codecs` (from the shared branch) |
| Branch | `codex/v1.8.0-w81-w11b3-codecs` (local only; Root merges) |
| Design | [records/W11B3-DESIGN.md](records/W11B3-DESIGN.md), revision 2 (frozen): sections 3 and 4, and the B3-M row of the leaf table in section 5 |
| Record | `docs/work/v1.8.0-root-queue/records/W81.md` (new) |

## Why now

W11b3's leaves B3-U and B3-E wait for W11b2 to merge. The pure codecs and arithmetic of B3-M do not, so
building them now shortens W11b3. This task builds only that part, on the shared branch.

## What to build (pure Java, no registration, no block or item)

From the B3-M row and sections 3 and 4 of the design:

1. The bounded codecs, each with its schema version and the size, depth and node limits the design states:
   the loader's tank root, the rocket binding, the linker selection, the drop carrier, and the loader's escrow
   section (one marker, 256 bytes).
2. Bank selection: filter by propulsion role, current catalog, complete native Fluid compatibility and positive
   room; one eligible bank selects it, more than one gives `AMBIGUOUS_BANK`, none distinguishes incompatible
   from full. Never overwrite a nonempty bank of another tag or abstract fuel.
3. The refill arithmetic: `min(1,000, local mB, eligible bank room)` in integer mB, at most once per device per
   20 ticks, with no catch-up burst.
4. `FluidFuelLoaderStatus` with every member the design lists, in a fixed order, and its translation keys
   (the text itself comes with the data leaf).
5. The `arce-escrow-fluid-tank-1:` signature, **if** every type it needs exists on the shared branch.

Put the code in a package the design names, or in `rocket/fuel/loader` if it names none, as pure classes that
the later block entity and `prepareFill` will call.

**Leave out**, and say so in the record:
- `EscrowCoordinator.prepareFill`, the R6 headroom and R8 charging tests, and the retired-plus-live marker
  tests: they use reservation code that exists only on the W11b2 branch (`codex/v1.8.0-w11b2-rocket-integration`),
  which merges later.
- Anything else that needs a type found only on the W11b2 branch. Name each such piece for B3-E.

## Tests (A0, from the B3-M row)

- Codec boundaries, round trips, and future and unknown versions and sizes for the tank, binding, linker
  selection and carrier; the escrow section with one marker, at 256 bytes, and future and unknown versions;
  a pin of the signature if you built it.
- The unique bank; exactly 1, 749 and 1,000 mB (for 1,251 mB in a 2,000 mB bank and a 1,000 mB input: move 749,
  keep 251); full, incompatible and ambiguous cases; native tag preservation; integer overflow.
- Run each new class alone, then the whole unit suite.

## Commands

- The whole unit suite once at the end. No GameTest is needed: nothing is registered.
- `git diff --check`; commit to the branch; do not push.

## Not in this task

- Blocks, block entities, menus, screens, items, registrations, data generation, art and text (the next leaves).
- `prepareFill`, the authority move, production admission and anything on the W11b2 branch.
- ADRs, the risk register, status files and the ledger (Root).
