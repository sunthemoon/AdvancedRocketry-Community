package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Recreates the two requests in an autosave tick without waiting for the 6,000-tick periodic autosave. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteRegistryWindowGameTests {
    private SatelliteRegistryWindowGameTests() { }

    @GameTest(template = "empty", batch = "satellite_registry_window", timeoutTicks =
            SatelliteRegistryTickGameTests.REQUEST_TICKS + SatelliteRegistryTickGameTests.REQUEST_SETUP_TICKS)
    public static void anInheritedCadenceRequestDrainsBeforeTheMeasuredWindow(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var data = SatelliteMissionSavedData.get(server);
        boolean[] prepared = {false};
        long[] coalesced = {SatelliteRuntime.coalescedFlushes()};
        int[] quietSince = {server.getTickCount()}, requestedAt = {-1};
        helper.onEachTick(() -> {
            if (prepared[0]) { return; }
            int now = server.getTickCount();
            if (requestedAt[0] < 0) {
                if (coalesced[0] != SatelliteRuntime.coalescedFlushes()) {
                    coalesced[0] = SatelliteRuntime.coalescedFlushes();
                    quietSince[0] = now;
                }
                // The next scheduler END pass must be eligible for a coalesced request.
                if (now - quietSince[0] < 120 || server.overworld().getGameTime() % 20 != 0
                        || GameTestAutosave.within(server, SatelliteRegistryTickGameTests.REQUEST_TICKS + 20)
                        || SatelliteRegistryGameTestSupport.busy(server, data)) { return; }
                UUID seed = UUID.randomUUID();
                helper.assertTrue(data.launchIdle(time -> SatelliteState.launchIdle(seed, ModIdentity.id("solar_satellite"),
                        UUID.randomUUID(), time, CelestialIds.EARTH_ID,
                        new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("solar_transmitter_module")),
                                false, new SatelliteStats(4, 720, 0, 0, 10)),
                        new SatelliteKindState.Solar(100, Optional.empty())), server.overworld().getGameTime()).success(),
                        "Could not seed the cadence regression");
                // Same request path as vanilla's autosave. Its capture precedes the END-phase coalesced request.
                server.overworld().getDataStorage().save();
                helper.assertTrue(SatelliteRegistryGameTestSupport.inFlight(server, data), "The autosave-style capture did not start");
                // A later sequence keeps END's request pending when the first capture publishes.
                data.updateKindState(seed, new SatelliteKindState.Solar(100, Optional.of(UUID.randomUUID())));
                requestedAt[0] = now;
            } else {
                helper.assertTrue(SatelliteRuntime.coalescedFlushes() == coalesced[0] + 1,
                        "The END-phase coalesced request was not issued after the autosave-style capture");
                helper.assertTrue(now - requestedAt[0] < 10,
                        "The first capture did not publish inside the regression's cadence window");
                if (SatelliteRegistryGameTestSupport.inFlight(server, data)) { return; }
                helper.assertTrue(SatelliteRegistryGameTestSupport.busy(server, data),
                        "The cadence did not retain a pending request after publication");
                prepared[0] = true;
                AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_REGISTRY_WINDOW_REGRESSION request_tick={} body_ready_tick={} busy=true in_flight=false coalesced={}",
                        requestedAt[0], now, SatelliteRuntime.coalescedFlushes());
            }
        });
        // All scheduling is registered here, never from the setup callback.
        SatelliteRegistryTickGameTests.requests(helper, () -> prepared[0]);
    }
}
