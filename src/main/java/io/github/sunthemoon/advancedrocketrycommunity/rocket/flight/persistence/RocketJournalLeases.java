package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * R17 item 3: the checked journal lease of one logical rocket L. It holds while the journal has the exact acknowledged
 * record instance of L (or the exact checked absence of any record of L), L's acknowledged guard is unchanged, no other
 * record names the operation's T or serial, and no change of L is unacknowledged. Another rocket's change never revokes
 * it. The owner thread publishes an immutable view; a lease reads only that view, so the ledger writer may check it.
 *
 * <p>The conflict exclusion is checked (lease review LR1): it holds in RAM and in the last acknowledged generation. A
 * record that left RAM after that acknowledgement may still be on disk, so it keeps naming its T and serial until an
 * acknowledgement at or after its removal.
 */
final class RocketJournalLeases {
    /** Changes of more distinct rockets, or more unacknowledged removals, than this fold into one conservative floor. */
    static final int MAX_HISTORY = 1_024;

    private record View(boolean usable, RocketJournalCheckpoint.Acknowledgement acknowledged,
                        Map<UUID, RocketTransferRecord> records, Map<UUID, Long> changes, long forgotten,
                        List<RocketTransferRecord> removed, boolean removedForgotten) {
        static final View NONE = new View(false, null, Map.of(), Map.of(), 0, List.of(), false);

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
            if (transfer == null && serial <= 0) { return true; }
            // The acknowledged generation's own transfer index; only L's exact record may name T there.
            if (removedForgotten || transfer != null && acknowledged.records().containsKey(transfer)
                    && (expected == null || !transfer.equals(expected.transferId()))) { return false; }
            for (var record : records.values()) {
                if (record != expected && names(record, transfer, serial)) { return false; }
            }
            // Records that left RAM after the acknowledgement, which may still be in it: their serials and T.
            for (var record : removed) {
                if (names(record, transfer, serial)) { return false; }
            }
            return true;
        }

        private static boolean names(RocketTransferRecord record, UUID transfer, long serial) {
            return record.transferId().equals(transfer) || serial > 0 && record.nativeTransfer()
                    .map(value -> value.armed().serial() == serial).orElse(record.schemaVersion() > 2);
        }
    }

    /** A record instance that left RAM at {@code sequence}: removed, replaced or superseded by a later version. */
    private record Removed(RocketTransferRecord record, long sequence) { }

    private final LinkedHashMap<UUID, Long> changes = new LinkedHashMap<>();
    private long forgotten;
    private final ArrayDeque<Removed> removed = new ArrayDeque<>();
    private long removedFloor;
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

    /** {@link #changed(UUID, long)}, where the change took {@code superseded} (if not null) out of RAM. */
    void changed(UUID logical, long sequence, RocketTransferRecord superseded) {
        changed(logical, sequence);
        if (superseded == null) { return; }
        removed.addLast(new Removed(superseded, sequence));
        while (removed.size() > MAX_HISTORY) {
            // A dropped removal names every T and serial until an acknowledgement at or after it.
            removedFloor = Math.max(removedFloor, removed.removeFirst().sequence());
        }
    }

    /** Owner thread, after every record, acknowledgement or lifecycle change. */
    void publish(boolean usable, RocketJournalCheckpoint.Acknowledgement acknowledged, Iterable<RocketTransferRecord> records) {
        // Acknowledgements move forward only; a removal at or before this one is absent from it and from every later one.
        while (acknowledged != null && !removed.isEmpty() && removed.peekFirst().sequence() <= acknowledged.sequence()) {
            removed.removeFirst();
        }
        var byLogical = new HashMap<UUID, RocketTransferRecord>();
        for (var record : records) { byLogical.put(record.logicalRocketId(), record); }
        view = new View(usable, acknowledged, Map.copyOf(byLogical), Map.copyOf(changes), forgotten,
                removed.stream().map(Removed::record).toList(),
                acknowledged == null || removedFloor > acknowledged.sequence());
    }

    boolean acknowledged(UUID logical) { return view.acknowledged(Objects.requireNonNull(logical)); }

    /** Owner-thread scheduling reads the same published change history, including its conservative overflow floor. */
    boolean unchangedSince(UUID logical, long sequence) { return view.changed(logical) <= sequence; }
    boolean untouched(UUID logical) { return view.changed(logical) == 0; }
    boolean rootAcknowledged(long sequence) {
        var current = view;
        return current.usable() && current.acknowledged() != null && current.acknowledged().sequence() == sequence;
    }

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
