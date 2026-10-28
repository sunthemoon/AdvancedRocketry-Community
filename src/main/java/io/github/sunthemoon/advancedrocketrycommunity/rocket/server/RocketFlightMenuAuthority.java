package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * W179: the flight menu's cancel intent for the menu-authority matrix, through the live flight service exactly as
 * the {@code rocket_flight} handler passes it on. The handler discards the service's result, so this reads it the
 * way {@code RocketReleaseBoardingGameTests} does: from the installed service's {@code flights}.
 */
public final class RocketFlightMenuAuthority {
    private RocketFlightMenuAuthority() {
    }

    /** Accepted once the intent passed the rate limiter and the owner check; its flight result does not matter. */
    public static MenuAuthorityMatrix.Outcome cancel(ServerPlayer player, RocketEntity rocket) {
        RocketFlightService flights;
        try {
            var service = RocketRuntime.class.getDeclaredField("service");
            service.setAccessible(true);
            flights = RocketSourceRetirementFixtures.field(service.get(null), "flights");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("The installed flight service is unavailable", failure);
        }
        RocketFlightRequestCode code = flights.request(player, rocket.getId(), RocketFlightAction.CANCEL,
                rocket.flightData().orElseThrow().currentTarget().orElseThrow(), UUID.randomUUID()).code();
        return code == RocketFlightRequestCode.RATE_LIMITED ? MenuAuthorityMatrix.Outcome.RATE_LIMITED
                : code == RocketFlightRequestCode.UNAUTHORIZED ? MenuAuthorityMatrix.Outcome.REFUSED
                : MenuAuthorityMatrix.Outcome.ACCEPTED;
    }
}
