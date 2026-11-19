package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedArrival.Result;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketGovernedArrivalDispatcherTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void unchangedSuccessfulVisitReusesEveryNonHeldResultWithoutComputingAgain() {
        for (var first : new Result[] {Result.DESCENDING, Result.READY_TO_LAND, Result.LANDED}) {
            assertEquals(first, RocketGovernedArrivalDispatcher.result(first, true, true, true, true, true,
                    () -> { fail("Unchanged successful visit recomputed arrival"); return Result.HELD; }));
        }
    }

    @Test void everyFailedIdentityObservationClockOrBindingConditionRecomputesOnce() {
        for (int changed = 0; changed < 5; changed++) {
            boolean[] same = {true, true, true, true, true}; same[changed] = false;
            var calls = new AtomicInteger();
            assertEquals(Result.HELD, RocketGovernedArrivalDispatcher.result(Result.DESCENDING,
                    same[0], same[1], same[2], same[3], same[4], () -> { calls.incrementAndGet(); return Result.HELD; }));
            assertEquals(1, calls.get(), "Failed guard " + changed + " did not compute exactly once");
        }
    }

    @Test void absentOrHeldFirstComputationNeverSuppressesTheLaterAttempt() {
        for (var first : new Result[] {null, Result.HELD}) {
            var calls = new AtomicInteger();
            assertEquals(Result.READY_TO_LAND, RocketGovernedArrivalDispatcher.result(first, true, true, true, true, true,
                    () -> { calls.incrementAndGet(); return Result.READY_TO_LAND; }));
            assertEquals(1, calls.get());
        }
    }
}
