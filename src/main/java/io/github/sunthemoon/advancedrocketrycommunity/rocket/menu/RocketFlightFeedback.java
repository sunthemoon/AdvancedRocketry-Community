package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Player feedback is action-specific without changing the service result or its audit code. */
public final class RocketFlightFeedback {
    public static final String BOARDED = "message.advancedrocketrycommunity.rocket.boarded";
    public static final String LEFT = "message.advancedrocketrycommunity.rocket.left_seat";
    /** "Landing pad refused: %s", with the pad's translated reason; the chat line uses the same text. */
    public static final String PAD_REFUSED = "message.advancedrocketrycommunity.pad.landing_refused";
    private RocketFlightFeedback() { }

    public static Component reply(RocketFlightAction action, RocketFlightRequestResult result) {
        if (result.code() == RocketFlightRequestCode.SUCCESS) {
            if (action == RocketFlightAction.BOARD) { return Component.translatable(BOARDED); }
            if (action == RocketFlightAction.LEAVE) { return Component.translatable(LEFT); }
        } else if (result.detailKey() != null) {
            // W311 (finding 7-5): the pad's own reason, not the result code's text, which speaks of fixed pads.
            return Component.translatable(PAD_REFUSED, Component.translatable(result.detailKey()));
        }
        return Component.translatable(result.code().translationKey(), result.requiredFuel());
    }

    /**
     * W311: whether an action-bar message refuses a flight request, which the console keeps until its next action or
     * plan change. Success and the journal's pending progress are not refusals.
     */
    public static boolean refusal(Component message) {
        if (message == null || !(message.getContents() instanceof TranslatableContents text)) { return false; }
        if (PAD_REFUSED.equals(text.getKey())) { return true; }
        for (RocketFlightRequestCode code : RocketFlightRequestCode.values()) {
            if (code != RocketFlightRequestCode.SUCCESS && code != RocketFlightRequestCode.JOURNAL_PENDING
                    && code.translationKey().equals(text.getKey())) {
                return true;
            }
        }
        return false;
    }
}
