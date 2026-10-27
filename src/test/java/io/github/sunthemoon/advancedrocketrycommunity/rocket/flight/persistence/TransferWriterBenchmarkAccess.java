package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * W129a B3a: the existing transfer writer at W11B2's maximum fixture, driven through its unchanged package API. The
 * transfer writer itself is not modified.
 */
public final class TransferWriterBenchmarkAccess {
    private TransferWriterBenchmarkAccess() { }

    /** RocketJournalBenchmark's maximum: every transfer slot with a snapshot at the exact legal byte ceiling. */
    public static RocketTransferSavedData maximum() {
        int low = 0, high = RocketLimits.MAX_TOTAL_NBT_BYTES;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            try {
                var candidate = RocketJournalBenchmark.snapshot(middle); RocketSnapshotNbtCodec.encode(candidate);
                RocketSnapshotNbtCodec.encode(candidate.relocated(new UUID(3, 1), RocketFlightPlanner.MOON.dimensionId(),
                        new RocketPosition(0, 80, 0), 160));
                low = middle;
            } catch (RocketSnapshotException tooLarge) { high = middle - 1; }
        }
        var maximum = RocketJournalBenchmark.snapshot(low);
        var full = new RocketTransferSavedData();
        for (int i = 0; i < RocketFlightLimits.MAX_ACTIVE_TRANSFERS; i++) { full.put(RocketJournalBenchmark.record(maximum, i)); }
        return full;
    }

    /** Smoke only: {@code records} transfers with W11B2's small snapshot. */
    public static RocketTransferSavedData small(int records) {
        var journal = new RocketTransferSavedData(); var snapshot = RocketJournalBenchmark.snapshot(0);
        for (int i = 0; i < records; i++) { journal.put(RocketJournalBenchmark.record(snapshot, i)); }
        return journal;
    }

    public static int rootBytes(RocketTransferSavedData journal) {
        return RocketNbtSize.uncompressedBytes(journal.save(new CompoundTag()));
    }

    public static boolean request(RocketTransferSavedData journal, Path file, CheckedSavedDataPolicy policy) {
        return journal.requestWrite(file, policy);
    }

    public static long completedWriteCount(RocketTransferSavedData journal) { return journal.completedWriteCount(); }
    public static long lastWriterNanos(RocketTransferSavedData journal) { return journal.lastWriterNanos(); }
}
