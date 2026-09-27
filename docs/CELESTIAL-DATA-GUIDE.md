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
  dimension, load chunks or permit a flight. Surface travel also requires an
  unambiguous mapping, an actually started Level, a reachable route and a safe
  server-selected landing site.
- Setting `landable=false` closes new arrival, not departure from a valid
  still-mapped source. `orbitable=false` closes new station creation/arrival,
  but does not delete committed stations or revoke an authorized departure.
- Station UUIDs, live ownership/membership and region checks remain required.
  An orbit flag does not add a generic-orbit flight destination.
- Preserve the fixed Earth/Overworld, Moon/Moon and Space/Space mappings. Do not
  remap an existing custom body across restarts: the world-owned binding history
  below reserves mapped and unmapped identities independently of this schema.

## Built-in planetary worlds

The development build adds `advancedrocketrycommunity:mars` and
`advancedrocketrycommunity:venus` as both body and startup Level IDs. Mars has
red-sand/red-sandstone rolling terrain; Venus has yellow-terracotta surfaces
over basalt highlands. These use original generation rules and existing vanilla
blocks. Their configured environments are gameplay analogues, not a scientific
simulation. Custom skies, temperature protection and discovery progression are
not supplied by these world definitions.

`advancedrocketrycommunity:gas_giant` is an unmapped, orbitable body. It has no
surface Level or landing position. A committed permitted station may orbit it;
surface launch requests are rejected.

Use the existing rocket flight screen and fuel/route checks to reach the new
surfaces. The operator `/arce celestial goto` utility remains limited to its
historical fixed destinations; it is not a general planet teleporter. Players
standing on a mapped orbitable surface can create that body's station through
the existing station controls, subject to the same ownership limit.

For new surfaces, landing chooses from eight bounded server-owned candidates
around horizontal origin zero, uses the maximum terrain height under occupied
columns and requires sturdy nonfluid support under at least one bottom block.
Air overhangs are permitted; fluid below any bottom block rejects the site.
The selector does not clear terrain or build a platform. Occupied, out-of-border,
out-of-height or unsupported candidates are skipped; no safe candidate means
the launch is denied. If support is lost before destination spawning, the
existing transfer service returns to the source with its original fuel ledger.
Earth spawn-based and Moon fixed-height landing behavior remains unchanged.

Back up the complete world before installing added worldgen data. The new
dimensions are available on startup, not created by `/reload`. Existing binding
records must agree with the added IDs: a custom pack that previously reserved
`mars`, `venus` or `gas_giant` differently blocks startup rather than overwriting
its worlds. Prefer your own namespace for custom content. Generator changes
affect newly generated chunks only; rollback requires the complete pre-upgrade
backup, not deleting a dimension directory or binding entry.

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

## World binding persistence

Before the first start with persistent planetary bindings, back up the complete
world and keep its last working data packs. First startup records the validated
catalog's body/Level assignments; it cannot reconstruct assignments used before
this feature existed. A missing binding file is not proof of a fresh world.

The world owns `data/advancedrocketrycommunity_planetary_bindings.json`, an
independent schema-1 file. After adoption, a body cannot change its Level or
switch between mapped and unmapped. Removing a body from the pack retains its
record and reserves its Level; restoring the same definition restores eligibility
subject to ordinary capability, actual-Level and travel checks. A new body ID
cannot take a retired body's Level. Packs assigning two bodies to one Level are
rejected rather than selecting one. Shared-Space station regions retain their
separate existing authority.

There are at most **128 lifetime bindings**, including retired bodies, and
**32768 UTF-8 bytes** of stored binding data. Long IDs can reach the byte limit
before the entry limit. Reload never evicts old bindings to accept new ones.
Metadata and route changes do not rewrite the binding file when identities stay
the same, but its integrity is still checked before publication.

Binding errors reject a runtime reload and retain the previous catalog. An
incompatible, unreadable or interrupted binding file blocks startup before world
loading. Keep the server stopped, preserve the diagnostics, and compare the
packs and binding files against a complete backup. An interrupted `.json.pending`
file is retained for recovery, not automatically adopted. Do not clear binding
history to bypass a conflict; restore a known complete backup instead. The
underlying server can emit a crash report and even return exit code zero on a
rejected pre-Level startup, so check readiness and the explicit error, not only
the process exit code.

These records do not create dimensions, load chunks, establish historical-world
migration correctness or open new flight destinations by themselves.

The [implementation log](work/v1.4.0-implementation-log.md) records development
verification and remaining scope; it is not a release approval.
