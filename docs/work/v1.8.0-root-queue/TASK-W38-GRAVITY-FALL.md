# TASK W38: gravity-scaled fall damage (C18a living gravity, leaf B)

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w38-fall` |
| Branch | `codex/v1.8.0-w38-fall` (local only; Root merges) |
| Ledger row | `event:PlanetEventHandler.fallEvent(LivingFallEvent)` |
| Record | `docs/work/v1.8.0-root-queue/records/W38.md` |

## Inputs (read them all first)

- ADR-066 section 3.3, the gravity bullet.
- `docs/work/v1.8.0-c18a-living-gravity/TASK-01.md`:
  - "Leaf B: retained requirements and open finding";
  - the paragraph after it, on canceled events, the multiplier and saturation.
- Leaf A as delivered:
  - `CelestialGravityController`;
  - the `environment.classicGravityEnabled` key;
  - `LivingGravityGameTests`.
- The padded boots: `equipment/motion/PaddedBoots.java` and `SuitMotionEvents.java` line 87.

## Step 1: resolve M1 (the mount and passenger boundary)

Write `records/W38-DESIGN.md`, one or two pages, before writing the listener. Base it on the mapped
Forge 47.4.10 jar, read with `javap -p -c`. Cite each method and the arguments it passes on.

The note must cover:
- a living mount with a living rider, such as a horse with a player or a zombie;
- player precedence: a player's field or station factor against the mount's Level factor;
- a non-living vehicle, such as a boat or minecart, with living passengers;
- nested passengers;
- how cancellation and immunity propagate, for example padded boots, slow falling and the
  `fall_damage_immune` tag.

Required outcome: each living entity's fall is scaled **once** per physical landing, with the
ARCE multiplier, before vanilla computes the damage. Constraints:
- Do not suppress native passenger damage.
- Do not add hidden markers.
- Do not extend ADR-068's hatch hooks to living entities.
- Use Forge events only. ADR-073 allows Mixin only with its own ADR.

If a once-only rule is impossible without a new interception mechanism or a major semantic
change, stop after the note and say so in the record. That needs an ADR and the owner's
confirmation.

## Step 2: implement

The listener is one `LivingFallEvent` handler on the logical server.
- **Multiplier.** It uses the same ARCE multiplier that leaf A applied: the owned transient
  modifier, as `PaddedBoots.gravityMultiplier` reads it, or one shared resolver.
  - Never multiply again by the complete gravity attribute, which includes other mods'
    modifiers.
  - Never resolve a second time in a way that could disagree with leaf A.
- **Distance.** Computed as distance times multiplier, in double precision.
  - The result saturates at `Float.MAX_VALUE`.
  - A finite, non-negative result is written back.
  - A malformed distance or factor leaves the event unchanged.
- **Untouched fields.** The damage multiplier is not changed, and no damage is applied directly.
- **Canceled events.** These stay untouched. Padded boots still cancel.
- **The gravity switch.** When `classicGravityEnabled` is false, falls are unchanged.
- **Unaffected cases.** The Overworld (factor 1), and any entity without the owned modifier,
  are unchanged.

## Tests

- **Unit tests:** the pure scaling, covering bounds, NaN, infinity, negatives, saturation and
  factor 1.
- **Native GameTests:** use real falls where you can; call `causeFallDamage` with the vanilla
  arguments where you can't. Cover:
  - a player and a mob on the Moon, against the same fall in the Overworld;
  - a player with a station or field factor, if the existing fixtures allow it;
  - a horse with a rider;
  - a boat or minecart with a passenger;
  - a nested passenger;
  - padded boots still cancelling;
  - slow falling;
  - the switch off.

  Each test asserts that the damage reflects exactly one scaling.
- **Regressions:** the existing `LivingGravityGameTests`, the station and field suites, the
  planetary exposure suite and the padded boots tests must pass unchanged.

## Commands

- The whole unit suite.
- `runGameTestServer` under the lock, then the log checker.
- Report the escrow `SUPERSEDING_SAVE` flake if it appears; W36 owns it.

## Not in this task

- Non-living gravity.
- Non-player area fields or station overrides.
- Other C18a rules.
- Status files, the ledger, ADRs and the risk register. Root updates these.
