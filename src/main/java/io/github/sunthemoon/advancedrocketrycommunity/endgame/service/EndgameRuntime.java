package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import java.util.Objects;
import java.util.Optional;

/** Narrow lifecycle bridge from block entities and menus to the installed endgame service. */
public final class EndgameRuntime {
    private static volatile EndgameService service;

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
}
