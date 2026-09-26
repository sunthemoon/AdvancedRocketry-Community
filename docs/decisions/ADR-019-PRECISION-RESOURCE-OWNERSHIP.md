# ADR-019 — Precision Assembler resource ownership at the crash boundary

```yaml
status: ACCEPTED
date: 2026-09-26
deciders: [sunthemoon]
accepted_at: 2026-09-26
owner: sunthemoon
target_version: v1.2.0
amends: ADR-016 Precision Assembler resource placement and port removal
supersedes: ""
```

## Context

The controller persists `arce_process` and `arce_process_journal` in its chunk,
while eight physical ports persist resources in their own chunks. A valid saved
journal lets `V120-PREC-04A` reconcile mixed before/after Item ports. It cannot
recover a port chunk saved after a batch if the controller chunk still contains
the pre-journal state. `BlockEntity.setChanged()` only marks a chunk for saving;
Forge's `ChunkDataEvent.Save` is emitted before the serialized chunk is handed
to the storage writer. Neither establishes a durable journal-before-port order.

A single `save-all flush` fixture verifies one completed checkpoint, not every
interruption point. Starting another batch can also overwrite the only retained
journal while a different chunk still contains an older resource snapshot.

## Decision

Make the controller chunk authoritative for all seven Item slots, process state,
resource revision and transaction journal. Physical Item ports remain
generation-scoped capability/menu facades and never persist a second active
resource copy. The controller stores an independently versioned, bounded
`arce_precision_resources` root (schema 1). The existing process and journal
roots and public machine IDs do not change. A batch updates only one chunk's
BlockEntity data; the journal state, before/after Item state and applied marker
are serialized in the same chunk snapshot. The Energy port remains physically
persistent and per-tick Energy remains a separate process step; this decision
does not claim arbitrary-crash atomicity for external Energy transfers.

No capability query or migration may load an unavailable chunk. Formed-machine
operations still require the exact loaded port set and binding generation.
Breaking a formed Item port removes its controller-owned slot and spawns that
stack at the controller position. Spawned Item entities use Minecraft's entity
persistence, not the controller's BlockEntity snapshot; block-break drops are
not part of the Item-batch crash-atomicity claim. Structural mismatch or
temporary unload keeps resources in the controller. Breaking the controller
drops its remaining Item
slots there once; the physical Energy port retains its Energy. These removal
semantics amend ADR-016's physical-port inventory placement.

## Legacy save transition

- A newly placed controller starts with an empty active resource root. A loaded
  controller without that root is legacy; its physical port roots remain
  authoritative and execution/capabilities pause until migration finishes.
- Migration requires all seven exact bound Item ports loaded with supported
  legacy schemas. It copies their complete Item resources into a controller root in a
  `PREPARING` phase without clearing the old roots. A completed save barrier
  must make that controller snapshot durable before any port marker changes.
- Each old port receives a bounded, schema-versioned migration marker tied to
  the machine UUID and assigned channel. Once a second completed save barrier
  covers those markers, the controller may become `ACTIVE`. Old port resource
  roots remain preserved but inert; they cannot be exposed or dropped again.
- Restart in `PREPARING` resumes from the controller snapshot and verifies every
  loaded marker/root before continuing. Unknown or invalid roots, conflicting
  ownership, unavailable chunks or a failed save barrier stop migration without
  deleting data. An old interrupted transaction must be recovered or blocked
  by its existing journal rules before active processing resumes.
- An old save already inconsistent from an interruption before this change
  cannot be reconstructed from absent journal data. Migration preserves its
  observed data and does not label that historical state exact-once.

The marker is an additive persistence identity, not a reinterpretation of
`arce_precision_port` schema 1. The frozen roots are
`arce_precision_resources` schema 1, at most 65,536 NBT bytes, and
`arce_precision_port_migration` schema 1, at most 1,024 NBT bytes. Migration
calls `ServerChunkCache.save(true)` for each level-chunk writer flush; at most
four candidates per server tick share two flushes per level. Forge 47.4.10's
`ChunkMap.save(ChunkAccess)` catches per-chunk `Exception`, logs it and returns
`false`, so a normal `save(true)` return alone does not authorize activation.
After each flush, a read-only Anvil reader confirms the controller's exact
saved identity, process, journal and Item roots; after the second flush it also
confirms all seven Item port bindings, legacy shadows and migration markers. The
physical Energy port binding/resource root is confirmed as well: binding repair
can affect its separate chunk, even though Energy ownership does not move. Each
distinct chunk is read once per barrier, with a 1 MiB compressed and 4 MiB
decoded NBT limit; missing, malformed, oversized or mismatched data keeps the
controller in `PREPARING`. Record headers and payloads must be complete, but
unused padding in the last allocated sector is not required: a live vanilla
region file adds that padding on close, not on flush. A save event or `setChanged()` alone is not a
barrier. Readback covers stopped-process ordering after the writer flush, not
an unproven hardware power-loss/fsync guarantee.

