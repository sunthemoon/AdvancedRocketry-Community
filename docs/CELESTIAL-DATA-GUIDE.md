# Celestial definition data

The v1.4 development build accepts schema-2 definitions under
`data/<namespace>/celestial_bodies/<name>.json`. The `id` must match the resource
name. Definitions describe logical bodies; they do not create Minecraft worlds.
Back up a world before changing its data packs, and keep client/server builds
matched: the celestial display channel and snapshot format now use version 2.

## An unmapped body

Client resource-pack sky customization is described in
[Planetary sky profiles](PLANETARY-SKY-GUIDE.md). `visual_profile` is presentation
only; neither a resource pack nor a missing profile changes environmental rules.

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
astronomy or bundled visual assets. Radiation is metadata only. Temperature,
pressure and solar exposure require the explicit opt-in described below.

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
simulation. Mars and Venus enable environmental exposure; bring a complete
oxygen-filled space suit or maintain a supplied, sealed vent room. Custom skies
and discovery progression are not supplied by these world definitions.

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

## Survival environment and protection

Schema-2 definitions can set `"environment_effects": true`. Omitted means
false, preserving older packs. This flag enables additional survival effects
only on an unambiguously mapped surface; a station never acquires its orbiting
planet's surface heat or pressure. Existing gravity and oxygen rules apply
independently, even when the new flag is false. A pre-exposure development build
will reject the new field; use the matching older pack when rolling back.

| Exposure | Unprotected condition |
|---|---|
| Cold | Temperature below 240 K |
| Heat | Temperature above 330 K |
| High pressure | Pressure above 2 |
| Intense sunlight | Solar intensity above 1.5, daytime and direct sky visibility |

Exposed survival players receive one damage attempt of 2 health points every
20 ticks, not one per simultaneous hazard. Pressure takes precedence over heat,
cold and sunlight. A simultaneous suffocation attempt is not duplicated.
Normal armor, resistance, spawn immunity and damage-cancelling mods can reduce
or reject damage. Creative and spectator players are exempt. These are gameplay
thresholds, not a physics model. Radiation remains a placeholder without damage.

An active oxygen vent in a sealed room provides climate protection while its
oxygen and power last. This also works when outdoor air is breathable but hot
or highly pressurized; breathable air alone is not climate control. Broken
seals, pending scans, unloaded providers and exhausted supplies grant no room
protection. Climate operation uses the vent's existing finite consumption.

Passive equipment protection requires four correctly slotted armor items, each
with count one and each included in the relevant server item tag:
`advancedrocketrycommunity:thermal_protection`,
`advancedrocketrycommunity:pressure_protection`, or
`advancedrocketrycommunity:solar_protection`. The built-in suit belongs to all three. Packs can add other
`ArmorItem` equipment to these tags; forced non-armor items do not count. Tags
do not provide oxygen or register an oxygen adapter. An empty suit still
suffocates, even though its passive insulation remains effective.

Localized action-bar warnings identify remaining exposure. The existing oxygen
HUD still describes oxygen protection, not total environmental safety. Cover
blocks direct sunlight, but ordinary outdoor cover alone does not cool hot air
or lower pressure. Returning to safety resets exposure; there is no persistent
or offline exposure debt.

## Using the flight star map

Open a rocket's flight console, then select **Star map**. The diagram shows
parent relationships, not physical orbital distances. Drag the viewport to pan,
scroll to zoom, or use **< / >** to focus each body. Selecting a node displays
gravity, temperature, atmosphere, sunlight and radiation metadata; this display
does not promise survival, oxygen supply or enabled environmental damage.

**Use surface** returns the chosen body to the console. Around a selected body,
**Station n/m** cycles through stations the server permits visiting; hover it to
see the name and route status, then choose **Use station**. A gas giant has no
surface arrival, but can have accessible orbit stations. Selection never starts
a flight: use the console's separate **Launch** button. Boarding, leaving and
cancelling an active countdown remain in the console.

The server explains control/access denial, unavailable routes, missing components,
thrust, fuel capacity and fuel shortages. Quotes do not reserve a landing pad or
guarantee terrain readiness; launch validates the actual situation again. Station
membership and catalog changes refresh while the console stays open. A removed
station is not silently replaced with another selected destination.

During navigation synchronization, launch is disabled until the catalog and quote
generations agree. The open console retries automatically; an active countdown
can still be cancelled from the console. Install matching client/server builds:
the navigation update uses flight protocol **7**, with celestial display schema
and protocol **2** unchanged. The diagram uses the existing public body metadata;
it does not create routes, permissions or discovery unlocks.

## Validation and legacy data

Schema 2 requires all three capability booleans, solar intensity (finite
0..16), radiation (finite 0..1), gravity (0..4), atmosphere and orbit fields.
Pressure is 0..10, temperature 0..2000 K, and breathable vacuum is invalid.
The optional `environment_effects` field accepts only a literal boolean, not
null, numeric or string values. It is not accepted in legacy definitions.
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
