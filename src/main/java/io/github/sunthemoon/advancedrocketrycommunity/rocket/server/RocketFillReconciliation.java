package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFillSelfGuard;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGuard;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowAudit;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Ordinary callers keep the strict boundary; only exact guarded loader reconciliation gets the exception. */
final class RocketFillReconciliation {
    private RocketFillReconciliation() { }

    static boolean boundary(RocketEntity rocket, EscrowGuard guard, RocketTransferBayDispatch bays,
                            Set<UUID> live, Set<UUID> settled, Predicate<UUID> disassembling,
                            java.util.function.Function<UUID, String> fenceReason) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()
                || rocket.flightData().orElseThrow().activeTransferId().isPresent()
                || disassembling.test(rocket.assemblyTransactionId().orElse(null))) { return false; }
        var journal = RocketTransferSavedData.get(level.getServer());
        if (!journal.actionAuthorityAvailable()) { return false; }
        var record = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
        if (record == null) { return true; }
        var bay = bays.before(level.getServer(), journal, record, RocketTransferBayRecoveryGate.Action.TICK_SOURCE);
        if (bay == null || bay.kind() != RocketTransferBayRecoveryGate.BoundaryKind.UNCLAIMED) { return false; }
        boolean stationary = RocketTransferEntities.isReplaceableLandedAuthority(rocket, record);
        return RocketTransferService.reconciliationRecordAllowed(record, settled.contains(record.transferId()),
                live.contains(record.transferId()), stationary, record.nativeTransfer().isPresent() && RocketEscrowGate.terminalSuccessor(rocket))
                || guardedRecordAllowed(record, live.contains(record.transferId()), stationary, fenceReason.apply(record.transferId()))
                && guard != null && RocketEscrowAudit.journal(guard, journal.logicalGuard(record.logicalRocketId()).orElse(null)) == EscrowReason.NONE
                && EscrowFillSelfGuard.allows(rocket, guard);
    }

    static boolean guardedRecordAllowed(RocketTransferRecord record, boolean live, boolean stationary, String reason) {
        return record.nativeTransfer().isEmpty() && record.phase() == RocketTransferPhase.COMMITTED && !live && stationary
                && (reason == null || reason.equals("ESCROW_PENDING_OR_TOMBSTONE")
                || reason.equals("ENDPOINT_JOIN_PROOF_REQUIRED") || reason.equals("ENDPOINT_PROOF_REQUIRED"));
    }

    /** No tickets, proof-key writes, fence removal or recovery actions. This only preserves an existing settlement. */
    static boolean selfGuard(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        if (server == null) { return false; }
        var level = RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension());
        var id = record.destinationEntityId().orElse(null);
        var checked = journal.logicalGuard(record.logicalRocketId()).orElse(null);
        if (level == null || id == null || checked == null || checked.transfer() == null
                || !(level.getEntity(id) instanceof RocketEntity rocket)) { return false; }
        var guard = new EscrowGuard(checked.logical(), checked.transfer(), Math.max(1, checked.sequence()), checked.recordDigest());
        return EscrowFillSelfGuard.allows(rocket, guard) && RocketRuntime.escrowReconciliationBoundary(rocket, guard);
    }
}
