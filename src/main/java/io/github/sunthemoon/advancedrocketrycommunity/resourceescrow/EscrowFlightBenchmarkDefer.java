package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventBenchmarkHold;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Holds first-event availability at its gated disposition seam during the first live descent. */
final class EscrowFlightBenchmarkDefer implements AutoCloseable {
    private FirstEventBenchmarkHold hold;
    private UUID firstLogical;

    void arm(MinecraftServer server, RocketTransferSavedData journal) {
        // R23 item 4: release-test hooks wait for a clean root, not only an acknowledged-current one (W91-R5).
        if (hold != null || !journal.clean()) { return; }
        var first = journal.entries().stream().filter(record -> record.phase() == RocketTransferPhase.COMMITTED)
                .filter(record -> {
                    var level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION, record.destinationSnapshot().sourceDimension()));
                    return level != null && record.destinationEntityId().map(level::getEntity).orElse(null) instanceof RocketEntity rocket
                            && rocket.flightData().orElseThrow().state() == RocketFlightState.DESCENT;
                }).findFirst().orElse(null);
        if (first == null) { return; }
        EscrowTags.require(journal.firstMoonLandingRam().isEmpty(), "Benchmark hold missed the first Moon election");
        firstLogical = first.logicalRocketId();
        hold = FirstEventCutSeams.holdBenchmarkDisposition(server);
    }

    void verify() {
        EscrowTags.require(hold != null && hold.released() && hold.deferredVisits() >= 3 && hold.deferredTicks() >= 3,
                "Benchmark did not exercise repeated real world-first DEFER visits");
    }

    java.util.Map<String, Object> report() {
        return java.util.Map.of("first_logical", String.valueOf(firstLogical), "released", hold != null && hold.released(),
                "deferred_visits", hold == null ? 0 : hold.deferredVisits(), "deferred_ticks", hold == null ? 0 : hold.deferredTicks(),
                "minimum_hold_ticks", FirstEventBenchmarkHold.DEFER_TICKS,
                "cause", "gated disposition source holds ALLOW at DEFER; no writer acknowledgement is changed");
    }

    @Override public void close() { if (hold != null) { hold.close(); } }
}
