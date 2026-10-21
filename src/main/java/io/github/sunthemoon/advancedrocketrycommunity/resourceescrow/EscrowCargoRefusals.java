package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCoordinator.Transfer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/** Typed cargo diagnostics only; no admission, mutation or change to the closed Fill result contract. */
final class EscrowCargoRefusals {
    private EscrowCargoRefusals() { }
    static Optional<Transfer> capacity(EscrowLedger root, Collection<EscrowOperation> pending,
                                       EscrowNativeBinding.Admission capture) {
        var op = capture.operation();
        if (capture.before().ledger() != null && !root.id().equals(capture.before().ledger())) {
            return Optional.of(Transfer.STALE);
        }
        var additions = new ArrayList<>(pending); additions.add(op);
        EscrowLedger prospective;
        try { prospective = root.append(additions); }
        catch (EscrowSettlementCapacity.ReservationFull full) { return Optional.of(Transfer.ESCROW_FULL); }
        catch (IllegalArgumentException stale) { return Optional.of(Transfer.STALE); }
        try {
            EscrowSettlementCapacity.requireAdmission(prospective, op, capture.before());
            EscrowSettlementCapacity.preflight(prospective, op.id());
        } catch (EscrowSettlementCapacity.MarkerLimit limit) { return Optional.of(Transfer.ROCKET_LIMIT); }
        catch (EscrowSettlementCapacity.ReservationFull full) { return Optional.of(Transfer.ESCROW_FULL); }
        catch (EscrowTags.TooLarge size) { return Optional.of(Transfer.CARGO_TOO_LARGE); }
        catch (IllegalArgumentException stale) { return Optional.of(Transfer.STALE); }
        return Optional.empty();
    }
    static RocketCargoStatus status(Transfer result) {
        return switch (result) {
            case QUEUED -> RocketCargoStatus.ESCROW_PENDING;
            case CARGO_TOO_LARGE -> RocketCargoStatus.CARGO_TOO_LARGE;
            case ROCKET_LIMIT, ESCROW_FULL, SERVER_FULL, ROCKET_FULL -> RocketCargoStatus.ESCROW_FULL;
            case STALE -> RocketCargoStatus.STALE_BINDING;
            case ENDPOINT_BUSY -> RocketCargoStatus.ESCROW_PENDING;
            case UNAVAILABLE, IDENTITY_CONFLICT -> RocketCargoStatus.LEDGER_UNAVAILABLE;
            case INVALID -> RocketCargoStatus.INVALID_DATA;
            case ROCKET_BUSY -> RocketCargoStatus.ROCKET_BUSY;
            case OTHER_TRANSFER_PENDING -> RocketCargoStatus.OTHER_TRANSFER_PENDING;
            case RECEIPT_BACKED -> RocketCargoStatus.RECEIPT_BACKED;
        };
    }
}
