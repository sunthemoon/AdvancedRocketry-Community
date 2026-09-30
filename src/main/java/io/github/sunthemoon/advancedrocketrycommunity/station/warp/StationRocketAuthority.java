package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/**
 * ADR-044 §5 port: whether a rocket that can still move overlaps the station's region. Implemented
 * by the rocket module from its transfer journal alone (no entity query, no chunk load) and wired
 * at startup. Returning {@code true} blocks a warp.
 */
@FunctionalInterface
public interface StationRocketAuthority {
    /** Used until the rocket module installs its implementation: every warp is blocked. */
    StationRocketAuthority FAIL_CLOSED = (server, station) -> true;

    boolean inMotion(MinecraftServer server, StationState station);

    /** One bounded line for operators, e.g. the transfer journal's classification counts. */
    default Optional<String> diagnostics(MinecraftServer server) {
        return Optional.empty();
    }
}
