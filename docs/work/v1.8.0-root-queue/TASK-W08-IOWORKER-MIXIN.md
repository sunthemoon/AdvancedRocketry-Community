# TASK W08: IOWorker pending-read copy (ADR-073)

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.
Implementer: a headless Claude session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w08-ioworker-mixin` |
| Branch | `codex/v1.8.0-w08-ioworker-mixin` (local only; Root merges) |
| Decision | [ADR-073](../../decisions/ADR-073-V180-IOWORKER-PENDING-READ-COPY.md), accepted by the owner on 2026-10-12 |
| Record | `docs/work/v1.8.0-root-queue/records/W08.md` |

## Goal

Stop the vanilla 1.20.1 race in which the IO worker hands a pending write's tag to the
chunk loader, which then changes it while it is being written. The fix is one narrow Mixin
injection that returns a copy, exactly as ADR-073 decides. Read ADR-073 in full first.

## Root's authorization for central files

This task may change `build.gradle`, and `gradle.properties` if a version property is
needed, but only to add Mixin support:
- the MixinGradle plugin, with its plugin repository;
- the Mixin annotation processor dependency;
- the `mixin { add sourceSets.main, '<refmap>'; config '<file>' }` block;
- the `MixinConfigs` jar manifest attribute.

It may also add `src/main/resources/advancedrocketrycommunity.mixins.json`. Change nothing
else in the build, the workflow or the status files. List every build change in your
record.

## Scope

1. **Build:**
   - Follow the standard ForgeGradle 6 and MixinGradle setup for Forge 1.20.1 with Mixin
     0.8.5.
   - The first Gradle run must download the new plugin and processor, so run it without
     `--offline` once. Later runs may use `--offline`.
   - Make sure the refmap is generated, and that the built jar contains both the refmap and
     the configuration.
2. **Mixin:**
   - `IOWorkerPendingReadMixin` in a new package `...advancedrocketrycommunity.mixin`.
   - It targets `net.minecraft.world.level.chunk.storage.IOWorker`. In the pending-write
     branch of `loadAsync`, the returned `CompoundTag` becomes `data == null ? null :
     data.copy()`.
   - Use the narrowest injection that works with the mapped and SRG names, and verify the
     target by bytecode:
     - the lambda is `lambda$loadAsync$4` in the mapped 47.4.10 jar;
     - the pending-branch value is `IOWorker$PendingStore.data`, passed to
       `Optional.ofNullable`.
   - Add a short comment that cites ADR-073.
3. **Configuration:** `advancedrocketrycommunity.mixins.json`:
   - required true;
   - package `...mixin`;
   - compatibility level `JAVA_17`;
   - the refmap;
   - the Mixin class listed in `mixins` (it is common, not client-only);
   - `minVersion` 0.8.

## Tests (required)

- A GameTest:
  1. obtain the level's `ChunkMap`;
  2. store a small, valid tag for an unused, far-away chunk position through the worker;
  3. read it back with `ChunkMap.read(pos)` or the worker, while the write is still
     pending (call immediately and join);
  4. assert the result equals the stored tag and is **not the same object**;
  5. change the returned tag;
  6. flush the worker and read again: the written data does not contain the change.

  Use a far-away position so no real chunk is touched, and clean up if the test needs to.
- The whole unit suite once. At the end: `runData` and `runGameTestServer` (under the
  lock), then the log checker. Report the GameTest totals.
- Build the jar (`./gradlew jar`) and confirm that it contains
  `advancedrocketrycommunity.mixins.json`, the refmap and the `MixinConfigs` manifest entry.

## Not in this task

- Any other Mixin, Access Transformer or coremod.
- Changes to `GuardedChunkSaves` or the log manifest.
- Status files and the ledger.
