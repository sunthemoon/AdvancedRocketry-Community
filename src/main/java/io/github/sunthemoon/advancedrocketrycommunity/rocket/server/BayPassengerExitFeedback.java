package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status;
import net.minecraft.network.chat.Component;

/** One completed detach produces one outcome, never a receipt reason or a claim of life-support safety. */
final class BayPassengerExitFeedback {
    private BayPassengerExitFeedback() { }
    /** Capture-time refusals (W230 section 4.3): a refused live slot, a refused permit and a nested capture. */
    static final String EXIT_CAPACITY = "exit_capacity", AUTHORITY_UNAVAILABLE = "authority_unavailable",
            REENTRANT_REFUSED = "reentrant_refused";
    /** W242-R6: only NO_SAFE_EXIT, with an admitted history row and the mapped landing observers, grants one descent. */
    static boolean grants(Status status, boolean admitted, boolean landing) {
        return status == Status.NO_SAFE_EXIT && admitted && landing;
    }
    /** W242-R6: every status other than SAFE and NO_SAFE_EXIT has its own key; none says "No safe position found". */
    static String outcome(Status status, boolean authority, boolean admitted, boolean offered) {
        return switch (status) {
            case SAFE -> "safe";
            case NO_SAFE_EXIT -> offered ? "fall_offer" : !authority ? AUTHORITY_UNAVAILABLE : !admitted ? "capacity_or_used" : "no_safe_exit";
            case EXIT_BUDGET_EXHAUSTED -> "budget_exhausted";
            case REENTRANT_REFUSED -> "reentrant_refused";
            case CAPACITY_REFUSED -> "capacity_refused";
            case STALE -> "stale";
            case PENDING -> "pending";
        };
    }
    static void completed(BayPassengerExitState.Slot slot, String outcome) {
        var permit = slot.permit;
        permit.player.sendSystemMessage(Component.translatable("message.advancedrocketrycommunity.bay_exit." + outcome));
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_BAY_EXIT logical={} transfer={} player={} seat={} outcome={} code={} candidate={} attempt={} capture={}",
                permit.logical, permit.transfer == null ? null : permit.transfer.transferId(), permit.player.getUUID(),
                permit.seat, outcome, permit.transfer == null ? "EXIT_AUTHORITY_UNAVAILABLE" : slot.result.status(),
                slot.result.ordinal(), slot.result.attemptSequence(), slot.result.captureSequence());
    }
    /** A capture refused before any slot exists; sent only once the detach is observed, never for a cancelled one. */
    static void refused(net.minecraft.server.level.ServerPlayer player,
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket, String outcome) {
        if (!BayPassengerExitPermit.online(player)) { return; }
        player.sendSystemMessage(Component.translatable("message.advancedrocketrycommunity.bay_exit." + outcome));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_BAY_EXIT_REFUSED logical={} player={} outcome={}",
                rocket.assemblyTransactionId().orElse(null), player.getUUID(), outcome);
    }
}
