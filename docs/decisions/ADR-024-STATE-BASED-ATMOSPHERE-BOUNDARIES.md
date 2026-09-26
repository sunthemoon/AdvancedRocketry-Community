# ADR-024 — State-based atmosphere boundary registration

```yaml
status: ACCEPTED
date: 2026-09-27
deciders: [sunthemoon]
owner: sunthemoon
target_version: v1.3.0
api_version: "1.2"
authorization: maintainer instruction to use recommended solutions
```

## Context

The loaded-world atmosphere adapter currently uses sealing tags, built-in door
states, permeable tags, fluids, exposed air and full collision shapes. There is
no external registration boundary. Existing room scans are budgeted and cached;
running arbitrary third-party callbacks per visited cell would add unbounded
work to their hot path. Oxygen equipment is a separate resource-mutation contract.

## Decision

Add four types under `api.atmosphere`, exported in the API classifier and normal
host with identical runtime bytes; increase the API minor to 1.2 under ADR-021:

- `AtmosphereBoundary`: enum `DEFAULT`, `SEALED`, `PERMEABLE`.
- `AtmosphereBoundaryProvider`: functional `AtmosphereBoundary classify(BlockState state)`.
- `AtmosphereBoundaryRegistrar`: functional `void register(ResourceLocation providerId,
  Set<ResourceLocation> blockIds, AtmosphereBoundaryProvider provider)`.
- `RegisterAtmosphereBoundariesEvent`: final MOD-bus event, constructor taking a
  registrar for host/tests, and the same forwarding `register` method. Construction
  alone does not grant access to a host registry.

The host emits one owner-bound synchronous event per mod container during queued
common setup on both physical client and dedicated server, after block registration
and before world services are installed.
Register listeners in mod construction. Retained handles, another owner's handle,
off-thread calls, reentrant registration during compilation and calls after
freeze/close are rejected. IDs use the receiving
mod's namespace; referenced blocks may belong to another mod. Unknown blocks,
duplicate provider IDs and overlapping block claims fail loading, not last-writer-wins.

## Compilation and finite work

Rules are pure functions of immutable BlockState. They must not read world state,
time, external mutable state, files or networks, mutate registries, or retain
mutable world references. The host evaluates every possible state of each claimed
block at registration in sorted block-ID order, then retains only the resulting
immutable state table, never the provider callback. `DEFAULT` uses legacy logic;
`SEALED` stops a scan at that cell; `PERMEABLE` allows traversal.
Dynamic tag queries (including `BlockState.is(TagKey)`) are not pure state: tags
are unavailable during compilation and can change on reload. They belong to the
host's runtime priority layer, not provider callbacks.

Bounds: 256 providers, 64 block IDs per provider, 1,024 total block IDs, 4,096
states per registration and 16,384 total states. IDs are at most 255 characters.
Null/invalid results, thrown runtime exceptions, a callback returning after more
than 5 ms or a registration's accumulated callback time exceeding 1 second reject
that entire registration without adding claims or state entries. The registry
remains bounded even if an extension catches a rejection and retries. Loading
reports the provider/block identity without logging arbitrary provider data.

Timing checks apply only after callbacks return; they cannot preempt a callback
that never returns. This is not a sandbox for arbitrary same-JVM mod code. No
callbacks execute during world scans, ticks, reloads or chunk lifecycle changes.

## Runtime precedence and invalidation

Keep existing priority: outside-build-height/unloaded guards, sealing tag,
built-in door/trapdoor/fence-gate state, permeable tag, then the registered rule,
then existing fluid/air/collision fallback. Thus higher-priority legacy rules
remain authoritative, even for a registered block. No caller supplies positions,
worlds or BlockEntities through this API and no callback can request a chunk.

Custom blocks express changing airtightness in their BlockState and must issue
ordinary neighbor notifications when that state or block changes. With external
boundaries installed, neighbor notifications invalidate affected room authority
synchronously before bounded rescanning, including affected in-flight scans and
unconsumed completed results, even with a dirty-queue backlog or after a registered
block has been replaced. Registered-block interaction also invalidates that position.
Datapack/tag reload revokes cached classifications and all pending scan results;
the host uses the reload-wide server datapack-sync notification on the owning
server thread, not client packet tag updates or individual player login sync.
startup/restart rebuilds
the same table from the installed providers. Invalidation does not scan the world
or create chunk tickets. Existing dirty-queue, vent and inspection caps are retained.

The immutable catalog contains only block states/enum values, no levels or players.
Per-level cached volumes remain manager-owned and clear at server stop. No new
persistent data, opaque provider payload, migration or packet is introduced.
Uninstalling a provider returns retained block states to legacy classification on
the next startup; unknown missing blocks remain subject to normal Forge behavior.

## Verification and boundaries

Unit tests cover ownership/conflicts/bounds, atomic rejection, thread/window
closure, reentrancy, callback timing and stable compiled values. GameTests use the actual
vent/loaded-world adapter for custom state transitions, tags/legacy rules, reload
invalidation, dirty-backlog cancellation and unloaded guards. The independently built fixture uses only
API/platform classes; packaged startup/restart verifies actual loading and room
behavior. A startup-only fixture fault may verify rejected registration.

This slice does not freeze equipment/oxygen providers, arbitrary world-aware
boundary callbacks, new environmental hazards or later public APIs. It does not
change historical Gate evidence or ADR-018's deferred full-test schedule.

## Authorization and review

Accepted using the maintainer's instruction to apply recommended solutions.
Independent read-only contract review requested synchronous scan-result revocation,
reentrancy rejection, dynamic-tag exclusion and explicit physical-side loading;
these are part of this decision before downstream implementation. This accepts the
API contract, not a release or any outstanding Required Gate.
