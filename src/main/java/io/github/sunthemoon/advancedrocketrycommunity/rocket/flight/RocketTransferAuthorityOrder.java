package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Comparator;
import java.util.UUID;

/** Prefer the journal binding; UUID order only breaks ties among unbound candidates. */
public final class RocketTransferAuthorityOrder {
    private RocketTransferAuthorityOrder() {
    }

    public static Comparator<UUID> preferred(UUID expected) {
        return Comparator.comparing((UUID candidate) -> !candidate.equals(expected))
                .thenComparing(Comparator.naturalOrder());
    }
}
