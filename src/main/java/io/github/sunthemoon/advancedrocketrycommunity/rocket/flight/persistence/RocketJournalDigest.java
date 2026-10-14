package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import net.minecraft.nbt.CompoundTag;

/** NC1 sorted-key framing streamed to SHA-256, without allocating a journal-sized canonical buffer. */
final class RocketJournalDigest {
    // Snapshot/flight roots permit depth 32; transfers/list/root add three levels.
    private static final int MAX_DEPTH = 35;

    private RocketJournalDigest() { }

    static String of(CompoundTag payload, int maxBytes) {
        return CanonicalNbtDigest.of(payload, maxBytes, MAX_DEPTH);
    }
}
