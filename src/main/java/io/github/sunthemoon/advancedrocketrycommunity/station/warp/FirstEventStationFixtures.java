package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Gated complete station-world fixture; creation, charging and relocation use the existing services. */
public final class FirstEventStationFixtures {
    private FirstEventStationFixtures() { }

    private static void gate(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        var cut = FirstEventCutSeams.configured().orElseThrow();
        if (!cut.name().startsWith("FW") && !cut.name().startsWith("FP")) {
            throw new IllegalStateException("No station first-event row is armed");
        }
    }

    public static void create(MinecraftServer server) {
        gate(server);
        var data = StationRegistrySavedData.get(server);
        if (!data.stations().isEmpty() || !data.reservations().isEmpty()) {
            throw new IllegalStateException("The station-world fixture requires an empty registry");
        }
        var earth = ModIdentity.id("earth");
        var moon = ModIdentity.id("moon");
        CelestialSavedData.get(server).discover(earth, server.overworld().getGameTime());
        CelestialSavedData.get(server).discover(moon, server.overworld().getGameTime());
        var creation = new StationCreationService(new StationPlatformGenerator(), id -> id.equals(earth) || id.equals(moon));
        for (int index = 0; index < 2; index++) {
            var result = creation.create(server, index == 0 ? FirstEventCutSeams.P1 : FirstEventCutSeams.P2,
                    "First-event fixture " + index, earth, false);
            if (!result.success()) { throw new IllegalStateException("Station fixture creation failed: " + result.code()); }
            var station = result.station().orElseThrow();
            data.foldWarpCredits(Map.of(station.stationId(), 4_000_000));
            data.flush(server);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture station index={} id={} orbit={} energy={}",
                    index, station.stationId(), station.orbitBody(), data.warpEnergy(station.stationId()));
        }
    }

    public static void warp(MinecraftServer server, UUID station) {
        gate(server);
        var result = StationWarpRuntime.service().orElseThrow().warpForFirstEventReleaseTest(server, station, ModIdentity.id("moon"));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture warp_result station={} code={} balance={}",
                station, result.code(), result.balance());
    }
}
