package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * W131 A1 for C-I's governed cases over a real cargo result (W11CD section 2.5, I1): the cargo-changed rocket is
 * recognized as its governed record's authority, yet a record whose ARMED authority is missing or terminal is only
 * routed and held, never permitted legacy recovery, an action or legacy readiness.
 */
public final class RocketCargoGovernedRecognitionGameTestSupport {
    private RocketCargoGovernedRecognitionGameTestSupport() { }

    public static void verify(GameTestHelper helper, RocketEntity rocket, RocketTransferRecord record, EscrowGovernedDispatch.Decision route) {
        var server = helper.getLevel().getServer(); EscrowTestEndpoints.requireEnabled(server);
        helper.assertTrue(record.nativeTransfer().isPresent() && RocketTransferEntities.isCommittedAuthority(rocket, record)
                && RocketTransferEntities.loadedMatches(server, record, true).equals(List.of(rocket)),
                "The real cargo-changed rocket is not its governed record's recognized authority");
        var journal = new RocketTransferSavedData();
        try {
            journal.put(record);
            var fences = new RocketTransferFences(); var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
            // A held route is asserted by the caller; fencing it here would log the production offline-repair WARN (as W99).
            if (route.route() != EscrowGovernedDispatch.Route.HOLD) { fences.classifyGoverned(record, route, live, settled); }
            helper.assertTrue(!fences.permit(server, journal, record, live, settled), "A governed cargo record was permitted legacy recovery");
            for (var action : RocketTransferRecoveryAction.values()) {
                helper.assertTrue(!fences.actionAllowed(server, journal, record, action, List.of(), List.of(rocket), live, settled),
                        "A governed cargo record allowed " + action);
            }
            helper.assertTrue(RocketTransferRecoveryService.step(List.of(record), live, settled, fences.ids(), 0, candidate -> {
                throw new IllegalStateException("A governed cargo record entered legacy readiness");
            }).candidate() == null, "A governed cargo record was offered to legacy recovery");
        } finally { journal.abort(); }
    }
}
