package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.Collection;
import java.util.Optional;

/** Direction-independent C-K checks, before any sequence, reservation, queue or writer mutation. */
final class EscrowTransferAdmission {
    private EscrowTransferAdmission() { }
    static boolean invalid(EscrowOperation op) {
        return !op.kind().transfer() || op.phase() != EscrowOperation.Phase.PREPARED
                || op.reason() != EscrowReason.NONE || !op.settlement().isEmpty() || !op.nativeBinding().complete()
                || op.nativeBinding().kind() != (op.kind().cargo()
                ? EscrowSignatureKind.ROCKET_COMPOSITE_V1 : EscrowSignatureKind.FUEL_VECTOR_V1);
    }
    static Optional<EscrowCoordinator.Fill> exclusive(EscrowLedger root, Collection<EscrowOperation> pending,
                                                     EscrowNativeBinding.Admission capture) {
        var op = capture.operation(); var logical = op.rocket().logicalRocket();
        var related = java.util.stream.Stream.concat(root.records().stream(), pending.stream())
                .filter(member -> logical.equals(member.rocket().logicalRocket())).toList();
        if (op.kind().cargo()) {
            if (capture.before().epoch() != op.rocket().beforeEpoch()) { return Optional.of(EscrowCoordinator.Fill.STALE); }
            if (EscrowSettlementTombstones.history(root, logical)
                    || root.rocketAuthorities().stream().anyMatch(head -> logical.equals(head.logical()))) {
                return Optional.of(EscrowCoordinator.Fill.RECEIPT_BACKED);
            }
            if (!related.isEmpty()) { return Optional.of(EscrowCoordinator.Fill.OTHER_TRANSFER_PENDING); }
        } else if (related.stream().anyMatch(member -> member.kind().cargo())) {
            return Optional.of(EscrowCoordinator.Fill.ROCKET_BUSY);
        }
        return Optional.empty();
    }
}
