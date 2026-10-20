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
        return rule(server, journal, record, bays, action, true);
    }

    /**
     * W116 (W98-R10, W96 section 4): the same rule for an ordinary retirement that may hold L's reservation. L must be
     * actionable with {@code own} recognized as the caller's (null when it holds none), checked before any bay boundary;
     * no other caller's reservation is ignored, and the reservation is never released to pass.
     */
    static boolean flightAllowed(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
            RocketTransferBayDispatch bays, RocketTransferBayRecoveryGate.Action action, RocketTransferSavedData.Reservation own) {
        UUID logical = record.logicalRocketId();
        if (!(own == null ? journal.actionable(logical) : journal.actionable(logical, own)) || record.nativeTransfer().isPresent()) {
            return false;
        }
        return rule(server, journal, record, bays, action, false);
    }

    private static boolean rule(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
            RocketTransferBayDispatch bays, RocketTransferBayRecoveryGate.Action action, boolean mutationProof) {
        var boundary = bays.before(server, journal, record, action);
        if (boundary == null) { return false; }
        // Record-3 data is never authority for a legacy callback, even when its ARMED entry is absent.
        if (record.nativeTransfer().isPresent()) { return false; }
        boolean permitted = boundary.kind() == RocketTransferBayRecoveryGate.BoundaryKind.PERMIT;
        if (permitted ? RocketEscrowGate.escrowLogicalLocked(server, record.logicalRocketId())
                : RocketEscrowGate.logicalLocked(server, record.logicalRocketId())) {
            if (permitted) { bays.conflict(server, journal, record); }
            return false;
        }
        // The retirement overload has already required L actionable, so its own reservation does not refuse it here.
        if (mutationProof && journal.resourceMutationUnproved(record.logicalRocketId())) { return false; }
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
