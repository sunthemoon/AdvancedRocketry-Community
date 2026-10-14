package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** Loaded-only O1 observations, never an absence checkpoint or permission to release. */
final class EscrowSettlementObserver {
    static final int ENTITIES_PER_SLOT = 1_024;
    private EscrowSettlementObserver() { }
    static List<EscrowSettlementIntent.Observation> floor(MinecraftServer server, net.minecraft.nbt.CompoundTag floor, UUID ledger) {
        var journal = RocketTransferSavedData.get(server);
        EscrowTags.require(journal.retryChecked(server), "Checked journal acknowledgement is unavailable");
        var guard = EscrowCodec.floorGuard(floor);
        EscrowTags.require(RocketEscrowAudit.journal(guard, journal.acknowledgedGuard(guard.rocket()).orElse(null)) == EscrowReason.NONE,
                "Floor journal guard changed");
        var record = journal.find(guard.transfer()).orElseThrow(() -> new IllegalArgumentException("Floor has no matching journal record"));
        EscrowTags.require(record.phase() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase.COMMITTED
                && record.destinationEntityId().filter(floor.getUUID("entity_id")::equals).isPresent(), "Floor needs its committed protected destination");
        var references = new LinkedHashMap<EscrowEndpoint.Key, EscrowEndpoint>();
        for (var snapshot : List.of(record.sourceSnapshot(), record.destinationSnapshot())) {
            var position = snapshot.sourceOrigin();
            var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, snapshot.sourceDimension().toString(), floor.getUUID("entity_id"),
                    guard.rocket(), io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("rocket").toString(),
                    position.x() >> 4, 0, position.z() >> 4, EscrowEndpoint.Unit.PRIMARY,
                    floor.getLong("resource_epoch"), floor.getString("signature"), floor.getString("signature"));
            references.putIfAbsent(endpoint.key(), endpoint);
        }
        var observations = observe(server, ledger, List.copyOf(references.values()));
        EscrowTags.require(observations.stream().allMatch(EscrowSettlementIntent.Observation::missing), "Protected floor identity is not missing at both ends");
        return observations;
    }
    static List<EscrowSettlementIntent.Observation> component(MinecraftServer server, EscrowLedger root, UUID selected) {
        var records = EscrowSettlementIntent.component(root.records(), selected);
        var journal = RocketTransferSavedData.get(server);
        EscrowTags.require(journal.retryChecked(server), "Checked journal acknowledgement is unavailable");
        var references = new LinkedHashMap<EscrowEndpoint.Key, EscrowEndpoint>();
        for (var op : records) {
            EscrowTags.require(RocketEscrowAudit.journal(op.guard(), journal.acknowledgedGuard(op.guard().rocket()).orElse(null))
                    == EscrowReason.NONE, "Journal guard changed");
            references.putIfAbsent(op.source().key(), op.source()); references.putIfAbsent(op.destination().key(), op.destination());
        }
        return observe(server, root.id(), List.copyOf(references.values()));
    }
    static List<EscrowSettlementIntent.Observation> observe(MinecraftServer server, UUID ledger, List<EscrowEndpoint> references) {
        EscrowTags.require(server.isSameThread() && references.size() <= EscrowLimits.OPERATIONS * 2, "Invalid observation scope");
        var result = new ArrayList<EscrowSettlementIntent.Observation>();
        for (var endpoint : references) {
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(endpoint.level())));
            EscrowTags.require(level != null, "Endpoint Level is unavailable");
            var chunk = endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? new ChunkPos(endpoint.x(), endpoint.z())
                    : new ChunkPos(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
            EscrowTags.require(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null
                    && level.areEntitiesLoaded(chunk.toLong())
                    && level.isPositionEntityTicking(new BlockPos(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ())),
                    "WAITING_UNLOADED: endpoint slot must be FULL, entity-loaded and ticking");
            if (endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY) {
                // No production source adapter is registered. Only already-gated source fixtures are supported.
                EscrowTestEndpoints.requireEnabled(server);
                var source = level.getChunkSource().getChunkNow(chunk.x, chunk.z)
                        .getBlockEntity(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
                if (source == null) { result.add(missing(endpoint)); continue; }
                EscrowTags.require(endpoint.identity().equals(source.getPersistentData().getCompound("arce_escrow_fixture").getUUID("identity"))
                        && endpoint.type().equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(source.getType()).toString()),
                        "Source identity/type changed");
                result.add(new EscrowSettlementIntent.Observation(endpoint,
                        EscrowTestEndpoints.state(source.getPersistentData(), ledger, false), true, EscrowTestEndpoints.amount(source.getPersistentData())));
            } else {
                int levels = 0;
                for (var known : server.getAllLevels()) {
                    EscrowTags.require(++levels <= 64, "Settlement Level observation bound exceeded");
                    EscrowTags.require(known == level || known.getEntity(endpoint.identity()) == null, "Endpoint identity relocated to another Level");
                }
                var exact = level.getEntity(endpoint.identity());
                requireNoOtherIdentity(level, endpoint, chunk);
                if (exact == null) { result.add(missing(endpoint)); continue; }
                EscrowTags.require(exact instanceof RocketEntity, "Unsupported settlement entity adapter");
                var rocket = (RocketEntity) exact;
                EscrowTags.require(RocketEscrowAudit.placement(endpoint, rocket) == EscrowReason.NONE
                        && rocket.flightData().orElseThrow().state().acceptsFuel(), "Rocket placement, occupancy or stationary state changed");
                var section = rocket.escrowSection();
                EscrowTags.require(section.ledger() == null || section.ledger().equals(ledger), "Foreign rocket ledger");
                String signature = RocketNativeResources.hash("arce-escrow-rocket-1:" + endpoint.logicalRocket() + ":"
                        + rocket.snapshot().orElseThrow().contentHash() + ":" + rocket.flightData().orElseThrow().fuel().resourceSignature());
                result.add(new EscrowSettlementIntent.Observation(endpoint,
                        new EscrowPrefix.State(ledger, section.epoch(), signature, section.markers()), section.ledger() != null,
                        rocket.flightData().orElseThrow().fuel().totalAmount()));
            }
        }
        return List.copyOf(result);
    }
    private static EscrowSettlementIntent.Observation missing(EscrowEndpoint endpoint) {
        return new EscrowSettlementIntent.Observation(endpoint, null, false, 0);
    }
    private static void requireNoOtherIdentity(ServerLevel level, EscrowEndpoint endpoint, ChunkPos chunk) {
        int[] examined = {0}; boolean[] conflict = {false};
        level.getEntities().get(EntityTypeTest.forClass(Entity.class),
                new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMinBlockX() + 16D, level.getMaxBuildHeight(), chunk.getMinBlockZ() + 16D), entity -> {
                    if (++examined[0] > ENTITIES_PER_SLOT) { conflict[0] = true; return AbortableIterationConsumer.Continuation.ABORT; }
                    if (entity instanceof RocketEntity rocket && endpoint.logicalRocket().equals(rocket.assemblyTransactionId().orElse(null))
                            && !endpoint.identity().equals(rocket.getUUID())) { conflict[0] = true; return AbortableIterationConsumer.Continuation.ABORT; }
                    return AbortableIterationConsumer.Continuation.CONTINUE;
                });
        EscrowTags.require(!conflict[0], "Duplicate logical identity or observation bound exceeded");
    }
}
