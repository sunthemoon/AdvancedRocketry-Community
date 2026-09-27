# ADR-032 — Persistent planetary bindings

```yaml
status: ACCEPTED
date: 2026-09-27
owner: sunthemoon
target_version: v1.4.0
development_dependency: ADR-030
contract_dependency: ADR-031
accepted_by: sunthemoon
accepted_at: 2026-09-27
acceptance_basis: maintainer standing authorization to proceed with recommended solutions
```

## Decision and scope

Record the first accepted body-to-Level assignments in a world-owned, explicit
persistence service before opening additional planetary destinations. Keep one
binding per body, including an explicit unmapped binding. Removal from a data
pack removes availability, not the recorded identity; re-addition requires the
same mapping. A mapped Level remains reserved to its original body even while
that body is absent. No automatic rename, remap, mapped/unmapped conversion or
binding deletion is supported. Environment, parent and capability metadata can
still change within the existing catalog contract.

Runtime binding validation rejects aliases rather than selecting a body from an
ambiguous Level. Standalone catalog/instance resolution still treats ambiguity
as unresolved (ADR-014/031). This is an additional world-admission constraint,
not a change to shared-Space station region precedence.

## First initialization and compatibility

A missing ledger means **uninitialized history**, not a proven new world.
Use prospective adoption: the first validated startup catalog establishes the
recorded assignments. Operators upgrading an existing world must retain its
last working data packs and take a complete backup before that first start.
Discovery IDs and station orbit IDs do not encode a surface Level; do not infer
old mappings from them or rewrite them. Unloaded entities and removed packs
cannot be exhaustively reconstructed by a bounded startup check. Adoption does
not certify that a custom mapping was unchanged before this feature existed.

Earth/Overworld, Moon/Moon and Space/Space remain mandatory exact mappings.
Existing celestial progress, station UUID/orbit/region, rocket journals, world
DataVersion and public/network schemas are not changed. The new ledger has its
own schema; it is not part of the historical Beta schema-1-to-2 transaction.
Existing nonbaseline packs that alias a Level now fail clearly and must be
repaired on a copy before upgrade; no partial catalog is accepted.

This choice preserves ordinary first-upgrade operation without requiring an
operator to manufacture a purported historical manifest. Its explicit limit is
pre-adoption custom history. A separate import/mapping migration would require
an ADR and original-world evidence; clearing this file is not a migration.

## Storage and publication

Stable file: `data/advancedrocketrycommunity_planetary_bindings.json`, relative
to the server world root. Schema 1 has exactly `schema_version` and `bindings`;
each binding has `body_id` and optional `level`. Absent `level` is explicitly
unmapped, not an unknown record. Resource IDs are canonical and at most 128
characters. At most 128 lifetime bindings (including retired entries), 32768
UTF-8 bytes and the existing strict JSON depth/duplicate-key bounds. The byte
ceiling is an additional bound, including for long-ID catalogs. At capacity,
reject new identities; never evict retired bindings to make room.

Use strict checked reads: malformed, future, oversized, non-regular or symbolic
link state is an error, never an empty ledger. Only true file absence permits
initialization. Detect changes/removal of an opened ledger before a later
publication, including metadata-only reloads; do not overwrite externally edited authority.

Write a same-directory staging file, force its bytes, verify its readback and
atomically replace the authority file. If atomic replacement is unsupported,
reject rather than use a non-atomic fallback. Do not rely on `SavedData.setDirty`
or a log-only save failure as a durability receipt. A normal failure keeps the
old catalog/generation/cache and old bindings. A process interruption after
replacement but before publication can leave additional reserved bindings, not
an active destination or duplicated world content. An interrupted staging file
aborts startup and is preserved for operator recovery; it is not silently
promoted. A caught ordinary staging failure removes only that operation's own
staging file; cleanup failure preserves it and is reported. Recovery requires a
stopped world and a complete backup before comparing/quarantining the pending
file or restoring a known complete backup. Never discard the accepted ledger.
This is not a hardware power-loss or whole-Forge transaction guarantee.

Initial resource loading precedes access to world storage. Attach and validate
the ledger at the server's pre-Level startup event before players/gameplay;
startup conflict or I/O failure aborts instead of exposing unpinned destinations.
Subsequent paired reloads validate and commit bindings before their single
publication. Stop clears the world-bound service. Metadata-only reloads do not
rewrite the file; no dimension/chunk is loaded or created by this service.

## Verification and boundaries

Test strict schema/input bounds; first adoption and exact fixed identities;
same-mapping reload; removal/re-add; body and reverse-Level reuse; explicit
unmapped entries; accumulated capacity; failed I/O/changed disk; initial startup
refusal and retained paired generation/cache. Run actual file round trips and
a short packaged reload/restart with raw ledger receipts. Independently inspect
the diff and rerun key commands. Keep original failures.

MAP-02 still must verify actual started Levels, existing saved target authority
and bounded landing before enabling new-world travel. This ledger alone adds no
terrain, generic orbit, flight destination, GUI, API export or imported asset.
Authentic old-world migration and S2/V1/V2/full-load acceptance remain open under
ADR-018. Rollback uses a complete pre-upgrade backup; old code ignoring this
new ledger is not a supported downgrade.

## Platform evidence

[Forge SavedData documentation](https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/)
describes deferred dirty/save behavior. Pinned 47.4.10 local bytecode inspection
also shows read/save exception handling; this decision requires an explicit
acknowledged commit instead. The
[dedicated startup hook](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.1/patches/minecraft/net/minecraft/server/dedicated/DedicatedServer.java.patch)
and local pinned lifecycle inspection establish the integration point; they do
not supply this project's migration policy.

Recorded after independent source/draft review under the maintainer's standing
authorization. This is not a claim of a new manual numbered-ADR review or a
release/Gate approval. The review record belongs to the MAP-01 slice evidence.
