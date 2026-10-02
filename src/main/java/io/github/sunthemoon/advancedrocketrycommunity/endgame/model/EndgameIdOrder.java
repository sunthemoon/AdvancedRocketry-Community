package io.github.sunthemoon.advancedrocketrycommunity.endgame.model;

import java.util.Comparator;
import java.util.UUID;

/**
 * ADR-054 section 7 ID order: the order of the canonical lowercase UUID strings ({@code String.compareTo}), not
 * {@link UUID#compareTo}, which compares signed halves. Every deterministic endgame order uses it.
 *
 * <p>The canonical string is the 32 lowercase hex digits of the two halves with dashes at fixed places, and the
 * digits {@code 0-9a-f} sort in their numeric order, so that string order is the unsigned order of the most
 * significant half, then of the least significant half. Compared that way it allocates nothing (C13: the root's
 * maps are looked up every tick).
 */
public final class EndgameIdOrder {
    public static final Comparator<UUID> ORDER = (first, second) -> {
        int high = Long.compareUnsigned(first.getMostSignificantBits(), second.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(first.getLeastSignificantBits(),
                second.getLeastSignificantBits());
    };

    private EndgameIdOrder() {
    }

    public static int compare(UUID first, UUID second) {
        return ORDER.compare(first, second);
    }
}
