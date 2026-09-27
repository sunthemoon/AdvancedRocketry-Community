# ADR-040 — Station regions and migration foundation

```yaml
status: ACCEPTED
date: 2026-09-28
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-09-28
acceptance_basis: maintainer standing authorization to proceed with recommended solutions
target_version: v1.5.0
development_dependency: ADR-039
station_registry_schema: 3
station_record_schema: 2
station_reservation_schema: 1
```

## Scope and source constraints

Existing stations are UUID-owned regions in shared Space, not snapshots of all
blocks in a region. Record schema 1 stores a centered 512-square region, fixed
landing pad, ownership/members/invitations, orbit body and environment. Registry
schema 2 uses the common Beta migrator. Reservations incidentally use the same
version constant as records although their shape is different. Station flush
uses ordinary SavedData and is not an acknowledged durability barrier.

This first contract covers station identity, bounded geometry, explicit legacy
migration and new region-operation authority. It does not implement orbit
physics, warp, resource costs, star systems or a public extension API. Those
remain separate required capabilities with their own contracts.

## Stable geometry and expansion policy

Keep the shared Space Level key, station UUID, cell, landing pad and orbit ID.
Keep grid spacing 1,024, cell coordinates within +/-1,000,000, landing Y 128 and
the existing 289-block initial platform. New stations still begin 512 by 512;
old stations do not shrink, move or expand automatically on load.

Record schema 2 permits exactly two centered inclusive squares: width 512 or
768. For cell center `(cx, cz)` and width `w`, bounds are
`[cx-w/2, cz-w/2, cx+w/2-1, cz+w/2-1]`. The pad stays `(cx,128,cz)`.
No arbitrary rectangle, offset center, relocation or shrink is permitted.
Growth from 512 to 768 is explicit and idempotent. Record width is derived from
the existing four-element `region` array; do not add redundant persisted size.

The 768 upper bound deliberately increases claim area, not scan/work budgets.
Even maximum-size adjacent cells leave 256 unclaimed block columns between
regions. Existing nearest-cell lookup remains one indexed lookup plus one
containment test, including negative boundaries. Allocation reserves one cell
and never chooses another station's cell. Committed and reserved UUID/cell
conflicts reject the whole restored registry. Counts remain 4,096 stations,
64 reservations, 32 members/invitations/destinations, one normally owned station,
8 KiB per record and 4 MiB registry. No terrain/chunk scan supplies this proof.

Expansion adopts a larger **permission/context region**, not a block inventory.
Existing gap blocks may be incorporated; neither old free building nor template
deletion proves the gap is empty. The local owner (or permission-level-2 operator)
must explicitly confirm this consequence. Members/invitees cannot expand. Both
owner and operator must be actual server players inside the currently committed
Space region, with their current-position chunk already loaded; no console or
remote-operator bypass. The
server derives the two allowed bounds from the current station; it never accepts
client coordinates, loads surrounding chunks or overwrites/copies their blocks.
This claim change has no item/energy cost; it is not warp or a free teleport.
The command/UI must warn about existing blocks before confirmation, identify
the station UUID/current bounds, and reject a stale or changed request. Confirmation
is server-issued and bound to actor UUID, station UUID, the observed complete
immutable station state and the registry service lifecycle. At most one pending
confirmation per player and 128 overall, lasting at most 200 server ticks;
capacity exhaustion rejects another request. Recheck current permission, region,
state, authority availability and expiry at commit. Expired entries are discarded
with bounded work; logout/server stop clears the applicable entries. A confirm
cannot transfer to another player or select a different region. No
automatic expansion on entering a gap, station deletion or cell reuse.
This is an explicit gameplay tradeoff, not a claim of foreign-block detection
or comprehensive protection from automation/explosions/fluids.

## Independently versioned station storage

The stable file remains `advancedrocketrycommunity_stations.dat` in Overworld
SavedData. Its root schema becomes 3 with `format_epoch=v1.5.0-orbital-station`.
Other managed files remain root schema 2 / `v0.9.0-beta`; do not advance the
global current version and accidentally rewrite celestial, rocket or mission
authorities. Keep type-specific current-schema/epoch selection small and explicit.

Schema-2 station records retain all existing fields and meanings, with only the
accepted region geometry widened as above. Reservations retain independently
named schema 1 and the existing base fields. They always commit an initial
512-square station. No speculative warp revision/journal/energy fields yet.

Migration accepts legacy root 1, or root 2 with the exact Beta epoch, containing
legacy record 1 and reservation 1. Validate **legacy** 512 geometry, exact cell
and pad, IDs/types/lists/bounds before changing versions. Then copy the payload,
change root version/epoch and record versions, leaving UUIDs, owner, name, cell,
region, pad, body, creation time, environment, members, invitations and reservation
data unchanged. Legacy unknown orbit IDs remain recoverable, not remapped.
Do not reinterpret a malformed legacy 768-wide record as valid new data.

Current root 3 accepts only record 2 / reservation 1. Mixed/future/invalid data,
bad epochs and over-bound output reject before rewriting any file. Version
migration preserves extra tags by copying rather than round-tripping through a
lossy encoder. Ordinary subsequent saves retain the existing policy of encoding
known fields; this is not a new promise to retain arbitrary unknown tags forever.
Existing valid byte-valued vacuum semantics remain unchanged; do not introduce
silent boolean normalization in the migration itself. Repeated startup of a
current file does not remigrate or create another backup.

