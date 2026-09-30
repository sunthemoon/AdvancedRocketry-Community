package io.github.sunthemoon.advancedrocketrycommunity.station.orbit;

import java.util.Objects;
import java.util.Optional;

/**
 * Holds the one registered station sky context service, installed once at mod construction and kept
 * across integrated-server sessions (the service clears its own state at stop). GameTests use it to
 * observe the production wiring (final v1.5 review B1).
 */
public final class StationSkyContextRuntime {
    private static volatile StationSkyContextService service;

    private StationSkyContextRuntime() {
    }

    public static void install(StationSkyContextService installed) {
        service = Objects.requireNonNull(installed, "installed");
    }

    public static Optional<StationSkyContextService> service() {
        return Optional.ofNullable(service);
    }
}
