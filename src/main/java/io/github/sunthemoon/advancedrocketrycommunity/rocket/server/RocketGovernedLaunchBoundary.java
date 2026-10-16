package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.HashSet;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Final F1/F2 observations. Never loads a chunk, adds a ticket, elects or discards a candidate. */
final class RocketGovernedLaunchBoundary {
    private RocketGovernedLaunchBoundary() { }

    static boolean allows(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                          RocketEntity source, RocketTransferBayDispatch bays) {
        return allows(server, journal, record, source, null, bays, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
    }

    static boolean allows(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                          RocketEntity source, RocketEntity arrival, RocketTransferBayDispatch bays) {
        return allows(server, journal, record, source, arrival, bays, RocketTransferBayRecoveryGate.Action.SPAWN_DESTINATION);
    }

    private static boolean allows(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                          RocketEntity source, RocketEntity arrival, RocketTransferBayDispatch bays, RocketTransferBayRecoveryGate.Action action) {
        if (!server.isSameThread()) { return false; }
        var bay = bays.before(server, journal, record, action);
        // Credited bay-source consumption is W27f, not an escrow exemption for a bay permit.
        if (bay == null || bay.kind() != RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED
                || record.nativeTransfer().isEmpty() || !source.isAlive() || !source.isAddedToWorld()
                || !source.operational() || !record.sourceEntityId().equals(source.getUUID())) { return false; }
        if (arrival != null && (!arrival.isAlive() || !arrival.isAddedToWorld() || !arrival.operational()
                || !record.nativeTransfer().orElseThrow().armed().destination().equals(arrival.getUUID()))) { return false; }
        var transactions = RocketTransactionSavedData.get(server);
        if (!transactions.operational() || transactions.entries().stream().anyMatch(entry ->
                entry.record().region().overlaps(RocketRegion.fromSnapshot(record.sourceSnapshot()))
                || entry.record().region().overlaps(RocketRegion.fromSnapshot(record.destinationSnapshot())))) { return false; }
        if (!RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record)) { return false; }
        var candidates = new HashSet<RocketEntity>();
        var destination = record.nativeTransfer().orElseThrow().armed().destination();
        for (var snapshot : List.of(record.sourceSnapshot(), record.destinationSnapshot())) {
            var level = RocketTransferEntities.level(server, snapshot.sourceDimension());
            if (level == null) { return false; }
            // Inspect fixed UUIDs even when an endpoint has moved outside its recorded slot.
            for (var id : List.of(record.sourceEntityId(), destination)) {
                var entity = level.getEntity(id);
                if (entity != null && !(entity instanceof RocketEntity)) { return false; }
                if (entity instanceof RocketEntity rocket) { candidates.add(rocket); }
            }
            var region = RocketRegion.fromSnapshot(snapshot);
            var box = new AABB(region.minimum().x() - 1.0, level.getMinBuildHeight(), region.minimum().z() - 1.0,
                    region.maximum().x() + 2.0, level.getMaxBuildHeight(), region.maximum().z() + 2.0);
            level.getEntities().get(EntityTypeTest.forClass(RocketEntity.class), box, rocket -> {
                // Do not filter owner/snapshot/validity: a contradictory protected copy must refuse, not disappear.
                if (rocket.assemblyTransactionId().filter(record.logicalRocketId()::equals).isPresent()
                        || rocket.getUUID().equals(record.sourceEntityId()) || rocket.getUUID().equals(destination)) {
                    candidates.add(rocket);
                }
                return candidates.size() >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                        ? AbortableIterationConsumer.Continuation.ABORT : AbortableIterationConsumer.Continuation.CONTINUE;
            });
            if (candidates.size() >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES) { return false; }
        }
        return candidates.size() == (arrival == null ? 1 : 2) && candidates.contains(source)
                && (arrival == null || candidates.contains(arrival));
    }
}
