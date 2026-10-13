package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Station lifecycle callbacks for an advisory store kept beside the registry (the ADR-065 section 5.3 offline notices).
 * {@link StationManager} calls them after its own change is flushed; an implementation must not throw.
 */
public interface StationLifecycleListener {
    StationLifecycleListener NONE = new StationLifecycleListener() {
    };

    default void created(MinecraftServer server, StationState station) {
    }

    /** A member was removed or ownership moved; {@code station} is the updated record. */
    default void teamChanged(MinecraftServer server, StationState station) {
    }

    default void deleted(MinecraftServer server, UUID stationId) {
    }
}
