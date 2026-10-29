package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.nbt.CompoundTag;

/** Read-only sizing for insertion into a journal of already validated, immutable records. */
final class RocketJournalInsertionBudget {
    private RocketJournalInsertionBudget() { }

    static void validate(List<RocketTransferRecord> existing, RocketTransferRecord candidate, FirstEventOutbox outbox,
                         boolean canonicalResidents) {
        validate(existing, candidate, outbox, canonicalResidents, RocketTransferJournalCodec::encodeRecord);
    }

    static void validate(List<RocketTransferRecord> existing, RocketTransferRecord candidate, FirstEventOutbox outbox,
                         boolean canonicalResidents, java.util.function.Function<RocketTransferRecord, CompoundTag> encoder) {
        validate(existing, candidate, canonicalResidents, records -> RocketNbtSize.uncompressedBytes(
                RocketTransferJournalCodec.encodeRoot(records, outbox, new CompoundTag(), encoder)));
    }

    /** The encoder retains every codec/record/root check. The seam counts encodes in unit tests. */
    static void validate(List<RocketTransferRecord> existing, RocketTransferRecord candidate,
                         boolean canonicalResidents, ToIntFunction<List<RocketTransferRecord>> encodeBytes) {
        if (!canonicalResidents) { validateWholeRoot(existing, candidate, encodeBytes); return; }
        int singleton = encodeBytes.applyAsInt(List.of(candidate));
        // The singleton already includes the actual schema/outbox/list framing and the checked candidate.
        // The journal's witness proves each resident's canonical encode. A list member uses LESS than its standalone
        // NBT size (no named-root header), so adding the unchanged per-record ceiling is conservative.
        long upper = singleton + (long) existing.size() * RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES;
        if (upper <= RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES) { return; }
        // Do not turn a conservative estimate into a new refusal if a future framing/budget no longer fits it.
        validateWholeRoot(existing, candidate, encodeBytes);
    }

    private static void validateWholeRoot(List<RocketTransferRecord> existing, RocketTransferRecord candidate,
                                         ToIntFunction<List<RocketTransferRecord>> encodeBytes) {
        var all = new ArrayList<>(existing); all.add(candidate); encodeBytes.applyAsInt(all);
    }

    static void ensureNoAuthorityConflict(java.util.Collection<RocketTransferRecord> entries, RocketTransferRecord candidate) {
        RocketRegion destination = RocketRegion.fromSnapshot(candidate.destinationSnapshot());
        for (RocketTransferRecord existing : entries) {
            if (existing.transferId().equals(candidate.transferId())) {
                continue;
            }
            if (existing.logicalRocketId().equals(candidate.logicalRocketId())) {
                throw new IllegalArgumentException("Logical rocket already has an active transfer");
            }
            if (RocketRegion.fromSnapshot(existing.destinationSnapshot()).overlaps(destination)) {
                throw new IllegalArgumentException("Transfer destination overlaps an active reservation");
            }
        }
    }
}
