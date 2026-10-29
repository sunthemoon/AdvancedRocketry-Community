package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;

/** Owner-thread insertion encoding boundary, separate from durable acknowledgement and journal authority. */
final class RocketCheckedInsertion {
    private final Function<RocketTransferRecord, CompoundTag> encoder;
    private RocketTransferRecord candidate;
    private long sequence;

    RocketCheckedInsertion() { this(RocketTransferJournalCodec::encodeRecord); }
    RocketCheckedInsertion(Function<RocketTransferRecord, CompoundTag> encoder) {
        this.encoder = Objects.requireNonNull(encoder);
    }

    CompoundTag encode(RocketTransferRecord record) { return encoder.apply(record); }

    /** Retain the successful encoding check, not mutable NBT or another copy of its payload. */
    void validated(RocketTransferRecord record, long mutationSequence) { candidate = record; sequence = mutationSequence; }
    void clear() { candidate = null; }

    void check(RocketTransferRecord record, long mutationSequence, boolean canonicalResidents) {
        boolean checked = canonicalResidents && candidate == record && sequence == mutationSequence;
        clear(); // Single-use even when put's later identity/capacity check refuses.
        if (checked) { return; }
        if (RocketNbtSize.uncompressedBytes(encode(record)) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
            throw new IllegalArgumentException("Transfer record exceeds the fixed NBT limit");
        }
    }
}
