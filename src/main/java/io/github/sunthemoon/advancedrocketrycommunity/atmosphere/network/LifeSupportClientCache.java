package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import java.util.Optional;

/** One bounded client display snapshot; it has no authority over game state. */
public final class LifeSupportClientCache {
    private static volatile PlayerLifeSupportSnapshot snapshot;

    private LifeSupportClientCache() {
    }

    public static void accept(PlayerLifeSupportSnapshot accepted) {
        snapshot = accepted;
    }

    public static Optional<PlayerLifeSupportSnapshot> current() {
        return Optional.ofNullable(snapshot);
    }

    public static void clear() {
        snapshot = null;
    }
}
