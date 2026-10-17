package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketBenchmarkReadinessTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void eightActiveVisitsCannotReachF3WithOnlyTheBenchmarkOriginChunksTicking() {
        var records = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> GovernedTransferFixture.create(i + 1, 1, false).record()).toList();
        var active = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.ACTIVE, null);
        var visits = new AtomicInteger(); var tickets = new AtomicInteger(); var executions = new AtomicInteger();
        for (int tick = 0; tick < 4000; tick++) {
            RocketGovernedSourceDispatcher.visit(records, 0, record -> {
                int index = visits.getAndIncrement() % 8;
                int x = 64 + index * 32, z = 64;
                // C52 native-01: all four columns loaded, but only the origin is entity-ticking.
                boolean ready = RocketTransferEntities.columnsAlreadyLoaded((x - 1) >> 4, (x + 2) >> 4,
                        (z - 1) >> 4, (z + 2) >> 4, (cx, cz) -> cx == x >> 4 && cz == z >> 4);
                assertFalse(ready);
                assertFalse(RocketGovernedSourceDispatcher.afterClassification(active, () -> false,
                        tickets::incrementAndGet, () -> ready, executions::incrementAndGet));
            });
        }
        assertEquals(32000, visits.get()); assertEquals(32000, tickets.get()); assertEquals(0, executions.get());
        RocketGovernedSourceDispatcher.visit(records, 0, record -> assertTrue(
                RocketGovernedSourceDispatcher.afterClassification(active, () -> false, tickets::incrementAndGet,
                        () -> RocketTransferEntities.columnsAlreadyLoaded(3, 4, 3, 4, (x, z) -> true), executions::incrementAndGet)));
        assertEquals(8, executions.get(), "Loading the required margin restores dispatch without changing any authority");
    }
}
