package io.github.sunthemoon.advancedrocketrycommunity.satellite.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client display cache for ADR-049 section 10: the last terminal view received. A screen shows it only
 * when it names that screen's container; the cache is cleared when the screen closes and on logout.
 */
public final class SatelliteTerminalViewCache {
    private static final AtomicReference<Optional<SatelliteTerminalViewPacket>> VIEW =
            new AtomicReference<>(Optional.empty());

    private SatelliteTerminalViewCache() {
    }

    public static void accept(SatelliteTerminalViewPacket packet) {
        VIEW.set(Optional.of(packet));
    }

    public static Optional<SatelliteTerminalViewPacket> view(int containerId) {
        return VIEW.get().filter(packet -> packet.containerId() == containerId);
    }

    public static void clear() {
        VIEW.set(Optional.empty());
    }
}
