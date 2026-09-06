# v1.2.0 port core verification

Date: 2026-09-06

Branch: `codex/v1.2.0-port-core`

Scope: `V120-PORT-01`, `V120-PORT-02`, `V120-PORT-03`

## Verified behavior

- Immutable definitions bound port count, filter IDs and slot/tank ranges.
- Mode and process-lock decisions are explicit for Item, Fluid and Energy ports.
- Controller-local sides transform to world directions without loading or reading a
  world.
- Shared revision state advances on successful external Energy mutation and not on
  simulation or rejected access.
- Item, Fluid and Energy Forge wrappers compile against Forge 47.4.10.
- Three initialized Forge GameTests verify Item range/filter/lock/revision, Fluid tank
  isolation/simulation/revision, and cached `LazyOptional` reuse/invalidation.

## Verification boundary

Ordinary JUnit cannot instantiate `ItemStack` or `FluidStack` without the Minecraft
registry bootstrap, so those checks run as A1 Forge GameTests. Actual machine
BlockEntity integration, side exposure and revision persistence remain future
`V120-MCH`/`V120-MIG` work. The complete acceptance campaign remains deferred by
ADR-018.

## Commands and results

See [test-summary.txt](test-summary.txt). Registry-dependent acceptance runs at A1
rather than weakening the runtime wrappers for ordinary JUnit.

## Evidence integrity

Hashes are recorded in [checksums.txt](checksums.txt).
