package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** A released receipt authorizes only marker removal, never resource or epoch replacement. */
public final class EscrowSettlementPruning {
    private EscrowSettlementPruning() { }
    public static RocketEscrowSection rocket(RocketEntity rocket, RocketEscrowSection section) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()) { return section; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null || section.markers().isEmpty() || !data.ledger().id().equals(section.ledger())) { return section; }
        UUID logical = rocket.assemblyTransactionId().orElseThrow();
        String signature = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.hash("arce-escrow-rocket-1:" + logical + ":"
                + rocket.snapshot().orElseThrow().contentHash() + ":" + rocket.flightData().orElseThrow().fuel().resourceSignature());
        var released = data.ledger().tombstones().stream().filter(tag -> tag.getInt("schema_version") == 2 && tag.getBoolean("released"))
                .filter(tag -> EscrowTags.list(tag, "survivors", 2).stream().anyMatch(raw -> {
                    var survivor = (CompoundTag) raw; var endpoint = EscrowCodec.endpoint(survivor.getCompound("endpoint"));
                    return endpoint.kind() == EscrowEndpoint.Kind.ENTITY && rocket.getUUID().equals(endpoint.identity())
                            && logical.equals(endpoint.logicalRocket()) && section.epoch() == survivor.getLong("epoch")
                            && signature.equals(survivor.getString("signature"))
                            && rocket.flightData().orElseThrow().fuel().totalAmount() == survivor.getLong("quantity");
                })).toList();
        var ids = EscrowSettlementTombstones.retiredIds(released);
        EscrowTags.require(data.ledger().records().stream().noneMatch(op -> ids.contains(op.id())), "Marker still has live authority");
        return new RocketEscrowSection(section.ledger(), section.epoch(), section.markers().stream().filter(marker -> !ids.contains(marker.operation())).toList());
    }
    static void tick(MinecraftServer server, EscrowLedger root, int cursor) {
        var tags = root.tombstones().stream().filter(tag -> tag.getInt("schema_version") == 2 && tag.getBoolean("released")).toList();
        if (tags.isEmpty()) { return; }
        var tag = tags.get(Math.floorMod(cursor, tags.size()));
        for (var raw : EscrowTags.list(tag, "survivors", 2)) {
            var survivor = (CompoundTag) raw; var endpoint = EscrowCodec.endpoint(survivor.getCompound("endpoint"));
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(endpoint.level())));
            if (level == null) { continue; }
            if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY) {
                if (level.getEntity(endpoint.identity()) instanceof RocketEntity rocket) { rocket.pruneSettledEscrow(); }
            } else if (server instanceof net.minecraft.gametest.framework.GameTestServer || Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) {
                EscrowTestEndpoints.requireEnabled(server);
                var chunk = level.getChunkSource().getChunkNow(endpoint.x() >> 4, endpoint.z() >> 4);
                if (chunk == null) { continue; }
                var source = chunk.getBlockEntity(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
                if (source == null || !endpoint.identity().equals(source.getPersistentData().getCompound("arce_escrow_fixture").getUUID("identity"))) { continue; }
                var state = EscrowTestEndpoints.state(source.getPersistentData(), root.id(), false);
                if (state.epoch() != survivor.getLong("epoch") || !state.signature().equals(survivor.getString("signature"))
                        || EscrowTestEndpoints.amount(source.getPersistentData()) != survivor.getLong("quantity")) { continue; }
                var group = tags.stream().filter(value -> value.getString("decision_digest").equals(tag.getString("decision_digest"))).toList();
                EscrowTestEndpoints.putState(source.getPersistentData(), EscrowTestEndpoints.prune(state, EscrowSettlementTombstones.retiredIds(group)), false);
                source.setChanged();
            }
        }
    }
}
