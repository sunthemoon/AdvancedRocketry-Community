# v1.2.0 port core verification

Date: 2026-09-06

Branch: `codex/v1.2.0-port-core`

Scope: `V120-PORT-01`, partial `V120-PORT-02`

## Verified behavior

- Immutable definitions bound port count, filter IDs and slot/tank ranges.
- Mode and process-lock decisions are explicit for Item, Fluid and Energy ports.
- Controller-local sides transform to world directions without loading or reading a
  world.
- Shared revision state advances on successful external Energy mutation and not on
  simulation or rejected access.
- Item, Fluid and Energy Forge wrappers compile against Forge 47.4.10.

## Remaining A1 verification

Ordinary JUnit cannot instantiate `ItemStack` or `FluidStack` without the Minecraft
registry bootstrap. Item/Fluid range/filter/revision behavior and cached
`LazyOptional` invalidation therefore remain explicitly unverified until a short Forge
GameTest runs in an initialized game environment. This is slice-local verification and
is not part of the full acceptance campaign deferred by ADR-018.

## Commands and results

See [test-summary.txt](test-summary.txt). Failed exploratory JUnit bootstrap attempts
were corrected by moving registry-dependent acceptance to A1 rather than weakening the
runtime wrappers.

## Evidence integrity

Hashes are recorded in [checksums.txt](checksums.txt).
