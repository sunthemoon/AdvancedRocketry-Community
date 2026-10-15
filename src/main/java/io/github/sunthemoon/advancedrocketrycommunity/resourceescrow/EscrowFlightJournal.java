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
        // Schema 1/2 cannot name a launch serial. Until the governed codec supplies a serial
        // reader, any other record version holds rather than being mistaken for no-T.
        if (journal.entries().stream().anyMatch(record -> record.schemaVersion() != 1 && record.schemaVersion() != 2)) {
            return () -> false;
        }
        return journal.acknowledgedLease();
    }
}
