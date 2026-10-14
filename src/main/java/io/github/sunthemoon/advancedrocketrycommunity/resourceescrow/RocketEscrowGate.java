package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Read-only guards: neither an ordinary rocket nor a policy query creates the lazy ledger. */
public final class RocketEscrowGate {
    private RocketEscrowGate() { }

    public static boolean idle(RocketEntity rocket) {
        return escrowIdle(rocket);
    }

    /** Bay-scoped permits may bypass only the bay hold, never these existing escrow protections. */
    public static boolean escrowIdle(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return false;
        }
        var section = rocket.escrowSection();
        UUID logical = rocket.assemblyTransactionId().orElse(null);
        if (!section.markers().isEmpty()) { return false; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null) { return section.ledger() == null; }
        var ledger = data.ledger();
        if (section.ledger() != null) { return false; }
        if (logical != null && data.bind(level.getServer()).logicalLocked(logical)) { return false; }
        if (data.ledger().records().stream().anyMatch(record -> rocket.getUUID().equals(record.source().identity())
                || rocket.getUUID().equals(record.destination().identity()))) { return false; }
        return ledger.tombstones().stream().noneMatch(tombstone -> {
            for (var raw : EscrowTags.list(tombstone, "missing", 2)) {
                var endpoint = EscrowCodec.endpoint((net.minecraft.nbt.CompoundTag) raw);
                if (rocket.getUUID().equals(endpoint.identity()) || logical != null && logical.equals(endpoint.logicalRocket())) { return true; }
            }
            for (var raw : EscrowTags.list(tombstone, "guards", 64)) {
                if (logical != null && logical.equals(EscrowCodec.guard((net.minecraft.nbt.CompoundTag) raw).rocket())) { return true; }
            }
            return false;
        });
    }

    public static boolean logicalLocked(net.minecraft.server.MinecraftServer server, UUID logical) {
        return escrowLogicalLocked(server, logical);
    }

    public static boolean escrowLogicalLocked(net.minecraft.server.MinecraftServer server, UUID logical) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return false; }
        return data.bind(server).logicalLocked(logical) || data.ledger().tombstones().stream().anyMatch(tombstone -> {
            for (var raw : EscrowTags.list(tombstone, "guards", 64)) {
                if (logical.equals(EscrowCodec.guard((net.minecraft.nbt.CompoundTag) raw).rocket())) { return true; }
            }
            for (var raw : EscrowTags.list(tombstone, "missing", 2)) {
                if (logical.equals(EscrowCodec.endpoint((net.minecraft.nbt.CompoundTag) raw).logicalRocket())) { return true; }
            }
            return false;
        });
    }
}
