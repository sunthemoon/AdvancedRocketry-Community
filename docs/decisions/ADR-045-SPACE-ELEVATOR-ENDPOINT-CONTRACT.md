# ADR-045 — Space elevator endpoint contract (v1.5 minimum)

```yaml
status: ACCEPTED
revision: 2
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-09-30
acceptance_basis: maintainer direction to finish v1.5 with recommended solutions, after independent contract review ("accept with changes"); all seven required changes applied in this revision
target_version: v1.5.0
development_dependency: ADR-031, ADR-039, ADR-040, ADR-041, ADR-043, ADR-044
implements: V150-ELEVATOR contract (minimal endpoint contract and validation)
```

## Context

The v1.5 plan (§3, §6.10, §12) asks only for a "basic space elevator connection
contract, possibly incomplete", with placeholder validation. It says no elevator
interface may expose dangerous write operations yet (§12.5), and full elevator
logistics are out of scope (§4). Nothing in the code models an elevator today.

Stations (ADR-040) are cells in the shared Space Level with a fixed landing pad,
and they orbit one body (ADR-041). Warp (ADR-044) can change that body at any
time. A surface anchor lives in a body's own Level. Levels are fixed (ADR-031),
but a body can be missing or remapped after a data-pack change (ADR-043).

## Relation to ADR-039 and v1.7

ADR-039 lists "a bounded elevator endpoint contract" as a v1.5 outcome and says a
diagnostic command alone cannot substitute for outcomes. Plan §3/§6.10 defines
this outcome as **a contract plus placeholder validation that may be
incomplete**. The contract (this record), the validator and their tests are that
outcome, not a stand-in for a player feature.

The player-visible elevator is v1.7 scope (V1.7 plan §3, post-1.0 roadmap):
- the structure;
- the climber;
- transport;
- logistics.

## Decision

### What an endpoint pair is

An **elevator endpoint pair** connects:
- a committed station (by `station_id`). Its end is derived from the committed
  record: the ADR-040 landing-pad column `(cx, cz)`, which expansion never moves.
  It is never supplied by a client. The pad column is also where rockets land, so
  v1.7 may choose a physical attachment point elsewhere inside the committed
  region, as long as it does not obstruct landing;
- a **surface anchor**: a body ID and a block column `(x, z)` in that body's
  Level.

A pair is **valid** when all of the following hold. They are checked in this
order on the server, and the first failure is reported:
1. the station registry is operational and not quarantined, and the station
   exists;
2. the requester is the station's owner or a permission-level-2 operator. This
   comes before anything else is disclosed;
3. the body is the station's current orbit body;
4. in the catalog captured once for this check (rules 3 and 4 read the same
   capture):
   - the body satisfies `supportsSurfaceArrival()` (landable, mapped, not the
     Space Level);
   - `server.getLevel(levelKey)` is present;
   - `PlanetarySurfaceResolver.find(catalog, levelKey, true)` returns this body,
     so a Level shared by several bodies is rejected;
5. the column is within ±30,000,000 blocks and inside that Level's world border.
   The border is read without loading any chunk.

The **validator is pure**: a function over a snapshot of these inputs:
- a station view;
- the captured catalog;
- a Level-presence predicate;
- a border predicate;
- the requester's authority.

A thin Forge adapter builds the snapshot. A check reads data only. It does not
load or generate a chunk in any Level, place blocks, reserve anything, move
players or items, mark any SavedData dirty, or persist anything.

### What v1.5 ships

- The pure model and validator, and one operator-only diagnostic command:
  `/arce station admin elevator check <station_id> <body_id> <x> <z>`.
  - The `elevator` literal carries its own `.requires(hasPermission(2))`. It does
    not depend on the shared `admin` node, because Brigadier keeps the first
    registered node's requirement.
  - `x` and `z` are bounded at parse to ±30,000,000.
  - It prints one bounded line: the first failing rule, or `valid`.
  - It writes one `ARCE_STATION_ELEVATOR_CHECK` audit line.
  - It is an **unstable diagnostic surface**: not persisted, no stability
    promise, and it may be renamed or removed in v1.7.
