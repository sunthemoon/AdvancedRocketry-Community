# TASK W20: the basic laser gun

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w20-laser-gun` |
| Branch | `codex/v1.8.0-w20-laser-gun` (local only; Root merges) |
| Ledger plan | C18b (`basic_laser_gun`) |
| Record | `docs/work/v1.8.0-root-queue/records/W20.md` |

## Goal

A player holds `basic_laser_gun` and keeps using it on a block. The server traces from the
player's eye and breaks one eligible block after continuous use, through the same protected
path as ordinary mining. Aimed at an entity, the gun deals one point of damage. Everything is
decided on the server.

## Read

- ADR-066 section 5.3 (the contract; quoted in the summary below) and section 2 (shared
  authority, limits and failure).
- ADR-066 section 7.1. W16 already registered the `basic_laser_gun` sound event and its
  client playback (`ClassicSoundClient`, `ClassicSound.BASIC_LASER_GUN`). Reuse them; do not
  register another sound.
- ADR-054 (protection) and the station BUILD policy. Find how the existing breaking paths
  call them, and call them the same way.
- The content ledger row for `basic_laser_gun`: its recipe and legacy references.

## The contract (ADR-066 section 5.3)

- **Use.** Vanilla item use (`getUseDuration`, `onUseTick` on the server). Add **no new
  network channel or packet**. Packets contain no target position, entity, damage or drop list.
- **Trace.** From the server player's current eye and look direction, at most 50 blocks.
  - Check that each ray segment's chunk is loaded before tracing it.
  - Never query an unloaded segment through a loading world API.
- **Breaking.**
  - One held target at a time.
  - 20 valid continuous-use ticks break one eligible block.
  - Then a cooldown of five ticks.
  - Durability is 512, and each break uses one.
  - Reset on any change of target, block state, item, range, Level or player, and on use
    end.
- **Eligibility.**
  - A zero-hardness block can break after one tick.
  - Refuse negative hardness, unbreakable blocks and unsupported block entities.
- **Protection.** Break through the ordinary server-player path (`ServerPlayerGameMode`),
  so that the following all run:
  - ADR-054 protection;
  - the Forge `BreakEvent`;
  - the real tool harvest check and loot;
  - the station BUILD policy.

  If the event is cancelled, nothing happens: no durability damage and no mutation.
- **Entities.**
  - An entity hit attempts one point of damage, with the vanilla interaction,
    line-of-sight, PvP and protection checks.
  - The gun never mines a hidden block behind that entity.
- **Tracking.** Track at most one state per connected player, and clear it at logout and
  server stop. Do not replay rewards after the cooldown.
- **Configuration.** The numbers above are ADR proposals. If a matching configuration key
  already exists, use it. Otherwise hard-code the constants and add no new key.

## Tests

- **Unit tests:**
  - the pure trace and segment arithmetic;
  - the tick, cooldown and reset state machine.
- **GameTests:**
  - a block breaks after 20 ticks, and the cooldown follows;
  - a zero-hardness block breaks after one tick;
  - an unbreakable block is refused;
  - a cancelled `BreakEvent` and a protected position leave no damage and no mutation;
  - a trace that would cross an unloaded chunk stops and loads nothing;
  - an entity hit deals one damage, and the block behind the entity is not mined;
  - durability falls by one per break;
  - changing the target resets the progress;
  - logout clears the tracking.

  Pace any loading wait with `GameTestTickPacer`.

## Commands

- The whole unit suite once.
- `runData` under the lock, if anything generated changes.
- `runGameTestServer` under the lock, then the log checker. Report the totals.

## Write scope

- A new package for the laser gun.
- Its `ModItems` entry and creative-tab entry.
- Recipe, model, texture, language and provenance data, through the v1.8 data generators.
- Tests.

## Not in this task

- The jackhammer.
- The orbital laser drill.
- Network channels.
- Status files and the ledger.
