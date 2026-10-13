package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** The escrow-only flight predicate deliberately does not reject normal active transfer journals. */
final class RocketTransferEscrowRules {
    private RocketTransferEscrowRules() { }
    static boolean flightAllowed(MinecraftServer server, RocketTransferRecord record) {
        if (RocketEscrowGate.logicalLocked(server, record.logicalRocketId())) { return false; }
        for (var dimension : List.of(record.sourceSnapshot().sourceDimension(), record.destinationSnapshot().sourceDimension())) {
            ServerLevel level = RocketTransferEntities.level(server, dimension);
            if (level == null) { continue; }
            for (UUID identity : List.of(record.sourceEntityId(), record.destinationEntityId().orElse(record.sourceEntityId()))) {
                if (level.getEntity(identity) instanceof RocketEntity rocket && !RocketEscrowGate.idle(rocket)) { return false; }
            }
        }
        return true;
    }
}
