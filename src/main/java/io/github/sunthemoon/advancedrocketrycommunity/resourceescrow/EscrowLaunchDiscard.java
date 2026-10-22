package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.UUID;

/** Stateless observations at launch discard sites; never evaluates an admission predicate. */
public final class EscrowLaunchDiscard {
    private EscrowLaunchDiscard() { }

    public static void note(UUID logical, UUID transfer, String site, String reason) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LAUNCH_DISCARD logical={} transfer={} site={} reason={}",
                logical, transfer, site, reason);
    }
}
