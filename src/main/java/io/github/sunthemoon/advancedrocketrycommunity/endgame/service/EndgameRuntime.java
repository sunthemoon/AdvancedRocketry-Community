package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import java.util.Objects;
import java.util.Optional;

/** Narrow lifecycle bridge from block entities and menus to the installed endgame service. */
public final class EndgameRuntime {
    private static volatile EndgameService service;
    private static volatile EndgameDevices devices;
    private static final EndgameTimings DETACHED = new EndgameTimings();

    private EndgameRuntime() {
    }

    public static void install(EndgameService installed) {
        service = Objects.requireNonNull(installed, "installed");
    }

    /** The service, only while the endgame root is operational (section 10). */
    public static Optional<EndgameService> operational() {
        EndgameService current = service;
        return current != null && current.operational() ? Optional.of(current) : Optional.empty();
    }

    public static Optional<EndgameService> service() {
        return Optional.ofNullable(service);
    }

    /** The installed service's work measurement (ADR-054 section 7); a detached one before installation. */
    public static EndgameTimings timings() {
        EndgameService current = service;
        return current == null ? DETACHED : current.timings();
    }

    public static void installDevices(EndgameDevices installed) {
        devices = Objects.requireNonNull(installed, "installed");
    }

    /** The device runtime, only while the endgame root is operational. */
    public static Optional<EndgameDevices> devices() {
        EndgameDevices current = devices;
        return current != null && operational().isPresent() ? Optional.of(current) : Optional.empty();
    }
}
