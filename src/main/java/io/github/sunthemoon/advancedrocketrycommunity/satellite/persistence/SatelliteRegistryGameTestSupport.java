package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import net.minecraft.server.MinecraftServer;

/** Read-only writer observations for GameTest fixture setup; never requests, polls or awaits a write. */
public final class SatelliteRegistryGameTestSupport {
    private SatelliteRegistryGameTestSupport() { }

    /** Includes both an unpublished capture and a cadence-delayed request. */
    public static boolean busy(MinecraftServer server, SatelliteMissionSavedData data) {
        SatelliteRegistryTestAwait.require(server);
        return data.writerBusy();
    }

    public static boolean inFlight(MinecraftServer server, SatelliteMissionSavedData data) {
        SatelliteRegistryTestAwait.require(server);
        return data.writerInFlight();
    }
}
