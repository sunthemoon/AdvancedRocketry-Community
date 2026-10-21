package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Shared first-transfer creation ordering; the empty baseline must precede the first L1. */
final class EscrowLedgerCreation {
    private EscrowLedgerCreation() { }
    static EscrowCoordinator.Transfer create(MinecraftServer server, EscrowRuntime runtime, UUID ledger,
            EscrowNativeBinding.Admission capture, Consumer<Boolean> completion) {
        var op = capture.operation();
        if (EscrowTransferAdmission.invalid(op)) { return EscrowCoordinator.Transfer.INVALID; }
        if (op.sequence() != 1) { return EscrowCoordinator.Transfer.STALE; }
        var empty = EscrowLedger.empty(ledger);
        var refusal = op.kind().cargo() ? EscrowTransferAdmission.refusal(empty, List.of(), capture)
                : EscrowTransferPreparation.fillRefusal(empty, List.of(), capture).map(EscrowCoordinator.Transfer::of);
        if (refusal.isPresent()) { return refusal.get(); }
        var reservation = runtime.propulsion(server).authority().tryReserve(op.id(), op.owner(), op.guard().rocket(),
                EscrowAdmissionAuthority.Consumer.PROPULSION);
        if (reservation != EscrowAdmissionAuthority.Result.RESERVED) { return EscrowCoordinator.Transfer.of(EscrowCoordinator.fill(reservation)); }
        var coordinator = ResourceEscrowSavedData.createForFill(server, ledger);
        coordinator.enqueueCreated(op, completion);
        return coordinator.operational() ? EscrowCoordinator.Transfer.QUEUED : EscrowCoordinator.Transfer.UNAVAILABLE;
    }
}
