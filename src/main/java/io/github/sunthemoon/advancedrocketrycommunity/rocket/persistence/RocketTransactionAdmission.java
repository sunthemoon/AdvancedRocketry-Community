package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Owner-thread, RAM-only sizing of the journal's immutable residents. Never a disk acknowledgement. */
final class RocketTransactionAdmission {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final int MAXIMUM = Math.toIntExact(TYPE.maxUncompressedBytes());
    private static final int ENTRY_DEPTH = RocketTransactionWriter.DIGEST_DEPTH - 2;
    // One row per resident ID (at most 256); no candidate or encoded NBT is retained.
    private final Map<UUID, SizedEntry> sizes = new HashMap<>();

    /** Refresh only the changed entry. A sizing failure must not introduce a new RAM-write/load refusal. */
    void replace(RocketPersistedTransaction entry) {
        var previous = sizes.remove(entry.record().transactionId());
        try {
            BoundedNbt.Size metadata;
            long snapshotBytes;
            int snapshotNodes;
            if (previous != null && previous.entry().snapshot() == entry.snapshot()) {
                // Phase/progress writes share the same immutable payload: only metadata changed.
                metadata = measure(RocketTransactionCodec.encodeMetadata(entry));
                snapshotBytes = previous.snapshotBytes();
                snapshotNodes = previous.snapshotNodes();
            } else {
                var encoded = RocketTransactionCodec.encodeEntry(entry);
                var full = measure(encoded);
                if (full == null) { return; }
                encoded.remove("snapshot");
                metadata = measure(encoded);
                if (metadata == null) { return; }
                // Includes the snapshot field name/type; the two root headers cancel exactly.
                snapshotBytes = full.bytes() - metadata.bytes();
                snapshotNodes = full.nodes() - metadata.nodes();
            }
            if (metadata != null && metadata.bytes() + snapshotBytes <= MAXIMUM
                    && (long) metadata.nodes() + snapshotNodes <= MAXIMUM) {
                sizes.put(entry.record().transactionId(), new SizedEntry(entry,
                        metadata.bytes() + snapshotBytes, snapshotBytes, snapshotNodes));
            }
        } catch (RuntimeException unmeasured) {
            // Leave uncached. canAppend performs the original check/exception behavior if it reaches this entry.
        }
    }

    void remove(UUID transaction) { sizes.remove(transaction); }
    void clear() { sizes.clear(); }

    boolean canAppend(Collection<RocketPersistedTransaction> residents, RocketPersistedTransaction candidate) {
        var header = new CompoundTag(); SavedDataSchemaMigrator.stampCurrent(TYPE, header);
        header.put("transactions", new ListTag());
        var measured = BoundedNbt.measure(header, MAXIMUM, RocketTransactionWriter.DIGEST_DEPTH, MAXIMUM);
        if (measured == null) { return false; }
        long bytes = measured.bytes();
        for (var entry : residents) {
            var cached = sizes.get(entry.record().transactionId());
            long size = cached != null && cached.entry() == entry ? cached.bytes() : encodedBytes(entry);
            if (size < 0) { return false; }
            bytes += size - 3; // List children have no named-root header.
            if (bytes > MAXIMUM) { return false; }
        }
        long candidateBytes = encodedBytes(candidate);
        return candidateBytes >= 0 && bytes + candidateBytes - 3 <= MAXIMUM;
    }

    private static long encodedBytes(RocketPersistedTransaction entry) {
        try {
            var size = measure(RocketTransactionCodec.encodeEntry(entry));
            return size == null ? -1 : size.bytes();
        } catch (IllegalArgumentException invalid) { return -1; }
    }

    private static BoundedNbt.Size measure(CompoundTag entry) {
        return BoundedNbt.measure(entry, MAXIMUM, ENTRY_DEPTH, MAXIMUM);
    }

    private record SizedEntry(RocketPersistedTransaction entry, long bytes, long snapshotBytes, int snapshotNodes) { }
}
