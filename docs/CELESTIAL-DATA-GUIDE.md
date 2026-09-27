# Celestial definition data

The v1.4 development build accepts schema-2 definitions under
`data/<namespace>/celestial_bodies/<name>.json`. The `id` must match the resource
name. Definitions describe logical bodies; they do not create Minecraft worlds.
Back up a world before changing its data packs, and keep client/server builds
matched: the celestial display channel and snapshot format now use version 2.

## An unmapped body

For example, `data/example/celestial_bodies/gas_giant.json`:

```json
{
  "schema_version": 2,
  "id": "example:gas_giant",
  "parent": "advancedrocketrycommunity:earth",
  "gravity_multiplier": 2.5,
  "atmosphere": {
    "pressure": 10.0,
    "breathable": false,
    "temperature_kelvin": 150.0,
    "profile": "example:gas"
  },
  "orbit": {
    "distance": 100,
    "period_ticks": 1000,
    "inclination_degrees": 0.0
  },
  "visual_profile": "example:gas",
  "capabilities": {"landable": false, "orbitable": true, "gas_giant": true},
  "solar_intensity": 0.04,
  "radiation": 0.5
}
```

These are illustrative configured values and profile IDs, not physical
astronomy or bundled visual assets. Solar intensity and radiation are metadata;
this build does not apply new temperature, solar or radiation gameplay effects.

## Mapping and admission

- Omit `level` for an unmapped body. Do not use `null` or shared Space as a fake
  surface. A gas giant requires no Level and `landable=false`.
- `landable=true` requires a Level mapping; that alone does not register a
  dimension, load chunks or permit a flight. Physical surface admission still
  supports the existing Earth/Moon worlds, not arbitrary custom planets.
- Setting `landable=false` closes new arrival, not departure from a valid
  still-mapped source. `orbitable=false` closes new station creation/arrival,
  but does not delete committed stations or revoke an authorized departure.
- Station UUIDs, live ownership/membership and region checks remain required.
  An orbit flag does not add a generic-orbit flight destination.
- Preserve the fixed Earth/Overworld, Moon/Moon and Space/Space mappings. Do not
  remap an existing custom body across restarts: durable custom mapping
  protection and new-world initialization are not provided by this schema.

## Validation and legacy data

Schema 2 requires all three capability booleans, solar intensity (finite
0..16), radiation (finite 0..1), gravity (0..4), atmosphere and orbit fields.
Pressure is 0..10, temperature 0..2000 K, and breathable vacuum is invalid.
IDs are limited to 128 characters and the catalog to 128 bodies. Integer
schema/orbit values cannot be fractional or strings. Unknown schema-2 fields,
explicit nulls, missing parents, self-parenting and cycles are rejected.

Omitting `schema_version` or using integer `1` reads legacy definitions with
their required Level, unchanged original values, solar intensity 1, radiation
0, orbitable true, gas giant false, and landable true except in shared Space.
Do not add schema-2 fields to a legacy document without changing its version.
Loading does not rewrite the pack or change world/save schema versions.

## Reload and diagnostics

Celestial definitions and travel routes are prepared together and published as
one validated generation. A malformed file, invalid graph or missing endpoint
rejects the whole candidate; both previous catalogs and their route-plan cache
remain active. Initial loading fails when there is no valid pair to retain.
This transaction does not roll back unrelated Forge reload listeners, recipes
or assets.

Raw resources must be valid UTF-8 JSON: at most **32768 bytes per body**,
**4096 bytes per route**, and **16 nested containers**. Whitespace and multibyte
characters count toward the byte limit. Duplicate keys (including equivalent
escaped names), comments, raw string controls, invalid escapes and trailing
input are rejected. Limits remain 128 bodies and 512 routes; diagnostics contain
at most eight details and 2048 characters. Older packs that relied on ignored
malformed files or excessive padding must be repaired before retrying.

A surface route endpoint needs an existing mapped, non-gas body; an orbit
endpoint needs an existing body. Closing `landable`/`orbitable` does not remove
departure/reverse edges. New arrivals and station creation still apply their
separate capability and authority checks.

Inspect active data using `/arce celestial validate` and `/arce celestial list`.
Operators can use `/arce celestial route <route_id>` to inspect one active route
and its generation without loading terrain. After a rejected `/reload`, repair
the reported pack file and retry; do not delete world or station data. When
removing a body, remove its route references in the same edit. Test pack changes
on a disposable world before using an existing save.

The [implementation log](work/v1.4.0-implementation-log.md) records development
verification and remaining scope; it is not a release approval.
