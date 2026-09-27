# ADR-034 — Planetary exposure and protection

```yaml
status: ACCEPTED
date: 2026-09-27
owner: sunthemoon
scope: v1.4.0 environmental response
authority: standing maintainer authorization to use recommended implementation decisions
```

## Context

Gravity and oxygen already consume body definitions. Temperature, high pressure
and solar intensity do not yet affect survival. Changing all historical bodies
would silently change existing data packs, and a breathable outdoor atmosphere
must not be mistaken for a climate-controlled room.

## Decision

- Add optional strict boolean `environment_effects` to definition schema 2,
  default `false`; legacy definitions cannot contain it. The original model
  constructor defaults to false. Mars and Venus opt in; gas has no surface.
  This is an additive data contract, not a new save or network schema. Legacy
  authoring must reject a lossy downgrade of an opted-in definition.
- Only an unambiguous actual surface Level uses these effects. Shared Space
  never inherits its station's orbiting body's surface hazards. Missing or
  removed definitions stop these additional effects; existing conservative
  oxygen behavior and existing gravity policy remain unchanged.
- Gameplay-safe temperature is 240..330 K inclusive; pressure above 2 is high
  pressure. Low-pressure/nonbreathable oxygen behavior remains the existing
  finite suit/vent system. Solar intensity above 1.5 is hazardous only in the
  daytime with direct sky visibility in a loaded column. Radiation remains
  metadata, as required by this version's placeholder scope.
- Cold, heat, pressure and sunlight are separately evaluated. Every 20 exposed
  ticks, attempt at most 2 health points of damage, selecting pressure, heat,
  cold, then sunlight if multiple hazards remain. Do not add this damage on a
  tick where life support already applies suffocation damage. Normal damage
  cancellation, immunity, armor and resistance remain effective. This is a
  bounded gameplay rule, not a physical simulation or a fixed observed HP loss.
  A suffocation-attempt tick still consumes the scheduled exposure interval.
  Changing one hazard/protection bit does not reset the phase while any hazard
  remains; complete protection does.
- Creative, spectator and dead players are exempt. Exposure phase resets on
  protection, player-entity replacement, Level/profile change, logout or server stop; it is transient, not
  persistent punishment. No offline catch-up or world/chunk scan is introduced.
- An active, supplied, sealed vent volume is climate controlled, including on
  an otherwise breathable hostile surface. Reuse the existing scan/dirty/chunk
  budgets and finite vent supply. Invalidation or exhausted supply immediately
  removes protection. Ambient breathability alone grants no climate protection.
- Three server-loaded item tags, `thermal_protection`, `pressure_protection`
  and `solar_protection`, select passive protection. Each requires exactly one
  correctly slotted armor item in all four slots. Built-in suit pieces belong
  to all three tags. Tags do not supply oxygen or grant suit API registration;
  an empty protected suit still suffocates. A third-party ArmorItem can opt in
  with tags independently of its oxygen adapter. Other item types do not gain
  passive protection merely by being forced into an armor slot.
- Add stable damage IDs `planetary_cold`, `planetary_heat`,
  `planetary_pressure`, `planetary_solar`. Generate original data and English/
  Chinese death/status text; do not replace old damage types or their tags.
- Explain remaining exposure with server-generated localized action-bar text,
  on change and at most once per 100 ticks otherwise. Clear the old warning on
  protection. The oxygen HUD remains specifically oxygen status. No new C2S,
  custom channel, public API export or client authority is introduced.

## Compatibility and rollback

Existing packs without the opt-in preserve their environment behavior; current
Mars/Venus content deliberately enables the new survival rules. All previous
world, binding, station, rocket, vent and suit IDs/schemas remain unchanged.
Damage/tag IDs are additive and must not be renamed in 1.x. Public API 1.7 and
display protocol 2 are unchanged; the extra flag is authoritative server data,
not inferred from the display snapshot. A pre-ENV development build rejects a
pack containing the new field; rollback restores the matching prior pack and
world backup, not a lossy rewrite. No release Gate or historical approval changes.

## Verification

Test opt-in/default/type/legacy codec behavior, finite thresholds, all protection
combinations, simultaneous exposure, cadence, exemptions and reset. GameTests
must exercise actual registered damage and item tags, live vent supply and
invalidation, sunlight occlusion, gravity, reload and Earth/Moon/Space behavior.
Run the scoped build/DataGen/GameTests and bounded packaged restart checks;
retain actual failures and independently review the diff and key checks.
The full GPU, multiplayer and long-load campaign remains scheduled by ADR-018.

Implementation uses the repository's existing DataProvider and lifecycle
patterns; see [Forge data generation](https://docs.minecraftforge.net/en/1.20.1/datagen/)
and [Forge lifecycle](https://docs.minecraftforge.net/en/1.20.1/concepts/lifecycle/).
No upstream source or art is imported.
