package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.level.ChunkDataEvent;

/** Capture, event, validation and submission are one uninterrupted owner-thread call (C1). */
public final class EscrowChunkStore {
    public record Capture(CompletableFuture<Void> stored, IOWorker worker, Path directory, ChunkPos chunk,
                           AtomicLong generation, long capturedGeneration, int bytes, Runnable restoreDirty,
                           Runnable releaseWatch) { }
    private final ServerLevel level;
    private final EscrowSaveMonitor monitor;
    private EscrowEntityStore entities;

    public EscrowChunkStore(ServerLevel level, EscrowSaveMonitor monitor) { this.level = level; this.monitor = monitor; }
    public Capture captureAndStore(EscrowEndpoint endpoint, Predicate<CompoundTag> validator) {
        EscrowTags.require(level.getServer().isSameThread() && level.dimension().location().toString().equals(endpoint.level()), "Wrong endpoint thread/Level");
        boolean entity = endpoint.kind() == EscrowEndpoint.Kind.ENTITY;
        ChunkPos pos = entity ? new ChunkPos(endpoint.x(), endpoint.z())
                : new ChunkPos(new net.minecraft.core.BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
        AtomicLong generation = monitor.watch(endpoint.level(), entity, pos);
        long capturedGeneration = generation.get(); // BEFORE serialization/event, never after store.
        if (entity) {
            try { return entities(endpoint, pos, generation, capturedGeneration, validator); }
            catch (RuntimeException failure) { monitor.release(endpoint.level(), true, pos); throw failure; }
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) { monitor.release(endpoint.level(), false, pos); throw new IllegalArgumentException("Endpoint terrain chunk is not FULL loaded"); }
        Runnable restore = () -> {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == chunk) { chunk.setUnsaved(true); }
        };
        try {
            level.getPoiManager().flush(pos);
            chunk.setUnsaved(true); chunk.setUnsaved(false); // Same clear-before-serialization ordering as ChunkMap.save.
            CompoundTag tag = ChunkSerializer.write(level, chunk);
            monitor.postOwn(new ChunkDataEvent.Save(chunk, level, tag));
            EscrowTags.bound(tag, AnvilRecordReader.DECODED_BYTES, 64, 262_144);
            tag = tag.copy(); // Event listeners may retain their tag; only this detached root goes to the worker.
            int bytes = validate(tag, validator);
            EscrowTags.require(generation.get() == capturedGeneration
                    && level.getChunkSource().getChunkNow(pos.x, pos.z) == chunk, "Capture superseded before store");
            CompletableFuture<Void> stored = level.getChunkSource().chunkMap.worker.store(pos, tag);
            var map = level.getChunkSource().chunkMap;
            if (map.legacyStructureHandler != null) { map.legacyStructureHandler.removeIndex(pos.toLong()); }
            map.markPosition(pos, chunk.getStatus().getChunkType());
            return new Capture(stored, map.worker, directory(false), pos, generation, capturedGeneration, bytes, restore,
                    () -> monitor.release(endpoint.level(), false, pos));
        } catch (RuntimeException failure) { restore.run(); monitor.release(endpoint.level(), false, pos); throw failure; }
    }
    private Capture entities(EscrowEndpoint endpoint, ChunkPos pos, AtomicLong generation, long capturedGeneration,
                             Predicate<CompoundTag> validator) {
        EscrowTags.require(level.areEntitiesLoaded(pos.toLong()), "Endpoint entity sections are not loaded");
        if (entities == null) { entities = new EscrowEntityStore(level, monitor); }
        int[] bytes = {0};
        CompletableFuture<Void> stored = entities.captureAndStore(pos, captured -> {
            CompoundTag root = NbtUtils.addCurrentDataVersion(new CompoundTag());
            root.putIntArray("Position", new int[] {pos.x, pos.z}); ListTag list = new ListTag();
            captured.getEntities().forEach(entity -> serialize(entity, list)); root.put("Entities", list);
            bytes[0] = validate(root, validator);
            EscrowTags.require(generation.get() == capturedGeneration, "Entity capture superseded before store");
            return root;
        });
        return new Capture(stored, entities.original().worker, directory(true), pos, generation, capturedGeneration, bytes[0], () -> { },
                () -> monitor.release(endpoint.level(), true, pos));
    }
    private static void serialize(Entity entity, ListTag list) {
        CompoundTag tag = new CompoundTag();
        // The manager has already selected all shouldBeSaved entities; a false save must not silently omit one.
        EscrowTags.require(entity.save(tag), "A saveable entity refused serialization"); list.add(tag);
        EscrowTags.require(BoundedNbt.fits(list, AnvilRecordReader.DECODED_BYTES, 64, 262_144), "Entity capture limit exceeded");
    }
    private static int validate(CompoundTag tag, Predicate<CompoundTag> validator) {
        EscrowTags.bound(tag, AnvilRecordReader.DECODED_BYTES, 64, 262_144);
        EscrowTags.require(validator.test(tag), "Captured endpoint identity/frontier mismatch");
        return RocketNbtSize.uncompressedBytes(tag);
    }
    private Path directory(boolean entities) {
        Path world = level.getServer().getWorldPath(LevelResource.ROOT);
        return net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(), world)
                .resolve(entities ? "entities" : "region");
    }
}