A failed chunk serialization can clear Minecraft's dirty flag before throwing.
Each validated migration attempt therefore marks its controller dirty before
the first barrier, and marks all seven matching Item ports dirty before the
second barrier even if their markers already exist. Attempts also mark the
validated Energy port dirty before that barrier, so a
failed save of its repaired binding cannot activate an unsaved generation.
Explicit event-driven retries can then recover after a transient failure without unrelated world
changes; failures do not start an unrestricted per-tick retry loop.

### Removal while ownership is unresolved

Until migration is `ACTIVE`, a `PREPARING` controller and its bound ports defer
ordinary player, explosion and Forge-aware entity destruction. The player sees
a load/repair/migration message. A port with an unavailable bound controller also
defers removal without loading that chunk; its own marker cannot distinguish all
interrupted migration phases. Unsupported controller/port roots stay protected
instead of being discarded through normal removal.

An unbound Item port with legacy contents or a migration marker checks at most
40 prospective controller positions derived from the fixed layout and eight
transforms. It defers removal for a pending nearby owner or an unavailable
candidate chunk, without loading it. This covers a saved binding that predates
the controller snapshot. An empty, unmarked facade needs no such restriction:
it holds no resource copy and can be replaced for the bounded binding repair.

Structural mismatch or an unavailable pattern retains pending port bindings and
part positions using the existing `BINDING_CONFLICT` state (`WAITING_UNLOADED`
for unloaded cells). Validation diagnostics remain available. Ownership transfer
is refused until migration finishes; repairing the same structure/definition
allows the original binding set to validate and migration to resume. This is a
temporary Precision migration restriction, not a new schema or a change to other
machines' unbinding policy. The bound Energy port is protected during this interval
because the complete structure is needed to finish migration; Energy ownership
does not move.

A saved `PREPARING` controller can precede an older or missing port binding on
disk. Recovery may restore the controller's existing generation only after all
eight fixed port positions are loaded and supported, all existing bindings are
absent or belong to that exact controller/UUID with no newer generation, and
every Item shadow/marker agrees with the prepared snapshot. This bounded,
idempotent repair changes no resources or markers and does not adopt another
owner's port. Its saved result is checked by the existing migration barriers.

An untouched legacy controller without a central resource snapshot remains
removable: its unmarked physical Item roots still own the resources. `ACTIVE`
controllers and their inert leftover ports keep the normal removal behavior above.
Explosion protection suppresses both block loot and block replacement; refusing
only replacement would still duplicate the block item.

Direct administrative `setblock`/`fill`, raw `Level.destroyBlock` or third-party
world replacements bypass these gameplay hooks and are not safe migration-removal
APIs. Datapacks that make machine blocks Enderman-holdable likewise bypass the
ordinary entity hook. No recovery from deliberate direct replacement is claimed.

## Rejected alternatives

- Force every cross-chunk batch to call a global synchronous save. This keeps
  port ownership but adds a full-world save to routine machine throughput.
- Retain only the latest journal in every port. It cannot recover an arbitrary
  old chunk after multiple batches without a bounded history plus checkpoints.
- Disallow formation at chunk boundaries. This changes valid structures and
  avoids rather than solves the persistence contract.
- Treat mismatched saved ports as a successful batch. This can duplicate or
  lose items and cannot satisfy the resource-conservation requirement.

## Required evidence before `V120-PREC-04B` can be verified

1. Supported/future/invalid NBT round-trip, maximum-size and clean old-save
   migration tests, including both migration barriers and marker replay.
2. Fault-injected controller/port saved snapshots at every transaction and
   migration phase, with no second Item batch or silent resource overwrite.
3. Port/controller break, unload, re-form, capability epoch and cross-chunk
   GameTests, including restart with old inert port roots.
4. A short packaged same-world forced-stop/restart fixture with build and save
   hashes; the later full-content acceptance campaign remains under ADR-018.

Acceptance authorizes implementation, not a Gate result. Until these checks
pass, `V120-PREC-04B` remains in progress.
