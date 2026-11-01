package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.network.chat.Component;

/** One completed detach produces one outcome, never a receipt reason or a claim of life-support safety. */
final class BayPassengerExitFeedback {
    private BayPassengerExitFeedback() { }
    static void completed(BayPassengerExitState.Slot slot, String outcome) {
        var permit = slot.permit;
        permit.player.sendSystemMessage(Component.translatable("message.advancedrocketrycommunity.bay_exit." + outcome));
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_BAY_EXIT logical={} transfer={} player={} seat={} outcome={} code={} candidate={} attempt={} capture={}",
                permit.logical, permit.transfer == null ? null : permit.transfer.transferId(), permit.player.getUUID(),
                permit.seat, outcome, permit.transfer == null ? "EXIT_AUTHORITY_UNAVAILABLE" : slot.result.status(),
                slot.result.ordinal(), slot.result.attemptSequence(), slot.result.captureSequence());
    }
}
