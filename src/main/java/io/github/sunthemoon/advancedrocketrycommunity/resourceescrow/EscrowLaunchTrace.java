package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Stateless INFO observations, enabled only for explicitly gated release-test launches. */
public final class EscrowLaunchTrace {
    public static final String PROPERTY = "advancedrocketrycommunity.escrowLaunchTrace";
    private EscrowLaunchTrace() { }

    static boolean selected(boolean hooks, boolean trace) { return hooks && trace; }

    static void note(RocketEntity rocket, RocketFlightData countdown, String check, String detail) {
        if (!selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), Boolean.getBoolean(PROPERTY))) { return; }
        note(rocket, countdown.plan().map(plan -> plan.requestId()).orElse(null), check, detail);
    }

    public static void note(RocketEntity rocket, UUID request, String check, String detail) {
        if (!selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), Boolean.getBoolean(PROPERTY))) { return; }
        if (!(rocket.level() instanceof ServerLevel level) || !EscrowTestEndpoints.enabled(level.getServer())) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LAUNCH_TRACE cut={} request={} check={} detail={}",
                System.getProperty("advancedrocketrycommunity.escrowCase", ""), request, check, detail);
    }
}
