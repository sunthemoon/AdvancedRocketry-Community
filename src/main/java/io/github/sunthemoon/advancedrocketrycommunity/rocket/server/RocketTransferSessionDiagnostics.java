package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded journal/session summary, separate from gameplay dispatch. */
final class RocketTransferSessionDiagnostics {
    private RocketTransferSessionDiagnostics() { }
    static String summary(RocketTransferSavedData journal, Set<UUID> live, Set<UUID> settled) {
        var classified = new java.util.HashSet<>(live); classified.addAll(settled);
        return summary(journal, live, settled, classified);
    }
    static String summary(RocketTransferSavedData journal, Set<UUID> live, Set<UUID> settled, Set<UUID> classified) {
        if (!journal.operational()) { return "transfer_journal=blocked"; }
        List<RocketTransferRecord> records = journal.entries();
        long liveCount = records.stream().filter(record -> live.contains(record.transferId())).count();
        long settledCount = records.stream().filter(record -> settled.contains(record.transferId())).count();
        List<RocketTransferRecord> unclassified = records.stream()
                .filter(record -> !classified.contains(record.transferId())).toList();
        StringBuilder line = new StringBuilder(String.format(java.util.Locale.ROOT,
                "transfer_journal=operational records=%d live=%d settled=%d unclassified=%d",
                records.size(), liveCount, settledCount, unclassified.size()));
        for (RocketTransferRecord record : unclassified.subList(0, Math.min(4, unclassified.size()))) {
            line.append(String.format(java.util.Locale.ROOT, " [transfer=%s phase=%s source=%s@%s destination=%s@%s]",
                    record.transferId(), record.phase(), record.sourceSnapshot().sourceDimension(),
                    position(record.sourceSnapshot().sourceOrigin()), record.destinationSnapshot().sourceDimension(),
                    position(record.destinationSnapshot().sourceOrigin())));
        }
        if (!unclassified.isEmpty()) { line.append(" load an end, then /arce rocket recover <transfer>"); }
        return line.toString();
    }
    private static String position(RocketPosition origin) { return origin.x() + "," + origin.y() + "," + origin.z(); }
}
