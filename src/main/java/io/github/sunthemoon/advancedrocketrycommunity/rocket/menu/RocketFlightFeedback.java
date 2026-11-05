package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import net.minecraft.network.chat.Component;

/** Player feedback is action-specific without changing the service result or its audit code. */
public final class RocketFlightFeedback {
    public static final String BOARDED = "message.advancedrocketrycommunity.rocket.boarded";
    public static final String LEFT = "message.advancedrocketrycommunity.rocket.left_seat";
    private RocketFlightFeedback() { }

    public static Component reply(RocketFlightAction action, RocketFlightRequestResult result) {
        if (result.code() == RocketFlightRequestCode.SUCCESS) {
            if (action == RocketFlightAction.BOARD) { return Component.translatable(BOARDED); }
            if (action == RocketFlightAction.LEAVE) { return Component.translatable(LEFT); }
        }
        return Component.translatable(result.code().translationKey(), result.requiredFuel());
    }
}
