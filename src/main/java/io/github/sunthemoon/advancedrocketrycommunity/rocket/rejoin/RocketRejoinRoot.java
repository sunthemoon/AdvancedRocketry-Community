package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable write-ahead obligations, not rocket or player resource authority. W80 section 2. */
public record RocketRejoinRoot(long nextSequence, List<Batch> batches, List<Entry> entries) {
    public static final int MAX_ENTRIES = 256, MAX_BATCHES = 256, MAX_PER_PLAYER = 4, MAX_PER_BATCH = 16;
    public enum Hold { H1, H2 }
    public enum Placement { PENDING, VERIFIED, NOT_REQUIRED }
    public enum Seat { PENDING, VERIFIED }
    public enum Target { PAD, OVERWORLD_SPAWN }

    public RocketRejoinRoot {
        require(nextSequence >= 0 && batches.size() <= MAX_BATCHES && entries.size() <= MAX_ENTRIES, "Rejoin capacity or sequence");
        batches = List.copyOf(batches); entries = List.copyOf(entries);
        var byId = new HashMap<UUID, Batch>(); var sequences = new HashSet<Long>();
        for (var batch : batches) {
            require(byId.put(batch.release(), batch) == null && sequences.add(batch.sequence())
                    && batch.sequence() < nextSequence, "Duplicate or unissued rejoin batch");
        }
        var keys = new HashSet<Key>(); var perPlayer = new HashMap<UUID, Integer>();
        var perBatch = new HashMap<UUID, Integer>();
        for (var entry : entries) {
            var batch = byId.get(entry.release());
            require(keys.add(entry.key()) && batch != null && batch.released().contains(entry.player()), "Invalid rejoin reference");
            require(!entry.finished(), "Finished entries must retire in the same generation");
            require(perPlayer.merge(entry.player(), 1, Integer::sum) <= MAX_PER_PLAYER, "Player rejoin capacity");
            require(perBatch.merge(entry.release(), 1, Integer::sum) <= MAX_PER_BATCH, "Batch rejoin capacity");
        }
        require(perBatch.keySet().equals(byId.keySet()), "Empty rejoin batch must retire");
    }

    public static RocketRejoinRoot empty() { return new RocketRejoinRoot(0, List.of(), List.of()); }

    /** Storage-only admission; the release coordinator must first supply all gameplay proofs. */
    public RocketRejoinRoot admit(Batch batch, List<Entry> added) {
        require(nextSequence != Long.MAX_VALUE && batch.sequence() == nextSequence, "Rejoin sequence exhausted or stale");
        require(added.size() == batch.released().size(), "Initial release must include every obligation");
        for (var entry : added) {
            require(entry.release().equals(batch.release()) && entry.seat() == Seat.PENDING
                    && entry.placement() != Placement.VERIFIED && entry.attempt() == null, "Invalid initial rejoin entry");
        }
        var nextBatches = new ArrayList<>(batches); nextBatches.add(batch);
        var nextEntries = new ArrayList<>(entries); nextEntries.addAll(added);
        return new RocketRejoinRoot(nextSequence + 1, nextBatches, nextEntries);
    }

    /** No obligation can be removed until both states are final; the last entry also retires its batch. */
    public RocketRejoinRoot update(Entry replacement) {
        var previous = entries.stream().filter(e -> e.key().equals(replacement.key())).findFirst().orElseThrow();
        require(previous.seatIndex() == replacement.seatIndex(), "Frozen seat changed");
        require(previous.seat() != Seat.VERIFIED || replacement.seat() == Seat.VERIFIED, "Seat proof regressed");
        require(previous.placement() == Placement.PENDING || previous.placement() == replacement.placement(), "Placement proof changed");
        require(previous.placement() != Placement.PENDING || replacement.placement() != Placement.NOT_REQUIRED, "Frozen placement classification changed");
        var nextEntries = new ArrayList<>(entries); nextEntries.remove(previous);
        if (!replacement.finished()) { nextEntries.add(replacement); }
        var nextBatches = batches.stream().filter(b -> !b.release().equals(replacement.release())
                || nextEntries.stream().anyMatch(e -> e.release().equals(b.release()))).toList();
        return new RocketRejoinRoot(nextSequence, nextBatches, nextEntries);
    }

