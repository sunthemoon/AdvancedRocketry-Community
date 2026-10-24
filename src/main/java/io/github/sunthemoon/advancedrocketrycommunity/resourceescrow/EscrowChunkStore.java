package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
    static final class SupersededCapture extends IllegalArgumentException {
        SupersededCapture() { super("Capture superseded before store"); }
    }
    /** A capture or watch refused by an escrow bound, never truncated; reported as BARRIER_BOUNDS. */
    static final class CaptureBounds extends IllegalArgumentException {
        CaptureBounds(String message) { super(message); }
    }
    public record Capture(CompletableFuture<Void> stored, IOWorker worker, Path directory, ChunkPos chunk,
                           AtomicLong generation, long capturedGeneration, int bytes, Runnable restoreDirty,
                           Runnable releaseWatch) { }
    private final ServerLevel level;
    private final EscrowSaveMonitor monitor;
    private final EscrowStoreFault fault = new EscrowStoreFault();
    private EscrowEntityStore entities;

    public EscrowChunkStore(ServerLevel level, EscrowSaveMonitor monitor) { this.level = level; this.monitor = monitor; }
    IOWorker entityWorkerForTests() {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        if (entities == null) { entities = new EscrowEntityStore(level, monitor); }
        return entities.original().worker;
    }
    public Capture captureAndStore(EscrowEndpoint endpoint, Predicate<CompoundTag> validator) {
        return captureAndStore(endpoint, validator, new AtomicBoolean(true));
    }
    Capture captureAndStore(EscrowEndpoint endpoint, Predicate<CompoundTag> validator, AtomicBoolean lifetime) {
        EscrowTags.require(level.getServer().isSameThread() && level.dimension().location().toString().equals(endpoint.level()), "Wrong endpoint thread/Level");
        boolean entity = endpoint.kind() == EscrowEndpoint.Kind.ENTITY;
        ChunkPos pos = entity ? new ChunkPos(endpoint.x(), endpoint.z())
                : new ChunkPos(new net.minecraft.core.BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
        var watch = monitor.captureWatch(endpoint.level(), entity, pos, lifetime);
        AtomicLong generation = watch.generation();
        long capturedGeneration = watch.capturedGeneration(); // BEFORE serialization/event, never after store.
        if (entity) {
            try { return entities(pos, watch, validator); }
            catch (RuntimeException | Error failure) { watch.release(); throw failure; }
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) { watch.release(); throw new IllegalArgumentException("Endpoint terrain chunk is not FULL loaded"); }
        Runnable restore = () -> {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) == chunk) { chunk.setUnsaved(true); }
        };
        try {
            level.getPoiManager().flush(pos);
            chunk.setUnsaved(true); chunk.setUnsaved(false); // Same clear-before-serialization ordering as ChunkMap.save.
            CompoundTag tag = ChunkSerializer.write(level, chunk);
            monitor.postOwn(new ChunkDataEvent.Save(chunk, level, tag));
            bounded(tag);
            tag = tag.copy(); // Event listeners may retain their tag; only this detached root goes to the worker.
            int bytes = validate(tag, validator);
            unchanged(generation, capturedGeneration);
            EscrowTags.require(level.getChunkSource().getChunkNow(pos.x, pos.z) == chunk, "Captured terrain chunk changed before store");
            // W107C: inert unless cut E1's gate is open; vanilla's store itself is always submitted unchanged.
            CompletableFuture<Void> stored = fault.observed(level, endpoint.level(), pos, level.getChunkSource().chunkMap.worker.store(pos, tag));
            var map = level.getChunkSource().chunkMap;
            if (map.legacyStructureHandler != null) { map.legacyStructureHandler.removeIndex(pos.toLong()); }
            map.markPosition(pos, chunk.getStatus().getChunkType());
            return new Capture(stored, map.worker, directory(false), pos, generation, capturedGeneration, bytes, restore,
                    watch::release);
        } catch (RuntimeException | Error failure) { restore.run(); watch.release(); throw failure; }
    }
    boolean ordinaryAvailable(String id, ChunkPos pos) { return monitor.ordinaryAvailable(id, pos); }
    EscrowSaveMonitor.Handle ordinaryWatch(String id, ChunkPos pos, java.util.function.BooleanSupplier reserve) {
        return monitor.ordinaryWatch(id, pos, reserve);
    }
    /** W98: an ordinary entity-slot capture, the same path as an ENTITY endpoint's, with no escrow identity. */
    Capture captureEntitySlot(ChunkPos pos, Predicate<CompoundTag> validator, EscrowSaveMonitor.Handle watch) {
        EscrowTags.require(level.getServer().isSameThread(), "Wrong entity-slot thread");
        return entities(pos, watch, validator);
    }

    /**
     * W98 (W96-R5(c), W98-R11): true only for the exact vanilla entity storage, or this store's decorator over it. Another
     * mod's subclass may override {@code storeEntities}, which the own store bypasses, so it leaves no own-store proof.
     */
    public boolean entityCaptureSupported() {
        var storage = level.entityManager.permanentStorage;
        return entities != null ? storage == entities && vanillaEntityStorage(entities.original().getClass())
                : vanillaEntityStorage(storage.getClass());
    }

    static boolean vanillaEntityStorage(Class<?> storage) { return storage == net.minecraft.world.level.chunk.storage.EntityStorage.class; }

    private Capture entities(ChunkPos pos, EscrowSaveMonitor.Handle watch, Predicate<CompoundTag> validator) {
        AtomicLong generation = watch.generation(); long capturedGeneration = watch.capturedGeneration();
        EscrowTags.require(level.areEntitiesLoaded(pos.toLong()), "Endpoint entity sections are not loaded");
        if (entities == null) { entities = new EscrowEntityStore(level, monitor); }
        int[] bytes = {0};
        CompletableFuture<Void> stored = entities.captureAndStore(pos, captured -> {
            CompoundTag root = NbtUtils.addCurrentDataVersion(new CompoundTag());
            root.putIntArray("Position", new int[] {pos.x, pos.z}); var list = new EntityList();
            captured.getEntities().forEach(entity -> list.add(serialize(entity))); root.put("Entities", list.tag());
            bytes[0] = validate(root, validator);
            unchanged(generation, capturedGeneration);
            return root;
        });
        return new Capture(stored, entities.original().worker, directory(true), pos, generation, capturedGeneration, bytes[0], () -> { },
                watch::release);
    }
    private static CompoundTag serialize(Entity entity) {
        CompoundTag tag = new CompoundTag();
        // The manager has already selected all shouldBeSaved entities; a false save must not silently omit one.
        EscrowTags.require(entity.save(tag), "A saveable entity refused serialization"); return tag;
    }
    /** Bounds each entity tag once against the remaining envelope, so capture cost is linear in the chunk's entities. */
    static final class EntityList {
        private final ListTag tag = new ListTag();
        private long bytes = 8; // Named root header plus the list's element type and length, as in a whole-list check.
        private int nodes = 1;
        void add(CompoundTag entity) {
            // A list element has no 3-byte named header; the whole root is still checked once by validate().
            var size = BoundedNbt.measure(entity, (int) (AnvilRecordReader.DECODED_BYTES - bytes + 3), 62, 262_144 - nodes);
            if (size == null) { throw new CaptureBounds("Entity capture limit exceeded"); }
            bytes += size.bytes() - 3; nodes += size.nodes(); tag.add(entity);
        }
        ListTag tag() { return tag; }
    }
    private static void unchanged(AtomicLong generation, long captured) {
        if (generation.get() != captured) { throw new SupersededCapture(); }
    }
    private static void bounded(CompoundTag tag) {
        if (!BoundedNbt.fits(tag, AnvilRecordReader.DECODED_BYTES, 64, 262_144)) { throw new CaptureBounds("Endpoint capture exceeds the decoded record bounds"); }
    }
    private static int validate(CompoundTag tag, Predicate<CompoundTag> validator) {
        bounded(tag);
        EscrowTags.require(validator.test(tag), "Captured endpoint identity/frontier mismatch");
        return RocketNbtSize.uncompressedBytes(tag);
    }
    private Path directory(boolean entities) {
        Path world = level.getServer().getWorldPath(LevelResource.ROOT);
        return net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(), world)
                .resolve(entities ? "entities" : "region");
    }
}
