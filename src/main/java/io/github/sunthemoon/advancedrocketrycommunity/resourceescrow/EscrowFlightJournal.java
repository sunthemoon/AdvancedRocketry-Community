package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Owner-thread acquisition of checked journal absence, never an inference from decoded RAM. */
final class EscrowFlightJournal {
    private EscrowFlightJournal() { }

    static BooleanSupplier noTransfer(RocketTransferSavedData journal, UUID logical, UUID transfer, long serial) {
        var guard = journal.acknowledgedGuard(logical).orElse(null);
        if (serial <= 0 || guard == null || guard.transfer() != null || journal.find(transfer).isPresent()) {
            return () -> false;
        }
        // A different logical rocket may still name this root-wide serial; it is not no-T.
        if (journal.entries().stream().anyMatch(record -> record.nativeTransfer()
                .map(value -> value.armed().serial() == serial).orElse(record.schemaVersion() > 2))) {
            return () -> false;
        }
        return journal.acknowledgedLease();
    }
}
