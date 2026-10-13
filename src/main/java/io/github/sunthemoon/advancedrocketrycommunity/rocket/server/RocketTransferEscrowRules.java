package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** The escrow-only flight predicate deliberately does not reject normal active transfer journals. */
final class RocketTransferEscrowRules {
    private RocketTransferEscrowRules() { }
    static boolean flightAllowed(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
            RocketTransferBayDispatch bays, RocketTransferBayRecoveryGate.Action action) {
        var boundary = bays.before(server, journal, record, action);
        if (boundary == null) { return false; }
        boolean permitted = boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT;
        if (permitted ? RocketEscrowGate.escrowLogicalLocked(server, record.logicalRocketId())
                : RocketEscrowGate.logicalLocked(server, record.logicalRocketId())) {
            if (permitted) { bays.conflict(server, journal, record); }
            return false;
        }
        if (journal.resourceMutationUnproved(record.logicalRocketId())) { return false; }
        for (var dimension : List.of(record.sourceSnapshot().sourceDimension(), record.destinationSnapshot().sourceDimension())) {
            ServerLevel level = RocketTransferEntities.level(server, dimension);
            if (level == null) { continue; }
            for (UUID identity : List.of(record.sourceEntityId(), record.destinationEntityId().orElse(record.sourceEntityId()))) {
                if (level.getEntity(identity) instanceof RocketEntity rocket
                        && !(permitted ? RocketEscrowGate.escrowIdle(rocket) : RocketEscrowGate.idle(rocket))) {
                    if (permitted) { bays.conflict(server, journal, record); }
                    return false;
                }
            }
        }
        return true;
    }
}
