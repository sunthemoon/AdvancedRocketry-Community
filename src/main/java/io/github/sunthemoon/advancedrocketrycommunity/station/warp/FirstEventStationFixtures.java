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
        if (!cut.name().startsWith("FW") && !cut.name().startsWith("FP") && cut != FirstEventCutSeams.Cut.FM9) {
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

    public static void unreadable(MinecraftServer server, UUID station) {
        gate(server);
        if (FirstEventCutSeams.configured().orElseThrow() != FirstEventCutSeams.Cut.FP11) { throw new IllegalStateException("FP11 is not armed"); }
        var creation = new StationCreationService(new StationPlatformGenerator(), id -> true)
                .create(server, FirstEventCutSeams.P1, "Unavailable fixture", ModIdentity.id("earth"), false);
        var warp = StationWarpRuntime.service().orElseThrow().warpForFirstEventReleaseTest(server, station, ModIdentity.id("earth"));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture unavailable creation={} warp={}", creation.code(), warp.code());
        if (creation.code() != io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationCode.REGISTRY_BLOCKED
                || warp.code() != io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode.AUTHORITY_UNAVAILABLE) {
            throw new IllegalStateException("An unreadable registry accepted a fixture operation");
        }
    }

    public static void probeFrozen(MinecraftServer server) {
        gate(server);
        var data = StationRegistrySavedData.get(server);
        var event = (io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent.StationWarp)
                data.durableFirstWarp().knownEvent().orElseThrow();
        var before = data.save(new net.minecraft.nbt.CompoundTag());
        boolean refused = false;
        try { data.addMember(event.stationId(), FirstEventCutSeams.P2); }
        catch (IllegalStateException expected) { refused = true; }
        var credits = data.foldWarpCredits(Map.of(event.stationId(), 1));
        var committed = StationRegistrySavedData.load(before.copy()).find(event.stationId()).orElseThrow();
        var altitude = data.checkedSetAltitude(server, committed, 5);
        if (!refused || credits.credited() != 0 || altitude != StationRegistrySavedData.CheckedUpdate.UNAVAILABLE
                || !before.equals(data.save(new net.minecraft.nbt.CompoundTag()))) {
            throw new IllegalStateException("A frozen registry accepted a fixture mutation");
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture frozen mutation_refused={} credited={} altitude={}",
                refused, credits.credited(), altitude);
    }
}
