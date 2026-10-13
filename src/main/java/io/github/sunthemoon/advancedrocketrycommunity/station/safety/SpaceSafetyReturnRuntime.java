package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import java.util.Objects;
import java.util.Optional;

/** Narrow lifecycle bridge to the installed safety return service: installed at server start, cleared at stop. */
public final class SpaceSafetyReturnRuntime {
    private static volatile SpaceSafetyReturnService service;

    private SpaceSafetyReturnRuntime() {
    }

    public static void install(SpaceSafetyReturnService installed) {
        service = Objects.requireNonNull(installed, "installed");
    }

    public static Optional<SpaceSafetyReturnService> current() {
        return Optional.ofNullable(service);
    }

    public static void clear() {
        service = null;
    }
}
