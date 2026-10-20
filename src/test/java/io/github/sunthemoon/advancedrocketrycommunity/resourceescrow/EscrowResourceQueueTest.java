package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowResourceQueueTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void maximumRocketRetainsTheReconstructionSlotAmongSmallRequestsAndFuel() {
        var blocks = new ArrayList<>(CargoTestFixtures.snapshot(FluidStack.EMPTY).blocks());
        var stone = new RocketBlockState(ResourceLocation.tryParse("minecraft:stone"), Map.of());
        for (int i = 2; i < 2048; i++) { blocks.add(new RocketBlock(new RocketPosition(i % 16, 2 + i / 256, i / 16 % 16), stone)); }
        var before = CargoTestFixtures.snapshot(blocks); var tick = new long[] {1};
        try (var session = new CargoTicketTestFixture(UUID.randomUUID(), before,
                CargoTestFixtures.delta(before, 0, new FluidStack(Fluids.WATER, 1000)), true)) {
            var queue = new EscrowResourceQueue(); var order = new ArrayList<String>();
            var fuel = new AtomicInteger(); var small = new AtomicInteger();
            queue.offerCargo(new UUID(0, 1), () -> {
                boolean ready = session.advance(tick[0]);
                assertTrue(session.budget().chargedBytes() <= RocketCargoWorkBudget.MAX_BYTES);
                assertTrue(session.budget().chargedVisits() <= RocketCargoWorkBudget.MAX_VISITS);
                assertEquals(1, session.budget().reconstructions());
                if (ready) { order.add("large"); } return ready;
            }, () -> { });
            for (int i = 0; i < 100; i++) {
                queue.offerCargo(new UUID(1, i), () -> { small.incrementAndGet(); order.add("small"); return true; }, () -> { });
            }
            queue.offer(new UUID(2, 0), () -> { fuel.incrementAndGet(); return true; });
            while (queue.size() > 0) {
                int oldSmall = small.get(); int oldFuel = fuel.get();
                assertTrue(queue.visit() <= EscrowResourceQueue.VISITS_PER_TICK);
                assertTrue(small.get() - oldSmall <= 1); assertTrue(fuel.get() - oldFuel <= 1);
                assertTrue(EscrowResourceQueue.VISITS_PER_TICK <= EscrowResourceQueue.COMMITS_PER_TICK);
                assertEquals(64, EscrowResourceQueue.COMMITS_PER_TICK);
                tick[0]++; assertTrue(tick[0] < 200);
            }
            assertEquals("large", order.get(0)); assertEquals(100, small.get()); assertEquals(1, fuel.get());
            assertEquals(1000, CargoTestFixtures.tank(session.cursor().after(), 0).getAmount());
        }
    }
    @Test void activeWorkCountsAgainstCapacityAndUnloadOrLifecycleCancellationRunsExactlyOnce() {
        var queue = new EscrowResourceQueue(); var cancelled = new AtomicInteger(); var id = UUID.randomUUID();
        assertTrue(queue.offerCargo(id, () -> false, cancelled::incrementAndGet)); queue.visit();
        for (int i = 1; i < EscrowResourceQueue.CAPACITY; i++) { assertTrue(queue.offer(new UUID(0, i), () -> false)); }
        assertEquals(256, queue.size()); assertFalse(queue.offer(UUID.randomUUID(), () -> false));
        assertTrue(queue.offerCargo(id, () -> { fail(); return false; }, () -> fail()));
        queue.cancel(id); queue.cancel(id); assertEquals(1, cancelled.get());
        queue.offerCargo(id, () -> false, cancelled::incrementAndGet); queue.clear();
        assertEquals(2, cancelled.get()); assertEquals(0, queue.size()); assertFalse(queue.preparing());
    }
    @Test void fuelNeverClaimsTheReconstructionBudget() {
        var budget = new RocketCargoWorkBudget(); var queue = new EscrowResourceQueue();
        queue.offer(UUID.randomUUID(), () -> true); queue.visit();
        assertEquals(0, budget.reconstructions()); assertEquals(0, budget.chargedBytes());
        assertEquals(0, budget.chargedVisits());
    }
}