Integrate through pre-server-start WorldDataMigrationService: validate every
managed source first, back up the original managed files and `_old` companions,
stage/validate replacements, then commit. A nested record upgrade must count as
a real migration, not happen later outside backup coverage. Keep the five-backup
cap and bounded IO. Introduce an accurately versioned manifest with per-primary
file source/target schemas; previous-copy entries are backup bytes, not a claim
that their content was migrated. A mixed 2-to-3 station / unchanged-2 world must
not be labeled globally as schema1-to2. Retain original hashes and existing
manifest readability for restoration; do not rewrite historical backups.

The existing migration service falls back to non-atomic replacement when the
filesystem lacks atomic move. Do not cite it as a cross-file power-loss
transaction. The station migration must require atomic replacement for every
upgraded authority and never fall back to a non-atomic move; ordinary caught failures restore and
verify backed-up source bytes. Process interruption may leave a mix of complete
old/new files; each individual source must be valid and startup must revalidate
before resuming. A leftover staging file is not authority. Operator restoration
uses the complete pre-upgrade backup, not a version-number edit. Keep arbitrary
hardware power loss, directory fsync and automatic backup pruning unclaimed.

## Runtime authority and checked mutation

A blocked registry is different from a valid gap. While station authority is
unavailable, deny player placement/break operations in Space, including operator
ordinary building; retain raw blocked data and give bounded diagnostics. Other
Levels are unaffected. Existing valid-gap behavior stays unchanged. Do not
publish an empty operational registry after malformed/future data.

Before exposing expansion, add an isolated checked **candidate expansion commit**,
reusing the existing bounded atomic IO where appropriate with explicit station
authority and tests. Keep the old ordinary `flush` callers' exception behavior
unchanged in this slice: merely inheriting a throwing `AtomicSavedData.flush`
would break creation's post-commit rollback, which releases only reservations.
Do not silently switch the whole station SavedData subclass or route creation,
membership and deletion through the new throwing API. Changing those callers
requires a separately enumerated adaptation and failure tests before adoption.

A success response means the exact candidate registry snapshot
was forced, validated and atomically replaced. Failed writes must not acknowledge
growth or leave a later autosave silently committing a rejected mutation. Restore
the previous in-memory authority when failure is known to precede replacement;
otherwise quarantine mutations and require verified recovery. Stage an immutable
candidate without mutating the live model; after checked replacement publish
that candidate in the same server operation. Keep selection,
permission, bounds and save on the owning server thread; publish no intermediate
permissions/context to another tick. Do not claim this alone makes old platform
creation, block/chunk saves, item payment or passenger state atomic.

All existing visit/build/team membership and transfer/delete permission semantics
remain unless explicitly changed here. Expansion is owner/operator management;
invitations grant nothing before acceptance. Preserve public API 1.7 signatures
and configured environment semantics and existing wire channels. A new payload
requires its own bounded protocol decision; use an existing server command for
the first user operation rather than silently adding an unversioned packet.

## Orbit movement boundary

Preserving identity/cell is the foundation; no orbit-changing mutator ships in
this slice. Before warp implementation, a separate ADR must decide stable-region
logical orbit relocation versus a bounded physical snapshot transfer and resolve
the version plan's target-copy/source-delete wording. A logical relocation may
be the smaller design, but it still owes actual environment/sky/routes, cost,
countdown, durable commit and online/offline passenger and docked-rocket behavior.

In particular, saved rocket currentBody, plans and transfer snapshots bind old
authority; changing station.orbitBody alone can strand docked rockets. Loaded
entity queries cannot prove absence of unloaded rockets or in-flight authority.
Do not rewrite old journal checksums, infer an empty docking roster, or claim
cross-store crash safety from discovery tests. Multi-star identity/unlocks and
warp costs/recovery remain unfrozen, not filled with speculative placeholder code.

## Verification and rollback

1. Schema/model: legacy/current/future/mixed/over-bound fixtures; exact old fields
   and extra-tag preservation; reservation schema independence; negative and
   maximum coordinate edges; 512/768 containment, gaps, conflicts and reuse.
2. Migration: actual pre-start backup manifest/bytes; current idempotence; corrupt
   source and capacity rejection before writes; injected staging/replace/restore
   failures; unsupported atomic move; unchanged non-station authority files.
3. Region operation: owner/operator/member/invitee checks, stale confirmation,
   local operator/console rejection, confirmation expiry/cap/logout, checked
   success/failure, fail-closed unavailable authority and unchanged world
   blocks/chunk-load counts. Finite 10/100 lookup checks are not long-load results.
4. Copied authentic v1.4 station world -> upgrade -> same-world second restart,
   preserving member/invite/orbit/geometry/docked identity and verifying an
   expanded region. Synthetic JSON/SNBT fixtures cannot replace native evidence.
5. Required short build/unit/DataGen/GameTests and independent diff/key checks.
   Keep full V1/V2 and reference-load acceptance pending under ADR-018.

Restore the entire pre-upgrade world/managed-data backup appropriate to the
operation when returning to v1.4; new root 3 must be refused by the old reader,
not down-versioned in place. No existing world is upgraded by this document.

## Acceptance record

Recorded on 2026-09-28 under the maintainer's standing recommended-solution
authorization after independent source/draft review. The isolated checked-save
boundary and local confirmation rules resolve the original contract findings;
original and revised reports are retained in the
[preparation evidence](../work/v1.5.0-preparation/VERIFICATION.md). The reviewer
did not grant maintainer authority or approve a Gate. Root records acceptance;
the [implementation plan](../work/v1.5.0-implementation-log.md) still has every
runtime leaf open. Preparation examples are documentation checks, not production
migration, permission, crash-recovery or runtime evidence.
