package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * R17 item 3: the checked journal lease of one logical rocket L. It holds while the journal has the exact acknowledged
 * record instance of L (or the exact checked absence of any record of L), L's acknowledged guard is unchanged, no other
 * record names the operation's T or serial, and no change of L is unacknowledged. Another rocket's change never revokes
 * it. The owner thread publishes an immutable view; a lease reads only that view, so the ledger writer may check it.
 */
final class RocketJournalLeases {
    /** Changes of more distinct rockets than this fold into one conservative floor. */
    static final int MAX_HISTORY = 1_024;

    private record View(boolean usable, RocketJournalCheckpoint.Acknowledgement acknowledged,
                        Map<UUID, RocketTransferRecord> records, Map<UUID, Long> changes, long forgotten) {
        static final View NONE = new View(false, null, Map.of(), Map.of(), 0);

        long changed(UUID logical) { return changes.getOrDefault(logical, forgotten); }

        /** No journal change of L is newer than its acknowledgement. */
        boolean acknowledged(UUID logical) {
            return usable && acknowledged != null && changed(logical) <= acknowledged.sequence();
        }

        boolean leased(UUID logical, RocketTransferRecord expected, String digest, UUID transfer, long serial, long since) {
            if (!acknowledged(logical) || changed(logical) > since || records.get(logical) != expected) { return false; }
            // L's acknowledged guard: its exact record digest, or no acknowledged record of L at all.
            if (expected == null ? acknowledged.logicalRockets().contains(logical)
                    : digest == null || !digest.equals(acknowledged.records().get(expected.transferId()))) { return false; }
            for (var record : records.values()) {
                if (record == expected) { continue; }
                if (record.transferId().equals(transfer) || serial > 0 && record.nativeTransfer()
                        .map(value -> value.armed().serial() == serial).orElse(record.schemaVersion() > 2)) { return false; }
            }
            return true;
        }
    }

    private final LinkedHashMap<UUID, Long> changes = new LinkedHashMap<>();
    private long forgotten;
    private volatile View view = View.NONE;

    /** Owner thread, after the mutation sequence moved for a change of this logical rocket. */
    void changed(UUID logical, long sequence) {
        changes.remove(logical); changes.put(Objects.requireNonNull(logical), sequence);
        var eldest = changes.entrySet().iterator();
        while (changes.size() > MAX_HISTORY) {
            // A rocket whose history is dropped is treated as changed at the newest dropped change.
            forgotten = Math.max(forgotten, eldest.next().getValue()); eldest.remove();
        }
    }

    /** Owner thread, after every record, acknowledgement or lifecycle change. */
    void publish(boolean usable, RocketJournalCheckpoint.Acknowledgement acknowledged, Iterable<RocketTransferRecord> records) {
        var byLogical = new HashMap<UUID, RocketTransferRecord>();
        for (var record : records) { byLogical.put(record.logicalRocketId(), record); }
        view = new View(usable, acknowledged, Map.copyOf(byLogical), Map.copyOf(changes), forgotten);
    }

    boolean acknowledged(UUID logical) { return view.acknowledged(Objects.requireNonNull(logical)); }

    /** Acquired on the owner thread; {@code since} is the journal's current mutation sequence. */
    BooleanSupplier lease(UUID logical, UUID transfer, long serial, long since) {
        Objects.requireNonNull(logical, "logical");
        var current = view; var expected = current.records().get(logical);
        String digest = expected == null || current.acknowledged() == null ? null
                : current.acknowledged().records().get(expected.transferId());
        if (!current.leased(logical, expected, digest, transfer, serial, since)) { return () -> false; }
        return () -> view.leased(logical, expected, digest, transfer, serial, since);
    }
}
