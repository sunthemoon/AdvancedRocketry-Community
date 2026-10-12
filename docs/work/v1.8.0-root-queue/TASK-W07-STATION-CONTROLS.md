# TASK W07: station controls, orbital distance and orientation

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.
Implementer: a headless Claude session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w07-station-controls` |
| Branch | `codex/v1.8.0-w07-station-controls` (local only; Root merges) |
| Ledger plan | C17b (`warpMonitor`, `gravityController`, `altitudeController`, `orientationController`) |
| Record | `docs/work/v1.8.0-root-queue/records/W07.md` |

## Goal

A station owner can operate their station from physical controller blocks:
- warp through the existing checked warp service;
- set station gravity;
- set a logical orbital distance and three orientation rates or phases.

The sky shows the result. Nothing physically moves, and old stations and elevators keep
their behaviour.

## Read

- ADR-065 (`docs/decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md`):
  - section 5 in full: 5.1 screens, 5.2 D2-A (owner-confirmed), 5.2.1 the clock, 5.3
    localization;
  - section 2 (the C17b identities: `warp_controller`, `station_gravity_controller`,
    `station_altitude_controller`, `station_orientation_controller`);
  - section 11 (the exact protocol transitions and the message table).
- ADR-044 (station warp), ADR-046 (warp commands), ADR-059 (elevator), and the existing
  station services: the station manager and registry, `StationGravityService`, the warp
  command service, the station sky payload and its sender.

## Scope

1. **Design note first.** This slice migrates the station record and registry schemas
   and raises the celestial network protocol, so AGENTS.md section 13 applies. Your first
   commit is `docs/work/v1.8.0-root-queue/records/W07-DESIGN.md`, at most one page. It
   covers:
   - record schema 3 and registry schema 5;
   - the migration from record 2;
   - the clock;
   - the payload schema 2;
   - protocol 4;
   - the elevator guard.
2. **Controller blocks** (§5.1), each with a block, a block entity if needed, a menu and a
   screen:
   - `warp_controller` reuses the warp service's quote, request, confirm, cancel and status
     actions through a shared context validator. The actor must be within 8 blocks of the
     loaded controller in the same region, and a loaded warp core within 5 blocks of the
     controller.
   - `station_gravity_controller` fronts `StationGravityService` (0 to 100%, the same
     authority, cooldown and budget).
   - The altitude and orientation controllers make checked candidate writes, which compare
     the observed station revision before committing.
   - Commands keep working.
3. **D2-A** (§5.2):
   - distance from 4 to 190, initially 4;
   - three phases in milli-degrees, from 0 to 359,999;
   - signed rates from -60 to 60 turns per 72,000 ticks;
   - a rate change samples the phase into a new epoch;
   - modular integer phase arithmetic;
   - no movement of blocks, regions, pads, players or entities;
   - the warp cost, route distance and `orbit_body` are unchanged.
4. **Persistence:**
   - the `orbital_controls` compound in record schema 3, and registry schema 5;
   - record 2 migrates to distance 4, zero rates, and phases that reproduce the stored sun
     angle;
   - only the transitions `ALTITUDE_ONLY` and `ORIENTATION_ONLY` are added;
   - the existing stage, fsync, readback and replace path, the 100-tick cooldown and the
     shared write budget all apply;
   - the 8,192-byte record and 4 MiB registry limits, tested at maximum population;
   - a changed altitude or orientation is refused while an elevator pair is bound or a ride
     is pending.
5. **Clock** (§5.2.1): exactly as specified there.
6. **Network:**
   - station sky payload schema 2, at most 256 bytes, with trailing-byte rejection;
   - no UUID, name or owner sent for visitors;
   - celestial protocol 3 to 4, with the message table of section 11;
   - a heartbeat once per 20 ticks per player while any rate is nonzero, inside the existing
     fair budget.
7. **Client sky:** all three axes are represented consistently. Do not copy the legacy
   degree, turn or NORTH bugs.
8. **Data:**
   - models and original textures;
   - loot and recipes;
   - en_us and zh_cn names and the §5.3 strings.

   All through data generation. Follow WORKER-RULES for new configuration keys and the
   exact configuration count.

## Tests (required)

- JUnit:
  - the clock and phase arithmetic, including wraparound, negative rates and an epoch
    change;
  - the record 2 to 3 migration (the sun angle is reproduced);
  - the record and registry codecs at their limits;
  - the payload codec bounds and trailing-byte rejection;
  - the transition whitelist.
- GameTests:
  - each controller refuses outside the actor-reach and core-reach rules;
  - the warp controller reaches the same outcome as the command;
  - gravity, altitude and orientation writes persist across a station manager reload;
  - the elevator guard refuses a changed value and accepts an unchanged one;
  - the sky payload heartbeat stays within its rate.
- The whole unit suite once. At the end: `runData` and `runGameTestServer` (under the
  lock), then the log checker. Report the GameTest totals.

## Not in this task

- Landing pads (§6.1).
- The force field (W04).
- The beacon and finder (§7).
- Physical station rotation.
- Solar attenuation by altitude.
- Status files and the ledger.
