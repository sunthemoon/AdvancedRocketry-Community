package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.nio.file.Path;

/** Read-only, bounded baseline check shared by pre-start validation and late binding. */
public final class EscrowLedgerBaseline {
    private EscrowLedgerBaseline() { }

    public static String require(Path file, EscrowLedger loaded) {
        var expected = EscrowCodec.encode(loaded);
        var first = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW);
        var second = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW);
        EscrowTags.require(first.equals(second) && (first.isPresent() ? first.get().equals(expected)
                : loaded.writerGeneration() == 0 && loaded.records().isEmpty() && loaded.floors().isEmpty()
                && loaded.tombstones().isEmpty() && loaded.armedFlights().isEmpty() && loaded.rocketAuthorities().isEmpty()
                && loaded.issuedLaunchSerial() == 0), "Escrow checked baseline differs from loaded authority");
        return first.isPresent() ? EscrowCodec.digest(expected) : null;
    }
}
