package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferAudit.auditPhase;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/** The transfer service's side of a pending landed disassembly; the sets are the service's, passed by reference. */
final class RocketLandedDisassemblyPort implements RocketLandedDisassemblies.Port {
    private final MinecraftServer server;
    private final RocketTransferSavedData journal;
    private final RocketTransferBayDispatch bays;
    private final Set<UUID> liveTransfers;
    private final Set<UUID> settledTransfers;

    RocketLandedDisassemblyPort(MinecraftServer server, RocketTransferSavedData journal, RocketTransferBayDispatch bays,
                                Set<UUID> liveTransfers, Set<UUID> settledTransfers) {
        this.server = server;
        this.journal = journal;
        this.bays = bays;
        this.liveTransfers = liveTransfers;
        this.settledTransfers = settledTransfers;
    }

    @Override
    public long now() {
        return server.overworld().getGameTime();
    }

    @Override
    public boolean bayAllowsRemoval(RocketTransferRecord record) {
        return bays.allowed(server, journal, record, RocketTransferBayRecoveryGate.Action.REMOVE_JOURNAL);
    }

    @Override
    public boolean queue(UUID logical, BooleanSupplier action, Runnable discarded) {
        return journal.afterAcknowledged(server, logical, action, discarded);
    }

    @Override
    public void released(RocketTransferRecord record, UUID entity) {
        liveTransfers.remove(record.transferId());
        settledTransfers.remove(record.transferId());
        auditPhase(record, "landed_reservation_released", entity);
    }

    @Override
    public void restored(RocketTransferRecord record, UUID entity, boolean settle) {
        if (settle) {
            // As restart recovery settles a matching stationary arrival; otherwise ordinary recovery classifies T.
            settledTransfers.add(record.transferId());
            liveTransfers.remove(record.transferId());
        }
        auditPhase(record, "landed_reservation_restored", entity);
    }

    @Override
    public void probe(String boundary, RocketTransferRecord record) {
        RocketLandedDisassemblyProbe.pause(server, boundary, record);
    }
}
