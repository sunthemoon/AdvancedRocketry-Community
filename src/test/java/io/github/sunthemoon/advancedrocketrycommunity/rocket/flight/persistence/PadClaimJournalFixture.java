package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.nio.file.Path;

/** Test-only access to the detached writer; production roots cannot synchronously await IO. */
public final class PadClaimJournalFixture {
    private PadClaimJournalFixture() { }
    public static RocketTransferRecord record() { return RocketTransferSavedDataTest.record(); }
    public static void write(RocketTransferSavedData journal, Path file, CheckedSavedDataPolicy policy) {
        journal.saveChecked(file, policy);
    }
    public static void request(RocketTransferSavedData journal, Path file, CheckedSavedDataPolicy policy) {
        if (!journal.requestWrite(file, policy)) { throw new AssertionError("Fixture write refused"); }
    }
    public static void drain(RocketTransferSavedData journal) { journal.drainForTests(); }
}
