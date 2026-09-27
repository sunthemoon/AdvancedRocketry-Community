# ADR-033 — Planetary worlds and surface admission

```yaml
status: ACCEPTED
date: 2026-09-27
owner: sunthemoon
target_version: v1.4.0
dependencies: [ADR-030, ADR-031, ADR-032]
accepted_by: sunthemoon
accepted_at: 2026-09-27
acceptance_basis: maintainer standing authorization to proceed with recommended solutions
```

## Additive content identities

Use namespace `advancedrocketrycommunity`. Stable body IDs `mars`, `venus` and
`gas_giant` are added without changing Earth/Moon/Space. Mars and Venus each
have identically named Level, dimension-type, biome and noise-settings keys;
their normal-noise keys are `mars_terrain` and `venus_terrain`. The gas giant
has no Level or dimension-type. Definitions use schema 2; bindings use ADR-032.
No identity is renamed, reused or migrated to a different native directory.

Mars is cold, low gravity, thin nonbreathable atmosphere, with red sedimentary
rolling terrain. Venus is hot, near-Earth gravity, dense nonbreathable atmosphere,
with dark volcanic ridges and a yellow surface. These are gameplay analogues,
not a scientific pressure/temperature simulation. Temperature/solar values are
configuration in this slice; damage/protection and custom skies remain later
v1.4 capabilities. Original authored density/surface/biome rules use existing
Minecraft block IDs, not copied art or a runtime custom generator.

New bidirectional routes are `earth_mars`, `earth_venus`, `mars_surface_orbit`,
`venus_surface_orbit` and `earth_gas_orbit`. The last connects Earth surface to
the gas body's orbit anchor, usable only by a committed permitted station;
it does not create a generic orbit landing site. Existing route IDs/values and
legacy generated resources stay unchanged. DataGen emits additive v1.4 resources.

## Physical admission and landing

Runtime travel uses one captured catalog pair. Before new launch/quote success,
compare the rocket's saved current body/Level/typed target with the actual source
Level and, for stations, the committed region and orbit body. A body surface
must have one unambiguous mapped definition; a destination additionally requires
surface-arrival capability and an actual started ServerLevel. Unknown/removed
definitions, missing Levels, mismatched saved identities and Space-as-surface
reject without rewriting stored identity or creating a transfer/fuel debit.
Existing in-flight journals retain their captured recovery authority.
Station records keep the established legacy `space` current-body alias:
`currentDimension=space` plus a committed matching station UUID/region resolves
the orbit body, as in the existing target migrator. That explicit alias is not
a new arbitrary-body mismatch exemption and is not rewritten during admission.
New launches accept only a mapped `BodySurface` source or a committed `Station`
source; generic `Orbit`/`Mission` sources are not launch sites. Quotes perform
only catalog, existing-Level and saved-authority checks, never terrain loading
or pad scanning. Physical pad selection happens once for an actual launch,
before creating the transfer journal entry or changing fuel/flight state.

The server selects coordinates, never the client. Keep eight deterministic
candidate offsets and existing per-candidate chunk/block limits. Preserve Earth
spawn-based and Moon fixed-pad compatibility. Other admitted surfaces use the
same eight offsets around horizontal origin zero, with height from generated
terrain, bounded footprint checks, solid nonfluid support and world-border/build
height checks before admission. Do not generate a platform, clear terrain or
silently teleport to another Level. An occupied/unsafe set is an explicit denial.
This bounded synchronous selector is not a reference-load performance claim.

For a new surface, use the maximum heightmap value across occupied horizontal
columns. At least one actual bottom-layer rocket block must have a sturdy upward
face immediately below it; reject fluid beneath any bottom-layer block. Other
bottom blocks may overhang air, so this is rigid-rocket support, not a requirement
for a perfectly flat footprint. Support inspection is a separate bounded phase:
at most 2048 bottom-block positions per candidate, alongside the existing at most
2048 column queries and 2048 occupied-block checks. Eight candidates and at most
16 footprint chunks each remain unchanged (at most 128 candidate-footprint
positions across the search, not a cap on repeated `getChunk` calls or vanilla
worldgen-neighbor work). Moon keeps its legacy minimum landing Y=80 and does not acquire this
new ground-support requirement; Earth retains its existing heightmap policy.
Recheck these new-surface support/fluid conditions immediately before normal
destination spawn, using the same bounded availability check. A changed pad
returns the rocket to its recorded source under the existing transfer/fuel
policy; it does not synthesize another landing site. Earth/Moon/Station pad
compatibility and captured recovery identities are unchanged.

Player station creation resolves their actual, unambiguous mapped surface from
the same catalog and checks orbit capability. It no longer hardcodes Earth/Moon;
Space is not a surface origin. Existing ownership limits, permissions, shared
Space regions and reserve/generate/commit semantics remain. Operator creation
continues to permit defined orbitable unmapped bodies. No moving station/warp
or new generic-orbit flight is introduced.

## Compatibility and verification

New worlds are startup datapack registry data, not reload-created dimensions.
Adding their definitions to an existing ledger appends reservations; old saved
worlds/targets remain intact. Removing pack definitions closes entry and leaves
native files and committed records untouched. Adding the worldgen data to a
world created before these keys must be checked with a short real restart;
first-adoption history still has ADR-032's explicit limitation.
Appending is conditional on identity compatibility. A prior custom pack may
already have reserved any of these host-namespace IDs: a different mapped or
unmapped identity must block startup, not be reset, overwritten or treated as a
new builtin. Retain that world/ledger and repair packs on a backed-up copy.

Public API 1.7, display protocol 2 and existing rocket/station save schemas stay
unchanged. Rollback is a complete pre-upgrade backup, not deleting dimensions or
binding entries. New generator settings affect only newly generated chunks;
later generator changes must preserve IDs and disclose terrain seams.

Verify generated data through native codecs/startup, contrasting actual chunks,
reachable routes, bounded landing, absent/closed/mismatched target denial,
fuel/rocket conservation, player station source resolution and same-world
restart. Independent review/key reruns accompany implementation. Full S2, V1/V2,
historical upgrade and long-load acceptance remain open under ADR-018.

Forge's [datapack registry generation documentation](https://docs.minecraftforge.net/en/1.20.1/datagen/server/datapackregistries/)
describes registry bootstrap and generation; the project-specific IDs, terrain
and admission policy above are not supplied by that API.

Recorded under the maintainer's standing authorization to use recommended
solutions after the independent draft review's support, identity-collision,
quote and source-admission clarifications. The independent report remains
separate from this decision. No new numbered manual approval, release permission
or Gate PASS is claimed.
