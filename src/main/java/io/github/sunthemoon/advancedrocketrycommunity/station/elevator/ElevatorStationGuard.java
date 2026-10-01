package io.github.sunthemoon.advancedrocketrycommunity.station.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/**
 * ADR-059 section 5 port: whether space-elevator pairs pin a station. The endgame module implements it from its root and
 * installs it at startup, so the station module never imports the endgame module. Until it is installed, and while the
 * endgame root is not operational, warps and station deletion are refused (fail closed), whatever the elevator switch.
 */
public interface ElevatorStationGuard {
    /** Used until the endgame module installs its guard: every warp and deletion is refused. */
    ElevatorStationGuard FAIL_CLOSED = new ElevatorStationGuard() {
        @Override
        public Decision warp(MinecraftServer server, StationState station) {
            return Decision.UNAVAILABLE;
        }

        @Override
        public Decision delete(MinecraftServer server, StationState station) {
            return Decision.UNAVAILABLE;
        }
    };

    enum Decision {
        ALLOWED,
        /** A pair names the station (warp), or a pair or a transit record does (deletion). */
        BOUND,
        /** The endgame root is not operational. */
        UNAVAILABLE
    }

    /** Warp request, confirmation and commit, read on the server thread right before each. */
    Decision warp(MinecraftServer server, StationState station);

    /** Deletion: also refused while a transit record's source or destination stands in the station's region. */
    Decision delete(MinecraftServer server, StationState station);

    /** The installed guard; a single port reference, kept across integrated-server sessions. */
    final class Installed {
        private static volatile ElevatorStationGuard guard = FAIL_CLOSED;

        private Installed() {
        }

        public static void install(ElevatorStationGuard installed) {
            guard = Objects.requireNonNull(installed, "installed");
        }

        public static ElevatorStationGuard current() {
            return guard;
        }
    }
}
