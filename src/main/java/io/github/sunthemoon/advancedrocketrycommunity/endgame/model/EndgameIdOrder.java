package io.github.sunthemoon.advancedrocketrycommunity.endgame.model;

import java.util.Comparator;
import java.util.UUID;

/**
 * ADR-054 section 7 ID order: the order of the canonical lowercase UUID strings ({@code String.compareTo}), not
 * {@link UUID#compareTo}, which compares signed halves. Every deterministic endgame order uses it.
 */
public final class EndgameIdOrder {
    public static final Comparator<UUID> ORDER = Comparator.comparing(UUID::toString);

    private EndgameIdOrder() {
    }

    public static int compare(UUID first, UUID second) {
        return ORDER.compare(first, second);
    }
}
