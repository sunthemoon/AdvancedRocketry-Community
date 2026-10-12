# ADR-073 - Copy pending chunk writes before the IO worker returns them (Mixin)

```yaml
status: ACCEPTED
date: 2026-10-12
owner: sunthemoon
deciders: [owner, Claude Root]
target_version: v1.8.0
scope: one Mixin injection into net.minecraft.world.level.chunk.storage.IOWorker
supersedes: none
```

## Acceptance

On 2026-10-12, in the interactive Claude Code session, Root asked how to handle the
vanilla chunk-save race described below. The owner chose the option labelled "Mixin
backport of Mojang's fix (Recommended)". That option's description committed Root
to write this ADR and dispatch the task. The acceptance covers only the single
injection in the Decision below. Any other Mixin needs its own ADR.

## Context

Hosted GameTest runs have failed three times with
`[IO-Worker-N/ERROR] IOWorker: Failed to store chunk [x, z]`, caused by
`java.util.ConcurrentModificationException` in `CompoundTag.write`:

- 38064322640: chunk [-13, 7], batch `endgame_gravity_station`;
- 38156329125, attempt 2: chunk [10, 7], same batch;
- 38199403721, attempt 1: chunk [-12, 7], batch `station_sky_context_wiring`.

All required GameTests passed in each of these runs. The failures affect about three of
ten recent runs. The stack shows a single `CompoundTag.write` frame directly under
`NbtIo.writeUnnamedTag`. So the concurrently changed map is the chunk's top-level tag.

The bytecode of the mapped Forge 47.4.10 jar shows the mechanism:

1. `IOWorker.loadAsync`, in lambda `lambda$loadAsync$4`, returns `pendingWrites.get(pos).data`
   directly, without a copy, when a write for that chunk is still pending.
2. `ChunkStorage.upgradeChunkTag` calls `injectDatafixingContext`, which `put`s
   `__context` on that top-level tag. It later calls `remove("__context")`.
3. When a chunk unloads and loads again before its write finishes, the main thread
   changes the same tag object that the IO thread is serializing.

Both the earlier Root investigation (`STORAGE-ALIAS-INVESTIGATION-01.md`, the
native-storage-ownership records) and Claude's analysis reached this mechanism. The
earlier work could not attribute it to a project object, because none is involved.

The effect is not limited to tests. On any 1.20.1 server, a chunk that unloads and
reloads quickly can lose that write. The chunk stays loaded, so it is normally saved
again later. A crash before that next save loses the change.

## Why a Forge event or an Access Transformer cannot fix this

- No Forge event fires inside `IOWorker.loadAsync` or between the pending-write lookup
  and `upgradeChunkTag`. `ChunkDataEvent.Load` fires after the upgrade has already
  changed the tag.
- An Access Transformer changes visibility and finality only. It cannot change what
  `loadAsync` returns.
- Replacing the `IOWorker` instance inside `ChunkStorage` would mean making a private
  final field public and writable, then running a second worker on the same region
  files. That is riskier than one narrow injection.
- Making the mod's own GameTests avoid quick unload-reload would hide the race in
  tests, but players would keep it.

## Decision

1. Add Mixin support for this one purpose:
   - the MixinGradle plugin and the Mixin annotation processor in the build;
   - one configuration file `advancedrocketrycommunity.mixins.json` (required, server
     and common side, compatibility level JAVA_17, with a refmap);
   - the `MixinConfigs` manifest attribute.
2. One Mixin class, `IOWorkerPendingReadMixin`, changes exactly one value: the
   `CompoundTag` that `IOWorker.loadAsync` returns from a pending write becomes
   `pending.data.copy()`. A null value stays null.
   - Use the narrowest injection, for example `@ModifyArg` on the
     `Optional.ofNullable` call in the pending branch, or the equivalent.
   - No other behaviour of `IOWorker` or `ChunkStorage` changes.
3. The disk-read branch and the store path are unchanged. The cost is one tag copy for
   each read of a chunk whose write is still pending, which is rare.
4. No other Mixin is added under this ADR. A future Mixin needs its own ADR, with the same
   proof that a Forge event and an Access Transformer cannot do the job.

## Verification

- A GameTest stores a tag through the level's chunk storage, reads it back while the
  write is pending, and asserts:
  - the returned tag is equal to the stored tag but is not the same object;
  - changing the returned tag does not change what is written to disk.
- The whole unit suite, `runData` and `runGameTestServer` pass.
- The hosted CI shows no new `IOWorker` CME across later runs. One run cannot prove this;
  Root tracks it in the queue.
- The packaged jar contains the Mixin configuration and refmap, and starts on a
  dedicated server.

## Consequences

- Positive: hosted CI no longer fails from this race, and players no longer lose a chunk
  write this way.
- Negative: the mod now depends on Mixin. Another mod that injects into the same method
  could conflict; the narrow injection keeps this unlikely.
- Revisit when Forge or Minecraft is upgraded. Remove the Mixin if the target version
  already copies pending data.
