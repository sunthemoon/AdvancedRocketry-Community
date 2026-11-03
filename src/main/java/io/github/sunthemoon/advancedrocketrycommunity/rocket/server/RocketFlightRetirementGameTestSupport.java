package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;

/** Test-only access to ordinary retirement timing and a real vanilla save at its first capture. */
public final class RocketFlightRetirementGameTestSupport {
    /**
     * One destination demand allows three ticket/proof opportunities. Budget each at RETRY_TICKS (200) plus
     * ATTEMPT_TICKS (100), then 100 ticks for admission and the journal removal/preparation acknowledgements:
     * 3 * (200 + 100) + 100 = 1,000 paced ticks. MAX_ATTEMPTS is concurrent capacity, not a per-demand retry count.
     */
    public static final int PREPARATION_TICKS = RocketFillRetirementPacing.DEMAND_TICKETS
            * (RocketOrdinaryRetirement.RETRY_TICKS + RocketOrdinaryRetirement.ATTEMPT_TICKS) + 100;

    private RocketFlightRetirementGameTestSupport() { }

    /** No clock, proof or queue mutation: supersede exactly one capture through the Level's vanilla entity store. */
    public static final class Supersession {
        private final GameTestHelper helper;
        private UUID logical;
        private boolean injected, refused, retried;

        public Supersession(GameTestHelper helper) { this.helper = helper; }

        public void arm(UUID logical) { this.logical = logical; }

        public void tick() {
            if (logical == null || retried) { return; }
            var retirements = installed().transfers().ordinaryRetirements();
            Map<UUID, Object> pending = RocketSourceRetirementFixtures.field(retirements, "pending");
            var entry = pending.get(logical);
            if (entry == null) { return; }
            helper.assertTrue(RocketSourceRetirementFixtures.field(entry, "site") == RocketOrdinaryRetirement.Site.BEFORE_REPLACE,
                    "The chained launch did not use destination replacement retirement");
            int attempts = RocketSourceRetirementFixtures.field(entry, "attempts");
            if (!injected && RocketSourceRetirementFixtures.field(entry, "state").toString().equals("PROVING")
                    && RocketSourceRetirementFixtures.<Integer>field(entry, "cursor") > 0) {
                helper.assertTrue(attempts == 1, "The forced save missed the first retirement capture");
                var own = RocketSourceRetirementFixtures.<List<RocketRetirementProof.Slot>>field(entry, "census").get(0);
                var level = RocketTransferEntities.level(helper.getLevel().getServer(), own.dimension());
                helper.assertTrue(level.entityManager.storeChunkSections(own.chunk().toLong(), entity -> { }),
                        "The superseding vanilla entity save was refused");
                injected = true;
                AdvancedRocketryCommunity.LOGGER.info("ARCE_CHAINED_FLIGHT_SUPERSEDED logical={} transfer={} tick={}",
                        logical, RocketSourceRetirementFixtures.<RocketTransferRecord>field(entry, "record").transferId(),
                        helper.getTick());
            }
            refused |= injected && "SUPERSEDING_SAVE".equals(retirements.reason(logical));
            retried |= refused && attempts >= 2;
        }

        public void assertRetried() {
            helper.assertTrue(injected && refused && retried,
                    "The chained flight did not observe a superseded retirement proof and its fresh retry");
        }

        private static RocketManager installed() {
            try {
                var field = RocketRuntime.class.getDeclaredField("service");
                field.setAccessible(true);
                return (RocketManager) field.get(null);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot inspect the installed flight fixture", failure);
            }
        }
    }
}
