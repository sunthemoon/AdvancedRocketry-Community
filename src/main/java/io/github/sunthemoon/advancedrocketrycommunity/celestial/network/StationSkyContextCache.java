package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/**
 * Client display cache for ADR-047: the last station sky context received. Cleared on logout and on
 * a Level change; the server re-sends the current context within its next pass.
 */
public final class StationSkyContextCache {
    private static final AtomicReference<Optional<ResourceLocation>> ORBIT = new AtomicReference<>(Optional.empty());

    private StationSkyContextCache() {
    }

    public static void accept(StationSkyContextPacket packet) {
        ORBIT.set(packet.orbitBody());
    }

    public static Optional<ResourceLocation> orbitBody() {
        return ORBIT.get();
    }

    public static void clear() {
        ORBIT.set(Optional.empty());
    }
}
