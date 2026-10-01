package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameIdOrder;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** ADR-054 section 11 transfer identity {@code (source, seq)}; ordered by the source's ID order, then by seq. */
public record TransitKey(UUID source, long seq) implements Comparable<TransitKey> {
    public static final Comparator<TransitKey> ORDER = Comparator.comparing(TransitKey::source, EndgameIdOrder.ORDER)
            .thenComparingLong(TransitKey::seq);

    public TransitKey {
        Objects.requireNonNull(source, "source");
        if (seq < 1L) {
            throw new IllegalArgumentException("A transfer seq starts at 1");
        }
    }

    @Override
    public int compareTo(TransitKey other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return source + "/" + seq;
    }
}
