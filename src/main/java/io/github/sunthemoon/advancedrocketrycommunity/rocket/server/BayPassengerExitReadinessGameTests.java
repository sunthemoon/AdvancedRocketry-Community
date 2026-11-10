package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** A delayed setup condition cannot spend the arbitration test's unchanged 400-tick budget. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayPassengerExitReadinessGameTests {
    private BayPassengerExitReadinessGameTests() { }

    @GameTest(template = "rocket_test", batch = "bay_exit_late_readiness", timeoutTicks = 400)
    public static void lateReadinessLeavesTheImmediateAndDelayedExitAssertionsIntact(GameTestHelper helper) {
        int[] checks = {0};
        long[] firstTick = {-1};
        new BayPassengerExitGameTestFixture(helper, BayPassengerExitGameTestFixture.Case.DELAYED_ONE_BOX, false, () -> {
            if (firstTick[0] == -1) { firstTick[0] = helper.getTick(); }
            if (++checks[0] <= 450) { return false; }
            helper.assertTrue(helper.getTick() == firstTick[0], "Readiness wait advanced measured GameTest ticks");
            if (checks[0] == 451) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_BAY_EXIT_LATE_READINESS checks={} test_tick={}", checks[0], helper.getTick());
            }
            return true;
        }).start();
    }

    @AfterBatch(batch = "bay_exit_late_readiness")
    public static void afterLateReadiness(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
}
