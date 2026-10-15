package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import net.minecraft.server.MinecraftServer;

/** Gated S-only availability cut, after the complete delivery tick and the healthy counterpart's baseline. */
final class FirstEventAvailabilityCut {
    private static FirstEventCutSeams.Cut pending;
    private static int armedTick;
    private static long armedNanos;
    private FirstEventAvailabilityCut() { }

    static void reset() { pending = null; }

    static void arm(MinecraftServer server, FirstEventCutSeams.Cut cut) {
        EscrowTestEndpoints.requireEnabled(server);
        pending = cut; armedTick = server.getTickCount(); armedNanos = System.nanoTime();
    }

    static void status(MinecraftServer server) {
        ClassicFirstEventDelivery.installed(server).orElseThrow().status().forEach(line ->
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture {}", line));
    }

    static void tick(MinecraftServer server) {
        if (pending == null || server.getTickCount() == armedTick) { return; }
        EscrowTestEndpoints.requireEnabled(server);
        if (FirstEventCutSeams.configured().orElseThrow() != pending) { throw new IllegalStateException("Availability cut changed"); }
        String healthy = pending == FirstEventCutSeams.Cut.FP10 || pending == FirstEventCutSeams.Cut.FP3
                ? "first_station_warp" : "first_moon_landing";
        boolean ready = ClassicFirstEventDelivery.installed(server).orElseThrow().status().stream().anyMatch(line ->
                line.startsWith("kind=" + healthy + " source=KNOWN:DELIVERED ")
                        && line.contains(" receipt=CONFIRMED hold=none "));
        if (!ready) {
            if ((long) server.getTickCount() - armedTick > 1_200 || System.nanoTime() - armedNanos > 60_000_000_000L) {
                throw new IllegalStateException("The healthy availability counterpart did not settle within the fixture bound");
            }
            return;
        }
        if (pending == FirstEventCutSeams.Cut.FP4) {
            io.github.sunthemoon.advancedrocketrycommunity.station.warp.FirstEventStationFixtures.warp(server,
                    java.util.UUID.fromString(System.getProperty(FirstEventCutSeams.STATION_PROPERTY, "")));
        }
        status(server);
        if (pending == FirstEventCutSeams.Cut.FP11) {
            io.github.sunthemoon.advancedrocketrycommunity.station.warp.FirstEventStationFixtures.unreadable(server,
                    java.util.UUID.fromString(System.getProperty(FirstEventCutSeams.STATION_PROPERTY, "")));
        }
        FirstEventCutSeams.halt(pending, pending == FirstEventCutSeams.Cut.FP3 || pending == FirstEventCutSeams.Cut.FP4
                ? FirstEventCutSeams.HELD : FirstEventCutSeams.FIRST_STEP, null);
    }
}
