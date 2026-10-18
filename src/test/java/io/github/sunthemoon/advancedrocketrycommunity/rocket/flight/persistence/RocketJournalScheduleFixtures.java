package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.UUID;

/** Distinct records and genuinely pending absences for bounded detached scheduler tests. */
public final class RocketJournalScheduleFixtures {
    private RocketJournalScheduleFixtures() { }
    public static RocketTransferRecord record(int index) {
        return RocketTransferSavedDataTest.record(new UUID(910, index), new UUID(911, index), new UUID(912, index),
                new RocketPosition(10_000 + index * 16, 80, 10_000));
    }
    public static UUID pendingAbsence(RocketTransferSavedData journal, int index) {
        var record = record(index); journal.put(record); journal.remove(record.transferId());
        return record.logicalRocketId();
    }
}
