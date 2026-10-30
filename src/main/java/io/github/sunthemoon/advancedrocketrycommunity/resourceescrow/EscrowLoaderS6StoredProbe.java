package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.server.MinecraftServer;

/** R202's exactly selected fixture-owned native phase stores. */
final class EscrowLoaderS6StoredProbe {
    private EscrowLoaderS6StoredProbe() { }
    static boolean selected(String cut) {
        return "LF_S6_F3_ASCENT".equals(cut) || "LF_S6_F3_TRANSIT".equals(cut) || "LF_S6_F4_STORED".equals(cut);
    }
    static boolean ready(String cut, RocketFlightState phase, double height) {
        if (!Double.isFinite(height) || height < 0 || height > 48) { return false; }
        return "LF_S6_F3_ASCENT".equals(cut) && phase == RocketFlightState.ASCENT && height >= 12
                || "LF_S6_F3_TRANSIT".equals(cut) && phase == RocketFlightState.TRANSIT && height == 48;
    }

    static boolean begin(MinecraftServer server, String cut, RocketEntity endpoint) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(cut), "S6 fixture-owned phase store is not authorized for this selection");
        var frozen = new EscrowFlightSource(endpoint); var runtime = EscrowRuntime.of(server);
        // Identical store/force/read-back mechanism to the established source-clock/destination fixtures.
        // No live state is changed and this proof is not supplied to the production flight executor.
        return runtime.barrier().begin(runtime.store(server.overworld()), frozen.endpoint, frozen.frontier,
                frozen.validator(), server.getTickCount(), result -> {
                    EscrowTags.require(result.confirmed(), "S6 fixture-owned phase store/force/readback failed: " + result.reason());
                    result.proof().release();
                    var phase = frozen.envelope.getCompound("flight");
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_STORED cut={} owner=FIXTURE phase={} phase_time={} digest={}",
                            cut, phase.getString("state"), phase.getLong("state_started_game_time"), frozen.digest);
                    EscrowLoaderS6JournalProbe.pause(cut, "FIXTURE_PHASE_READBACK");
                });
    }
}
