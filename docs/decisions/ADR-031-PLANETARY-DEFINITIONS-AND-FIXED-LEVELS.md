# ADR-031 — Planetary definitions and fixed-Level strategy

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-09-27
acceptance_basis: maintainer standing authorization to proceed with recommended solutions
target_version: v1.4.0
development_dependency: ADR-030
definition_schema: 2
```

## Context and first-slice outcome

The existing model requires a `level` for every logical body. Catalog lookup,
the flight planner, contextual legacy migration, diagnostics and the display
snapshot consume that field. A gas giant must not become a fake surface in
shared Space, nor should adding metadata create a Minecraft Level.

The first implementation delivers a versioned definition usable by these
existing consumers, including a non-landable, unmapped body. It does not alone
deliver new terrain, a star-map screen or environmental damage. The full
[v1.4 outcomes](../versions/V1.4.0-PLANETARY-EXPANSION.md) remain required.

## Dimension strategy

Use data-pack Level/dimension-type entries loaded at server startup for the
new landable worlds. Preserve existing Overworld/Moon/Space keys and use
separate stable keys for Mars-like and Venus-like worlds. Content definitions
refer to a `ResourceKey<Level>`; they never mutate the dynamic registry, create
or delete a Level, or choose a numeric dimension ID. A missing Level means an
unavailable destination, not a fallback to Earth or shared Space.

Keep station regions in the existing shared Space Level and resolve them by
their committed server registry before ordinary Level mapping (ADR-014).
Gas giants may be catalog/orbit subjects without a surface Level. This does
not add generic-orbit landing platforms, warp, mission instances or moving
stations. Existing station UUIDs, ownership, positions and `orbit_body` remain.

Logical metadata and routes can reload; adding/removing actual dimension data
is an operator startup/world-management operation. Do not delete native world
directories or reinterpret an old body/Level identity as part of data reload.
The world-content slice must verify fixed mappings against actual started
Levels and existing saved targets before making those destinations usable.

DATA-01 keeps the existing fixed Earth/Moon surface-admission boundary and
exact Earth/Moon/Space mapping invariants. It does not unlock arbitrary new
surface Levels. Existing nonbaseline mapping metadata was not pinned in
SavedData: comparing consecutive catalogs alone cannot detect a remap across
restart or remove/re-add. Cross-restart custom mapping protection is therefore
an explicit MAP/MIG prerequisite, not a guarantee supplied by schema 2.
Before new-world admission, freeze and implement the stable body/Level binding
and old-world initialization policy with its own persistence/migration tests.
Do not add a best-effort inferred binding to a saved rocket or station here.

This extends the fixed-dimension approach rather than choosing one giant
shared surface or a runtime dimension allocator. Separate fixed worlds retain
ordinary chunk/worldgen lifecycle and isolate contrasting terrain without a
new coordinate-region ownership system. Their memory/worldgen cost remains a
measurement obligation, not a presumed performance win.

## Definition schema

Keep resource directory `celestial_bodies` and stable body IDs. Schema 2 uses:

| Field | Contract |
|---|---|
| `schema_version` | Required integer `2`; future/zero/negative/malformed versions rejected |
| `id` | ResourceLocation, matches the resource file ID |
| `parent` | Optional ResourceLocation; missing means root, not an unknown parent |
| `level` | Optional ResourceKey name; absent is unmapped, never a fabricated sentinel |
| `gravity_multiplier` | Finite 0..4, unchanged bound |
| `atmosphere` | Existing pressure 0..10, breathable boolean, temperature 0..2000 K and profile ID |
| `orbit` | Existing bounded logical distance/period/inclination, not physical trajectory simulation |
| `visual_profile` | Bounded resource ID; asset existence is checked by the later asset batch |
| `capabilities` | Required object: explicit booleans `landable`, `orbitable`, `gas_giant` |
| `solar_intensity` | Required finite 0..16, relative configured intensity; 1 is the baseline |
| `radiation` | Required finite 0..1 normalized reserved metadata, not a dose or damage implementation |

Each ResourceLocation remains at most 128 characters. Schema-2 objects reject
unknown/misspelled fields; optional means absent, not a malformed value or
JSON null. Boolean fields are not numbers, and integer version/orbit fields
must not silently truncate fractions. Breathable vacuum, invalid graph roots,
self-parent, missing parent and parent cycles remain errors.

Capability invariants:

- `landable` requires a Level mapping; a mapping does not itself grant arrival.
- `gas_giant` requires `landable=false` and no Level mapping; `orbitable` is
  independently explicit, not permission to create a generic orbit instance.
- A mapped body can be closed to new landings while retaining its environment
  and existing occupants. `landable=false` must not erase identity or itself
  prevent departure from a still-valid authoritative source.
- Shared Space remains a world host, not a newly landable planetary surface.
- New surface arrivals, orbit destinations and station creation check the
  relevant capability, actual mapping/context and existing authority rules.
  Existing station records are retained if a capability later changes; no
  remapping, deletion or unauthorized new arrival follows.
  A committed station around a still-defined body may still be a departure
  source when `orbitable=false`, subject to the same live region/ownership
  checks. That flag closes new arrival/creation, not evacuation by an owner.

Missing `schema_version` or explicit `1` selects the legacy decoder. It requires
the existing `level` and preserves all existing IDs, parents, gravity,
atmosphere, orbit and visual values. Add defaults only: `solar_intensity=1`,
`radiation=0`, `gas_giant=false`, `orbitable=true`, and `landable=true` except
for definitions mapped to the existing shared Space Level. This matches the
existing separation between surface destinations and station regions; operator
fixed-Space diagnostic travel remains a separate explicit permissioned tool.
Legacy documents containing schema-2 fields without declaring version 2 fail
rather than silently losing the new intent. Other previously ignored legacy
fields retain their old behavior within the input-size limit.

Encode new data as schema 2. Reading legacy data does not rewrite world files
or mutate data packs. Preserve original generated resources and provenance;
if new generated schema-2 resources supersede them, package one authoritative
copy per resource ID and audit that exact exclusion. An explicit legacy export
path may retain the old authoring format; it must not silently erase schema-2
capabilities or claim downgrade compatibility.

No new public API record component is implied: v1.3 API 1.7's exported class
signatures remain intact. New internal metadata is not an unannounced binary
change to `EnvironmentSnapshot` or `AtmosphereProfile`.

## Catalog, mapping and reload boundary

Retain 128 bodies, 512 routes, existing route-search/cache budgets and ID limits.
Read each definition resource with a 32768-byte UTF-8 bound before JSON parsing;
reject malformed JSON, duplicate object keys and excessive nesting rather than
skipping files. JSON nesting is at most 16. Diagnostics remain at most eight
details and 2048 characters. Over-budget input rejects the candidate, not part
of the catalog. Verify the existing route-file bounds separately; do not raise
them to the body limit.

The raw-byte, nesting and duplicate-key rules are stricter than the old
post-parse compact-JSON character check. An older pack with excessive whitespace,
Unicode padding, deep ignored data or duplicate keys can now be rejected.
Report the resource and retain the active pair (or fail initial startup);
operators must repair/compact the pack and retry. Do not rewrite pack/world
files automatically or silently keep only the subset that parsed.

Only mapped definitions enter the Level index. Multiple body candidates for
one Level remain explicitly ambiguous; do not silently pick the first or erase
an existing instance resolver. Metadata lookup does not load terrain/chunks.
The fixed baseline still requires Earth/Overworld, Moon/Moon and Space/Space;
this contract does not silently change their graph or environment.

DATA-02 must publish celestial definitions and routes as one validated immutable
generation: decode both resource sets, validate graph and capability-compatible
route endpoints against the same candidate, then swap once. A surface graph
endpoint requires an existing mapped non-gas body; an orbit graph endpoint
requires an existing body. `landable`/`orbitable` are final-destination/new-station
admission checks, not reasons to remove an otherwise valid source or reverse
edge from the static graph. Direct generic-Orbit flight stays unsupported;
an orbit anchor used by a committed station is not such a flight request.
Failure retains
both previous catalogs, generation and derived-query/cache state. Initial
failure with no valid pair prevents an apparently usable partial startup.
Consumers planning one operation capture one pair, not unrelated snapshots.
This is not transactional rollback across every Forge reload listener or assets.
DATA-01 must not claim this stronger guarantee before DATA-02 is implemented.

Removing a body and its routes may make it unavailable for new entry; stored
visits, station records, rocket data and native world directories are retained.
Unknown sources/targets block unsafe actions with a bounded diagnostic, without
guessing another body or overwriting the original persisted identity. Existing
flight/transfer journals keep their captured authority; changes to their
recovery rules require a separate reviewed contract, not a definition default.

## Display, persistence and compatibility

Optional mappings and new capability/environment fields require a new display
snapshot format. Before publishing schema-2 bodies, bump the celestial snapshot
schema and celestial channel from 1 to 2 and require exact matching channel
versions. Include optional mapping, capabilities and configured environment in
the bounded display-only snapshot; keep the existing 96 KiB payload ceiling
and 128-body/128-character limits. The existing version/generation envelope
remains explicit. Invalid/trailing/oversized frames must not replace a good cache;
generation changes trigger complete replacement, and disconnect clears it.
Rocket/life-support/visual channels are not bumped without an actual change.

Definition schema 2 is not `CelestialSavedData` schema 2. This contract does not
change the latter, station/rocket save schemas, target schema 1, world
DataVersion, or persisted orbit IDs. Verify old-definition decoding separately
from authentic whole-world upgrade. Restoring pre-upgrade backups, not deleting
new world directories or feeding new packets to old clients, is rollback.

## Implementation and verification sequence

1. DATA-01: versioned definitions and all existing mapping consumers; bounded
   display format and actual server destination checks. Legacy/round-trip/value,
   capability, no-Level, ambiguous mapping, source-departure and malformed
   snapshot tests; unchanged API classifier identity and baseline gameplay.
2. DATA-02: bounded raw resource reader and coherent celestial/route publication;
   real successful/rejected reload, malformed-file retention, missing body/route
   pairs, 100-body synthetic input and existing budgets. This finite synthetic
   check is not a reference-load or soak result.
3. DATA-03: independent key checks and a bounded packaged restart/readback;
   distinguish real resources from synthetic migration fixtures.
4. Later current-version slices add world data, environmental effects,
   navigation/discovery and sky/assets with their concrete rules and evidence.
   Do not label metadata alone as completing these player-visible features.

Retain required short build/DataGen/GameTest checks, original failures and exact
artifact identities. Full S2/V1/V2/remote/load acceptance remains open under
ADR-018. No source/asset is copied by this decision; future imports need their
own source ledger before the file is added.

## Platform references and acceptance

Forge documents dynamic registry entries as data-file objects, with data
generation support rather than arbitrary runtime registration:
[registries](https://docs.minecraftforge.net/en/1.20.1/concepts/registries/),
[datapack generation](https://docs.minecraftforge.net/en/1.20.1/datagen/server/datapackregistries/).
Exact channel-version matching follows
[SimpleImpl](https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/).
The startup-only Level policy and paired catalog transaction are project
decisions, not claims that those documents supply a world migration API.

Recorded on 2026-09-27 under the maintainer's standing authorization to use
recommended solutions, after independent source/draft review and the route,
mapping-persistence and legacy-input clarifications above. The
[preparation evidence](../work/v1.4.0-preparation/VERIFICATION.md) retains the
original review of the proposed documents; it is not an approval message.
This freezes the first-slice contract, not new terrain, a migration result,
runtime implementation or a release Gate. Runtime work also requires the
separate accepted ADR-030 baseline decision.
