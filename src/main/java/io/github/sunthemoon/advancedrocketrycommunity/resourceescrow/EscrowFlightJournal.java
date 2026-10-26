package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Owner-thread acquisition of checked journal absence, never an inference from decoded RAM. */
final class EscrowFlightJournal {
    private EscrowFlightJournal() { }

    /**
     * F1 has no assigned serial until the coalesced writer captures ARMED. Another launch may consume nextSerial
     * while its source proof waits, so retain only L/T absence until capture, then also retain the exact serial lease.
     * Both leases use immutable published views and are safe for the writer's replacement guard.
     */
    static final class PreparationLease implements BooleanSupplier {
        private final RocketTransferSavedData journal;
        private final UUID logical, transfer;
        private final BooleanSupplier absence;
        private volatile BooleanSupplier assigned = () -> true;

        PreparationLease(RocketTransferSavedData journal, UUID logical, UUID transfer, long nextSerial) {
            this.journal = journal; this.logical = logical; this.transfer = transfer;
            absence = noTransfer(journal, logical, transfer, nextSerial).getAsBoolean()
                    ? journal.logicalLease(logical, transfer, 0) : () -> false;
        }

        boolean capture(long serial) {
            assigned = noTransfer(journal, logical, transfer, serial);
            return getAsBoolean();
        }

        @Override public boolean getAsBoolean() { return absence.getAsBoolean() && assigned.getAsBoolean(); }
    }

    /** R17 item 3: L's checked lease; another rocket's journal change does not revoke it. A failed write still refuses it. */
    static BooleanSupplier noTransfer(RocketTransferSavedData journal, UUID logical, UUID transfer, long serial) {
        var guard = journal.actionAuthorityAvailable(logical) ? journal.logicalGuard(logical).orElse(null) : null;
        if (serial <= 0 || guard == null || guard.transfer() != null || journal.find(transfer).isPresent()) {
            return () -> false;
        }
        // The lease also refuses any record that names T or this root-wide serial, including another rocket's, in RAM
        // or in the last acknowledged generation (an unacknowledged removal still names them).
        return journal.logicalLease(logical, transfer, serial);
    }

    /** R17 item 3: one checked lease per (rocket, T) that an escrow decision's members name, for O3/O4 and C3 to C7. */
    static BooleanSupplier members(RocketTransferSavedData journal, Iterable<EscrowOperation> members) {
        record Named(UUID rocket, UUID transfer) { }
        var named = new HashSet<Named>(); var leases = new ArrayList<BooleanSupplier>();
        for (var op : members) {
            if (named.add(new Named(op.guard().rocket(), op.guard().transfer()))) {
                leases.add(journal.logicalLease(op.guard().rocket(), op.guard().transfer(), 0));
            }
        }
        if (leases.isEmpty()) { return () -> false; }
        return () -> leases.stream().allMatch(BooleanSupplier::getAsBoolean);
    }
}
