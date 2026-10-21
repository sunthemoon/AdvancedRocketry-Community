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
                ? EscrowSignatureKind.ROCKET_COMPOSITE_V1 : EscrowSignatureKind.FUEL_VECTOR_V1)
                || !nativeFluid(op);
    }
    /** R39 for fluids: admission repeats the full native check that a durable record no longer carries. */
    static boolean nativeFluid(EscrowOperation op) {
        if (!op.kind().fluid()) { return true; }
        try { op.nativeFluid(); return true; }
        catch (IllegalArgumentException unavailable) { return false; }
    }
    static Optional<EscrowCoordinator.Transfer> refusal(EscrowLedger root, Collection<EscrowOperation> pending,
                                                       EscrowNativeBinding.Admission capture) {
        var exclusive = exclusive(root, pending, capture);
        if (exclusive.isPresent()) { return exclusive; }
        // Settlement-backed retired markers already imply RECEIPT_BACKED. A remaining marker is not
        // retirement authority merely because its record is absent; wait for authoritative pruning.
        if (capture.operation().kind().cargo() && !capture.before().markers().isEmpty()) {
            return Optional.of(EscrowCoordinator.Transfer.OTHER_TRANSFER_PENDING);
        }
        if (capture.operation().kind().cargo()) { return EscrowCargoRefusals.capacity(root, pending, capture); }
        var capacity = EscrowCoordinator.fillRefusal(root, pending, capture);
        if (capacity.isPresent()) { return capacity.map(EscrowCoordinator.Transfer::of); }
        return Optional.empty();
    }
    private static Optional<EscrowCoordinator.Transfer> exclusive(EscrowLedger root, Collection<EscrowOperation> pending,
                                                     EscrowNativeBinding.Admission capture) {
        var op = capture.operation(); var logical = op.rocket().logicalRocket();
        var related = java.util.stream.Stream.concat(root.records().stream(), pending.stream())
                .filter(member -> logical.equals(member.rocket().logicalRocket())).toList();
        if (op.kind().cargo()) {
            if (capture.before().epoch() != op.rocket().beforeEpoch()) { return Optional.of(EscrowCoordinator.Transfer.STALE); }
            if (EscrowSettlementTombstones.history(root, logical)
                    || root.rocketAuthorities().stream().anyMatch(head -> logical.equals(head.logical()))) {
                return Optional.of(EscrowCoordinator.Transfer.RECEIPT_BACKED);
            }
            if (!related.isEmpty()) { return Optional.of(EscrowCoordinator.Transfer.OTHER_TRANSFER_PENDING); }
        } else if (related.stream().anyMatch(member -> member.kind().cargo())) {
            return Optional.of(EscrowCoordinator.Transfer.ROCKET_BUSY);
        }
        return Optional.empty();
    }
}
