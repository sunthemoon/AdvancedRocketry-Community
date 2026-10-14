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
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()) {
            return false;
        }
        var section = rocket.escrowSection();
        UUID logical = rocket.assemblyTransactionId().orElseThrow();
        if (io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData
                .get(level.getServer()).resourceMutationUnproved(logical)) { return false; }
        if (!section.markers().isEmpty()) { return false; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null) { return section.ledger() == null; }
        var ledger = data.ledger();
        if (section.ledger() != null && !section.ledger().equals(ledger.id())) { return false; }
        if (data.bind(level.getServer()).logicalLocked(logical)) { return false; }
        String signature = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.hash("arce-escrow-rocket-1:" + logical + ":"
                + rocket.snapshot().orElseThrow().contentHash() + ":" + rocket.flightData().orElseThrow().fuel().resourceSignature());
        return !EscrowSettlementTombstones.stateLocked(ledger, logical, rocket.getUUID(), section, signature,
                rocket.flightData().orElseThrow().fuel().totalAmount());
    }

    public static boolean logicalLocked(net.minecraft.server.MinecraftServer server, UUID logical) {
        return escrowLogicalLocked(server, logical);
    }

    public static boolean escrowLogicalLocked(net.minecraft.server.MinecraftServer server, UUID logical) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return false; }
        return data.bind(server).logicalLocked(logical) || EscrowSettlementTombstones.logicalLocked(data.ledger(), logical);
    }
}
