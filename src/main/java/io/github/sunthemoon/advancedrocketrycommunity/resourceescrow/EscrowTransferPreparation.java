package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCoordinator.Fill;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCoordinator.Transfer;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Owner-thread admission and pure capacity checks, separate from the ledger writer lifecycle. */
final class EscrowTransferPreparation {
    private EscrowTransferPreparation() { }
    /** C-P capture entry for C-E: all eligibility passes finish before issuing a sequence or reserving anything. */
    static Transfer prepareCargo(EscrowCoordinator coordinator, EscrowCargoPreparedCapture prepared,
                          java.util.function.BooleanSupplier unchanged,
                          Consumer<Boolean> completion) {
        coordinator.owner();
        return prepareCargo(prepared, unchanged, admission -> prepareTransfer(coordinator, admission, completion));
    }
    static Transfer prepareCargo(EscrowCargoPreparedCapture prepared, java.util.function.BooleanSupplier unchanged,
            java.util.function.Function<EscrowNativeBinding.Admission, Transfer> admit) {
        var result = prepared.result();
        if (result.status() != io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.ELIGIBLE) {
            prepared.discard();
            return result.refusal();
        }
        // Changed native inputs are discarded and must repeat the full eligibility capture, not merely its digests.
        if (!prepared.valid()) { prepared.discard(); return Transfer.STALE; }
        var outcome = new Transfer[] {Transfer.UNAVAILABLE};
        prepared.admission(() -> {
            try {
                if (!unchanged.getAsBoolean() || !prepared.unchanged()) { outcome[0] = Transfer.STALE; }
                else {
                    // Repeat sequence, root, O1 and combined reservation against the final acknowledged root.
                    outcome[0] = admit.apply(result.capture());
                }
            } finally {
                // Apply must reconstruct from BEFORE, even when final admission throws.
                prepared.discard();
            }
            return new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation.Work(16L * 1_048_576, 16_384);
        });
        return outcome[0];
    }
    /** Shared owner-thread entry. Cargo has no production caller until C-E; no publication permit is issued here. */
    static Transfer prepareTransfer(EscrowCoordinator coordinator, EscrowNativeBinding.Admission capture, Consumer<Boolean> completion) {
        coordinator.owner(); var op = capture.operation();
        if (coordinator.admissionUnavailable()) { return Transfer.UNAVAILABLE; }
        if (EscrowTransferAdmission.invalid(op)) { return Transfer.INVALID; }
        if (coordinator.staleAdmission(op)) { return Transfer.STALE; }
        if (coordinator.heldAdmission(op)) {
            return Transfer.ENDPOINT_BUSY;
        }
        var pending = coordinator.pendingAdmissions();
        var refusal = EscrowTransferAdmission.refusal(coordinator.ledger(), pending, capture);
        if (refusal.isPresent()) {
            if (refusal.get() == Transfer.OTHER_TRANSFER_PENDING || refusal.get() == Transfer.ROCKET_BUSY) {
                coordinator.transferTurns().blocked(op.device().identity(), op.guard().rocket());
            }
            return refusal.get();
        }
        if (!coordinator.transferTurns().allows(op.device().identity(), op.guard().rocket())) {
            return op.kind().cargo() ? Transfer.OTHER_TRANSFER_PENDING : Transfer.ROCKET_BUSY;
        }
        var reservation = coordinator.admissionAuthority().tryReserve(op.id(), op.owner(), op.guard().rocket(), EscrowAdmissionAuthority.Consumer.PROPULSION);
        if (reservation != EscrowAdmissionAuthority.Result.RESERVED) { return Transfer.of(fill(reservation)); }
        // Nothing that can fail runs between the reservation and its queue entry: a reservation has no release call.
        coordinator.enqueue(op, completion);
        coordinator.transferTurns().admitted(op.device().identity(), op.guard().rocket());
        return Transfer.QUEUED;
    }
    /** Not a fresh FLUID_PROPULSION_FILL with a complete FUEL_VECTOR_V1 binding and a native Fluid. */
    static boolean invalid(EscrowOperation op) {
        return op.kind() != EscrowOperation.Kind.FLUID_PROPULSION_FILL || op.phase() != EscrowOperation.Phase.PREPARED
                || op.reason() != EscrowReason.NONE || !op.settlement().isEmpty()
                || op.nativeBinding().kind() != EscrowSignatureKind.FUEL_VECTOR_V1 || !op.nativeBinding().complete()
                || !EscrowTransferAdmission.nativeFluid(op);
    }
    /** The authority's refusal as a typed fill result; ALREADY_HELD is STALE because every index was checked first. */
    static Fill fill(EscrowAdmissionAuthority.Result reservation) {
        return switch (reservation) {
            case SERVER_FULL -> Fill.SERVER_FULL;
            case ROCKET_FULL -> Fill.ROCKET_FULL;
            case IDENTITY_CONFLICT -> Fill.IDENTITY_CONFLICT;
            case ALREADY_HELD -> Fill.STALE;
            case UNAVAILABLE -> Fill.UNAVAILABLE;
            case RESERVED -> Fill.QUEUED;
        };
    }
    /**
     * prepareFill's pure check against a given root and its queued admissions: the five-marker limit and settlement
     * fit of {@code requireAdmission}, then the candidate preflight, which keeps Root-R6's floor-retirement headroom
     * and charges every unsettled component, the candidate's included, at its ceiling (Root-R8). It adds no capacity
     * rule of its own and mutates nothing; the first-fill path runs it against a virtual empty root.
     */
    static java.util.Optional<Fill> fillRefusal(EscrowLedger root, java.util.Collection<EscrowOperation> admissions,
                                                EscrowNativeBinding.Admission capture) {
        var op = capture.operation();
        if (capture.before().ledger() != null && !capture.before().ledger().equals(root.id())) { return java.util.Optional.of(Fill.STALE); }
        var candidate = new ArrayList<>(admissions); candidate.add(op);
        EscrowLedger prospective;
        try { prospective = root.append(candidate); }
        catch (IllegalArgumentException invalid) { return java.util.Optional.of(Fill.STALE); }
        try { EscrowSettlementCapacity.requireAdmission(prospective, op, capture.before()); }
        catch (IllegalArgumentException limit) { return java.util.Optional.of(Fill.ROCKET_LIMIT); }
        try { EscrowSettlementCapacity.preflight(prospective, op.id()); }
        catch (IllegalArgumentException full) { return java.util.Optional.of(Fill.ESCROW_FULL); }
        return java.util.Optional.empty();
    }
}