- There is no player command, network packet, block, item, API method or
  SavedData.
- **Threat note:** operator-only, O(1), no chunk access and no writes.
- **Tests:**
  - each rule in order, with the authority rule tested on the pure validator;
  - an unknown station UUID, and a blocked or quarantined registry;
  - bounds;
  - the loaded-chunk count unchanged in every Level (Space, the target body's
    Level, the Overworld), and the station registry not dirtied;
  - a catalog change that removes the body or remaps its Level;
  - a world-border shrink that excludes a previously valid column;
  - a warp to another body invalidating a previously valid pair (rule 3);
  - output bounded to one line, with the audit line written;
  - API class bytes and generated files unchanged.

### Constraints handed to the v1.7 elevator ADR

v1.7 must satisfy these constraints, or supersede them explicitly in its own ADR
and threat model:
- **Unbinding:** the owner or an operator can always unbind a pair, whatever its
  validity. Unbinding is a server-side station-side write that never loads the
  far end's chunks.
- **Warp interlock:** warp, including an ADR-044 evacuation, is refused while a
  pair is bound. It is checked at request, at confirmation, and against live
  state inside the checked relocation. If the binding is part of the observed
  station state, a bind after confirmation makes the commit `STALE`. Because
  unbinding is always possible, a bound station is never stranded. The
  implementation adds named "bind pair" and "unbind pair" transitions to
  ADR-041's checked-update list, and extends ADR-044 §3's relocation predicate.
- **Deletion:** station deletion fails closed while an elevator transport record
  references the station, as the rocket guard does (ADR-044 §5).
- **Transport:** a separate journaled transaction, like rocket transfers. It
  never teleports on a client request, and never loads the far end's chunks from
  a client-supplied position.
- **Missing body, remapped Level, or a Level absent from the server:** the pair
  is reported invalid and nothing is deleted (fail closed, preserve).
- **Invariant:** validity is always re-derived from live state, so a crash
  between separately saved stores fails closed.
- **Open decisions for v1.7** (not fixed here):
  - the storage location (station record or a system-owned schema, per V1.7
    §8, including its share of the station registry's 4 MiB bound);
  - storing the Level key observed at bind time, with a changed mapping treated
    as invalid;
  - a never-reused pair UUID for transport journals;
  - cardinality (for example, at most one pair per station and column
    uniqueness across stations).

## Consequences

- v1.5 shows that endpoint validation composes with orbit context, star systems,
  warp and authority, without committing to storage, logistics or UI.
- No save-schema, network-protocol or API change, so nothing to migrate or roll
  back.
- ADR-044 §8's §12.5 row ("orbital weapons and elevator write interfaces: not
  applicable") now points here: the only elevator surface is read-only and
  operator-only.

## Non-goals

- Elevator blocks, structures or climbers.
- Transport of players, items, fluids or energy.
- Persistence, public API, player-facing commands and GUI.
- Warp integration beyond the constraints above.

## Rollback

Remove the command and the model; no data exists.

## Review history

- **Revision 1** (`2900e66`): an independent contract review found it in scope
  and accepted it with changes: four Medium, three Low and one Info finding.
- **Revision 2** applies all seven required changes:
  1. authority is rule 2 and tested on the pure validator;
  2. the unbind path, the warp interlock checkpoints, the deletion guard and the
     named transitions;
  3. the relation to ADR-039 and v1.7, fixed rules reframed as constraints, and
     storage left open;
  4. a leaf-level permission check, bounded coordinates, an audit line and an
     unstable surface;
  5. rule 4 (formerly 3) stated precisely;
  6. the ADR-040 pad invariant cited and the landing overlap disclosed;
  7. the extended test list and the definition of "pure".

  The report is archived in `docs/work/v1.5.0-review-closure/`. Acceptance is not
  a Gate approval.
