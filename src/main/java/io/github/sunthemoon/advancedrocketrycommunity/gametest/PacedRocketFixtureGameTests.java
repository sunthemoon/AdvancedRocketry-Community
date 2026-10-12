package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Continuation and deadline checks for the shared flight fixture's paced waits. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PacedRocketFixtureGameTests {
    private PacedRocketFixtureGameTests() {
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void readyWaitRunsItsAssertionsExactlyOnce(GameTestHelper helper) {
        var calls = new AtomicInteger();
        long start = helper.getTick();
        RocketFlightGameTestFixtures.awaitPaced(helper, start + 10, () -> true, calls::incrementAndGet);
        helper.assertTrue(calls.get() == 1 && helper.getTick() == start, "Ready wait deferred its assertions");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(calls.get() == 1, "Ready wait scheduled its assertions again");
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void pacedWaitStopsAtReadinessBeforeItsDeadline(GameTestHelper helper) {
        var calls = new AtomicInteger();
        long start = helper.getTick();
        RocketFlightGameTestFixtures.awaitPaced(helper, start + 10, () -> helper.getTick() >= start + 3, () -> {
            calls.incrementAndGet();
            helper.assertTrue(helper.getTick() == start + 3, "Paced wait missed readiness");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(calls.get() == 1, "Readiness continued more than once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void expiredWaitStillRunsItsOriginalAssertions(GameTestHelper helper) {
        var calls = new AtomicInteger();
        long deadline = helper.getTick() + 3;
        RocketFlightGameTestFixtures.awaitPaced(helper, deadline, () -> false, () -> {
            calls.incrementAndGet();
            helper.assertTrue(helper.getTick() == deadline, "Unreadiness exceeded the original deadline");
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(calls.get() == 1, "Expired wait repeated its assertions");
                helper.succeed();
            });
        });
    }
}
