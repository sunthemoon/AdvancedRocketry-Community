package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import java.util.Objects;

/**
 * W170 D5 (W27A-DESIGN section 8, "Journal rewrite"): the one journal mutation of a bay consumption. It rewrites the
 * live PREPARED record T of a logical rocket into T', which keeps the transfer ID, phase, owner, source UUID, flight
 * data, fuel, creation tick and snapshot IDs, and changes only the two snapshots' block-entity payloads and the
 * checksum. The existing off-thread checked writer persists it; the exact acknowledgement is the journal's own. The
 * caller (the bay coordinator) supplies the group's recorded hashes, so a T' outside its frozen group is refused.
 * Server thread only. Kept outside {@link RocketTransferSavedData} so that class stays under 800 lines (R49 Q7).
 */
public final class RocketTransferConsumption {
    /** A refusal names the first check that failed; nothing changed. */
    public enum Result { CONSUMED, ALREADY_CONSUMED, UNAVAILABLE, NOT_LIVE, NOT_PREPARED, GOVERNED, AUTHORITY_CHANGED,
        HASH_OUTSIDE_GROUP, TOO_LARGE }

    private RocketTransferConsumption() { }

    /**
     * Publishes T' for {@code before} if the journal still holds exactly {@code before}. {@code consumed} must be
     * {@code before.withConsumedSnapshots(...)}, and its two content hashes must equal the group's after hashes, while
     * {@code before}'s must equal the group's before hashes. A journal that already holds an equal T' answers
     * ALREADY_CONSUMED (the mixed {@code (S,T')} restart case), changing nothing.
     */
    public static Result apply(RocketTransferSavedData journal, RocketTransferRecord before, RocketTransferRecord consumed,
                               String srcBefore, String dstBefore, String srcAfter, String dstAfter) {
        Objects.requireNonNull(journal); Objects.requireNonNull(before); Objects.requireNonNull(consumed);
        if (!journal.operational() || !journal.schedulingAvailable()) { return Result.UNAVAILABLE; }
        var live = journal.find(before.transferId()).orElse(null);
        if (live != null && live != before && !before.checksum().equals(consumed.checksum())
                && live.checksum().equals(consumed.checksum())) { return Result.ALREADY_CONSUMED; }
        if (live != before) { return Result.NOT_LIVE; }
        if (before.phase() != RocketTransferPhase.PREPARED || consumed.phase() != RocketTransferPhase.PREPARED) {
            return Result.NOT_PREPARED;
        }
        if (before.nativeTransfer().isPresent() || consumed.nativeTransfer().isPresent()) { return Result.GOVERNED; }
        if (!same(before, consumed)) { return Result.AUTHORITY_CHANGED; }
        if (!before.sourceSnapshot().contentHash().equals(srcBefore) || !before.destinationSnapshot().contentHash().equals(dstBefore)
                || !consumed.sourceSnapshot().contentHash().equals(srcAfter)
                || !consumed.destinationSnapshot().contentHash().equals(dstAfter)) {
            return Result.HASH_OUTSIDE_GROUP;
        }
        if (RocketNbtSize.uncompressedBytes(RocketTransferJournalCodec.encodeRecord(consumed))
                > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
            return Result.TOO_LARGE;
        }
        journal.consumed(before, consumed);
        return Result.CONSUMED;
    }

    /** Everything but the two snapshots' payloads and the checksum is unchanged. */
    static boolean same(RocketTransferRecord before, RocketTransferRecord after) {
        return before.schemaVersion() == after.schemaVersion() && before.transferId().equals(after.transferId())
                && before.logicalRocketId().equals(after.logicalRocketId()) && before.ownerId().equals(after.ownerId())
                && before.sourceEntityId().equals(after.sourceEntityId())
                && before.destinationEntityId().equals(after.destinationEntityId())
                && before.sourceFlightData().equals(after.sourceFlightData())
                && before.destinationFlightData().equals(after.destinationFlightData())
                && before.requiredFuel() == after.requiredFuel() && before.createdAtGameTime() == after.createdAtGameTime()
                && before.sourceSnapshot().snapshotId().equals(after.sourceSnapshot().snapshotId())
                && before.destinationSnapshot().snapshotId().equals(after.destinationSnapshot().snapshotId())
                && !before.checksum().equals(after.checksum());
    }
}