    public record Key(UUID release, UUID player) {
        public Key { Objects.requireNonNull(release); Objects.requireNonNull(player); }
    }

    public record Entry(UUID release, UUID player, int seatIndex, Placement placement, Seat seat, Attempt attempt) {
        public Entry {
            Objects.requireNonNull(release); Objects.requireNonNull(player); Objects.requireNonNull(placement); Objects.requireNonNull(seat);
            require(seatIndex >= 0 && seatIndex < MAX_PER_BATCH, "Rejoin seat index");
            require(attempt == null || placement == Placement.PENDING, "Only pending placement has an attempt");
        }
        public Key key() { return new Key(release, player); }
        public boolean finished() { return seat == Seat.VERIFIED && placement != Placement.PENDING; }
    }

    public record Attempt(UUID generation, Target target, double x, double y, double z) {
        public Attempt { Objects.requireNonNull(generation); Objects.requireNonNull(target); coordinates(x, y, z); }
    }

    /** Fixed packed-position limits. Actual dimension height, border and safety belong to placement. */
    public record Position(int x, int y, int z) {
        public Position { coordinates(x, y, z); }
    }

    /** R104: the complete recorded proof census survives the transfer's checked removal. */
    public record Slot(String level, int x, int z) {
        public Slot {
            identifier(level);
            require(x >= -1_875_000 && x < 1_875_000 && z >= -1_875_000 && z < 1_875_000, "Rejoin chunk outside world limits");
        }
    }

    /** Exact resource witnesses, without copying inventories, fluid NBT or a reconstructible rocket payload. */
    public record Resources(String snapshot, String fuel) {
        public Resources { hash(snapshot); hash(fuel); }
    }

    public record Version(String checksum, String phase, UUID destination) {
        public Version {
            hash(checksum);
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase.valueOf(phase);
            require(phase.equals("PREPARED") == (destination == null), "Transfer version destination shape");
        }
    }

    public record Batch(UUID release, long sequence, UUID transfer, UUID rocket, UUID authority, Hold hold,
                        String dimension, Position pad, Position minimum, Position maximum, Resources resources,
                        Version before, Version scrubbed, List<UUID> released, List<Slot> slots) {
        public Batch {
            Objects.requireNonNull(release); Objects.requireNonNull(transfer); Objects.requireNonNull(rocket);
            Objects.requireNonNull(authority); Objects.requireNonNull(hold); Objects.requireNonNull(pad);
            Objects.requireNonNull(minimum); Objects.requireNonNull(maximum); Objects.requireNonNull(resources);
            Objects.requireNonNull(before); Objects.requireNonNull(scrubbed); identifier(dimension);
            require(sequence >= 0 && released.size() >= 1 && released.size() <= MAX_PER_BATCH, "Rejoin batch bounds");
            released = List.copyOf(released);
            require(new HashSet<>(released).size() == released.size(), "Duplicate released player");
            slots = List.copyOf(slots);
            require(!slots.isEmpty() && slots.size() <= 3 && new HashSet<>(slots).size() == slots.size(), "Invalid rejoin slot census");
            require(slots.contains(new Slot(dimension, pad.x() >> 4, pad.z() >> 4)), "Missing rejoin authority slot");
            require(minimum.x() <= maximum.x() && minimum.y() <= maximum.y() && minimum.z() <= maximum.z(), "Reversed region");
            require(before.phase().equals(scrubbed.phase()) && Objects.equals(before.destination(), scrubbed.destination()),
                    "Scrubbing changed the transfer envelope");
        }
    }

    private static void coordinates(double x, double y, double z) {
        require(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && x >= -30_000_000 && x < 30_000_000 && z >= -30_000_000 && z < 30_000_000
                && y >= -2_048 && y < 2_048, "Rejoin coordinates outside world limits");
    }
    static void identifier(String value) {
        require(value != null && value.length() <= 128 && value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"), "Noncanonical dimension");
    }
    static void hash(String value) { require(value != null && value.matches("[0-9a-f]{64}"), "Invalid resource hash"); }
    static void require(boolean valid, String message) { if (!valid) { throw new IllegalArgumentException(message); } }
}
