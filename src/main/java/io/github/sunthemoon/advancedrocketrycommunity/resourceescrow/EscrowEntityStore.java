package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;

/** Decorates the existing entity storage. Never constructs or replaces its IOWorker. */
final class EscrowEntityStore implements EntityPersistentStorage<Entity> {
    private final ServerLevel level;
    private final EntityPersistentStorage<Entity> delegate;
    private final EntityStorage original;
    private final EscrowSaveMonitor monitor;
    private Function<ChunkEntities<Entity>, CompoundTag> capture;
    private boolean capturing;
    private CompletableFuture<Void> ownStore;

    EscrowEntityStore(ServerLevel level, EscrowSaveMonitor monitor) {
        this.level = level; this.monitor = monitor; delegate = level.entityManager.permanentStorage;
        if (!(delegate instanceof EntityStorage storage)) { throw new IllegalStateException("Unsupported entity storage decorator"); }
        original = storage; level.entityManager.permanentStorage = this;
    }
    EntityStorage original() { return original; }
    CompletableFuture<Void> captureAndStore(ChunkPos pos, Function<ChunkEntities<Entity>, CompoundTag> serializer) {
        EscrowTags.require(!capturing && level.areEntitiesLoaded(pos.toLong()), "Entity sections are not fully loaded or capture is reentrant");
        capturing = true; capture = serializer; ownStore = null;
        try {
            boolean stored = level.entityManager.storeChunkSections(pos.toLong(), entity -> { });
            EscrowTags.require(stored && ownStore != null, "Entity manager refused or did not capture a store");
            return ownStore;
        } finally { capturing = false; capture = null; ownStore = null; }
    }
    @Override public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) { return delegate.loadEntities(pos); }
    @Override public void storeEntities(ChunkEntities<Entity> entities) {
        if (capture == null) {
            // Including empty saves that vanilla suppresses through emptyChunks.
            monitor.observed(level.dimension().location().toString(), true, entities.getPos());
            delegate.storeEntities(entities); return;
        }
        var serializer = capture; capture = null; // Nested vanilla stores during serialization must be observed, not mistaken for ours.
        CompoundTag tag = serializer.apply(entities);
        ownStore = original.worker.store(entities.getPos(), tag);
        if (tag == null) { original.emptyChunks.add(entities.getPos().toLong()); }
        else { original.emptyChunks.remove(entities.getPos().toLong()); }
    }
    @Override public void flush(boolean force) { delegate.flush(force); }
    @Override public void close() throws IOException { delegate.close(); }
}
