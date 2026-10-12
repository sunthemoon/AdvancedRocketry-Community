# TASK W13: station landing pads as named flight targets

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w13-landing-pads` |
| Branch | `codex/v1.8.0-w13-landing-pads` (local only; Root merges) |
| Ledger plan | C17b (`landingPad`, and the `concrete` pad recipe reference) |
| Record | `docs/work/v1.8.0-root-queue/records/W13.md` |

## Goal

A station owner or operator can register up to 8 named landing pads inside the station.
A rocket flight can then target a specific pad, and the destination staging validates it
and claims it for exactly one transfer. Legacy flights keep landing on the default cell
pad.

## Read

- ADR-065 (`docs/decisions/ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md`):
  - section 6.1 in full: it is the specification;
  - section 2 (identities and roots);
  - section 11.
- The station model, `StationState.landingPad` (its cell geometry is an invariant and is
  never rewritten), `StationAccessAction`, the rocket flight selection and the transfer
  journal (`RocketTransferSavedData` and its recovery service, which W02F hardened), and
  the destination staging and landing budgets.
- The merged W07 station controls, if you need the controller patterns.

## Scope

1. **The `landing_pad` block and its block entity:**
   - owned by the station owner or an operator;
   - a server-generated UUID, a name of at most 32 characters, a local position;
   - register and remove locally; members may VISIT but cannot register a private pad for
     another owner;
   - at most 8 pads per station.
2. **The pad index:**
   - a separately bounded pad-index root (schema 1, at most 2 MiB);
   - lookup per station, never a world scan;
   - an unloaded pad is unavailable;
   - epochs and the physical identity of the block entity stop a removed or replaced pad
     from inheriting reservations.
3. **Flight targeting:**
   - The flight selection and transfer journal gain an optional pad UUID, bound to its
     station and the observed epoch. The client never supplies a landing coordinate.
   - Destination staging validates the pad's authority and loaded identity, plus the full
     footprint, height, obstruction and current occupancy.
   - The candidate count stays 8. No operation raises the existing limits of 16 landing
     chunks or 2,048 inspected blocks. A rocket that exceeds the budgets at a pad gets a
     stated refusal.
4. **Claims:**
   - one transfer UUID per pad claim;
   - released on commit, cancel and recovery;
   - test every recovery cut;
   - pads never force chunks or take their own flight tickets.
5. **Data:**
   - models and original textures;
   - loot;
   - a recipe that references vanilla concrete by registry ID;
   - en_us and zh_cn names and messages.

   All through data generation.

## Tests (required)

- JUnit:
  - the pad-index codec and its bounds;
  - the 8-pad and 32-character limits;
  - epoch and identity stale checks.
- GameTests:
  - register and remove as owner, operator and member;
  - target a pad and land on it;
  - refused when obstructed, too small or unloaded;
  - claim release on commit, cancel and recovery, including a restart in between;
  - the default cell pad still works for an untargeted flight.

  Pace any wait for loading with `GameTestTickPacer`.
- The whole unit suite once. Each new class must also pass when run alone.
- At the end: `runData` and `runGameTestServer` (under the lock), then the log checker.
  Report the GameTest totals.

## Not in this task

- Station controls (W07).
- Loaders, the linker or the monitor (W11).
- The beacon (C17c).
- Status files and the ledger.
