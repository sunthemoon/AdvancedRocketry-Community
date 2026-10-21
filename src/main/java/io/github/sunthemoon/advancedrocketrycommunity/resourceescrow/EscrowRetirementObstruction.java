package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;

/** Initial-process obstacles for R52's native cuts; restarts remove the injected storage decorator. */
final class EscrowRetirementObstruction {
    private boolean installed;
    boolean install(MinecraftServer server, String cut, RocketTransferRecord record) {
        if (installed) { return true; }
        EscrowTestEndpoints.requireEnabled(server);
        var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                record.sourceSnapshot().sourceDimension()));
        var origin = record.sourceSnapshot().sourceOrigin(); var column = new ChunkPos(origin.x() >> 4, origin.z() >> 4);
        if (cut.endsWith("HELD_STORAGE")) {
            var destination = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    record.destinationSnapshot().sourceDimension()));
            destination.entityManager.permanentStorage = new ForeignStorage(destination.entityManager.permanentStorage);
        } else {
            if (!level.areEntitiesLoaded(column.toLong())) { return false; }
            boolean offColumn = cut.endsWith("HELD_COLUMN");
            if (offColumn) {
                var margin = new ChunkPos(column.x - 1, column.z);
                level.getChunkSource().addRegionTicket(FluidFuelLoaderEscrowFixtures.AREA, margin, 2, record.transferId());
                level.getChunk(margin.x, margin.z);
                if (!level.areEntitiesLoaded(margin.toLong())) { return false; }
            }
            if (level.getEntity(record.sourceEntityId()) != null) { installed = true; return true; }
            var copy = ModEntities.ROCKET.get().create(level);
            EscrowTags.require(copy != null, "R52 old copy creation failed");
            copy.initializeTransferred(record.sourceSnapshot(), record.logicalRocketId(), record.ownerId(), record.sourceFlightData());
            copy.setUUID(record.sourceEntityId()); copy.setPos(origin.x() + (offColumn ? -.5 : .5), origin.y(), origin.z() + .5);
            EscrowTags.require(level.addFreshEntity(copy), "R52 old copy publication failed");
            EscrowTags.require(server.saveEverything(false, true, true), "R52 old copy baseline save failed");
        }
        installed = true; return true;
    }

    private record ForeignStorage(EntityPersistentStorage<Entity> delegate) implements EntityPersistentStorage<Entity> {
        public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) { return delegate.loadEntities(pos); }
        public void storeEntities(ChunkEntities<Entity> entities) { delegate.storeEntities(entities); }
        public void flush(boolean sync) { delegate.flush(sync); }
        public void close() throws IOException { delegate.close(); }
    }
}
