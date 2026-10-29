package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.nbt.CompoundTag;

/** Read-only sizing for insertion into a journal of already validated, immutable records. */
final class RocketJournalInsertionBudget {
    private RocketJournalInsertionBudget() { }

    static void validate(List<RocketTransferRecord> existing, RocketTransferRecord candidate, FirstEventOutbox outbox) {
        validate(existing, candidate, records -> RocketNbtSize.uncompressedBytes(
                RocketTransferSavedData.encode(records, outbox, new CompoundTag())));
    }

    /** The encoder retains every codec/record/root check. The seam counts encodes in unit tests. */
    static void validate(List<RocketTransferRecord> existing, RocketTransferRecord candidate,
                         ToIntFunction<List<RocketTransferRecord>> encodeBytes) {
        int singleton = encodeBytes.applyAsInt(List.of(candidate));
        // The singleton already includes the actual schema/outbox/list framing and the checked candidate.
        // Each resident record was bounded at admission. As a list member it uses LESS than its standalone
        // NBT size (no named-root header), so adding the unchanged per-record ceiling is conservative.
        long upper = singleton + (long) existing.size() * RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES;
        if (upper <= RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES) { return; }
        // Do not turn a conservative estimate into a new refusal if a future framing/budget no longer fits it.
        var all = new ArrayList<>(existing); all.add(candidate); encodeBytes.applyAsInt(all);
    }
}
