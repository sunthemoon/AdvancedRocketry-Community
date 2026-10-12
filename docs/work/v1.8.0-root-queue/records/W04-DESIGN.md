# W04 force-field persistence and interception design

This implementer owns the force-field package, its tests and generated resources,
API 1.9 enum/version/consumer documentation, registration additions, and W04
records. It does not change the ledger, status, build scripts or other slices.

## Native roots and admission

The projector's independent schema-1 compound contains a random projector UUID,
optional owner UUID, positive generation, stable direction name and a 32-bit
claim mask (bits are offsets 1 through 32). Its preflight is 4 KiB, depth 8 and
128 nodes. The field's schema-1 compound contains projector UUID, owner UUID,
generation, source Level resource location, source BlockPos and direction. It is
bounded by 1 KiB, depth 8 and 64 nodes. Exact identity, generation, source and
offset must agree before removal; an unrelated block is never removed.

The Overworld owns `advancedrocketrycommunity_force_field_cleanup` SavedData.
Its schema-1 root is limited to 4,096 entries and 1 MiB (depth 8, 65,536 nodes).
Each entry reserves cleanup capacity for one projector generation and holds its
source identity, direction, claim mask and active/orphan flag. Admission checks
both count and aggregate encoded size before any effect. Claims are mirrored
before physical placement; removal converts the reservation to an orphan record
before deleting the source. A full or quarantined root refuses admission/removal.
Unknown, future and malformed roots remain verbatim and inert. Oversized BE roots
retain their original Tag reference and use the existing whole-chunk save guard;
the ADR implementation note and risk entry precede that protection.

## Recovery and bounded work

On restart the cleanup root rebuilds its fair work queue. Only already-FULL
chunks are inspected. A matching loaded source reconciles exact field claims;
an unloaded source proves nothing. A loaded missing/different readable source
proves an orphan. Field lifecycle observations enter the same bounded queue to
recover from independently saved source/field chunks. Quarantined sources never
prove absence. Orphan claims remain until a loaded target is observed absent or
its exact field is successfully removed through protection. No tickets are used.
All work shares a 64-cell server-tick budget, with one target-cell operation per
projector generation per five ticks, including cleanup and lifecycle validation.

## Event interception and room authority

Placement checks the existing FULL/bounds/zones/station/spawn chain, then
`FIELD_PROJECTION` and Forge `EntityPlaceEvent`. Removal uses `BLOCK_BREAK` and
Forge `BreakEvent`; autonomous events use an owner-bound, non-operator FakePlayer,
never the owner's offline operator privileges. Throwing listeners fail closed.
Player break and explosion detonation preflight before drops; block destruction
overrides repeat admission. `onRemove` covers direct world replacement and restores
the old block/BE if admission refuses, without drops. Successful field changes
synchronously invoke the existing bounded atmosphere dirty-cell invalidation;
refusals and source unload do not revoke the unchanged physical boundary.

Independent review and version-wide native restart/visual acceptance belong to
Root before merge. This note does not mark any Gate passed.
