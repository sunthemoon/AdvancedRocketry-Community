package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.ArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketItemSelectionBudgetTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void sharedTickBudgetDefersBeforeAnyContainerCheckAndResumesCanonicalOrder() {
        var before = snapshot(block(0, state(CHEST), container(null, null)),
                block(1, state(BARREL), container(null, null, entry(7, stack(Items.STONE, 60)))));
        var offers = new ArrayList<EscrowItemCargoPayload>();
        var cursor = new RocketItemCargoSelection.Cursor(before, buffer(stack(Items.STONE, 8)), true,
                delta -> { offers.add(delta); return RocketCargoSelection.Status.ELIGIBLE; });
        var budget = new RocketCargoWorkBudget();
        assertTrue(budget.charge(1, RocketCargoWorkBudget.MAX_BYTES, 0, false));
        assertNull(cursor.advance(1, budget)); assertTrue(offers.isEmpty());
        assertNull(cursor.advance(2, budget)); assertTrue(offers.isEmpty());
        assertNull(cursor.advance(2, budget)); assertTrue(offers.isEmpty());
        var chosen = cursor.advance(3, budget);
        assertEquals(1, chosen.delta().cursor().x()); assertEquals(7, chosen.delta().to());
        assertEquals(4, count(chosen.delta().stack())); assertEquals(1, offers.size());
        assertEquals(RocketCargoWorkBudget.MAX_BYTES, budget.chargedBytes());
        assertEquals(1, budget.chargedVisits()); assertEquals(0, budget.reconstructions());
    }

    @Test void aRejectedCandidateResumesWithinTheSameContainerWithoutASecondCheckThisTick() {
        var before = snapshot(CHEST, entry(0, stack(Items.STONE, 3)), entry(4, stack(Items.DIRT, 5)));
        var rejected = new HashSet<EscrowItemCargoPayload>();
        var cursor = new RocketItemCargoSelection.Cursor(before, buffer(), false,
                delta -> rejected.contains(delta) ? RocketCargoSelection.Status.CARGO_TOO_LARGE : RocketCargoSelection.Status.ELIGIBLE);
        var budget = new RocketCargoWorkBudget();
        var first = cursor.advance(1, budget); assertEquals(0, first.delta().from());
        rejected.add(first.delta()); cursor.retry();
        assertNull(cursor.advance(1, budget));
        assertEquals(4, cursor.advance(2, budget).delta().from());
    }

    @Test void noMoveDiagnosticsDistinguishEmptyFullAndIncompatibleStorage() {
        var all = new ArrayList<CompoundTag>();
        for (int i = 0; i < 27; i++) { all.add(entry(i, stack(Items.STONE, 64))); }
        assertNoMove(snapshot(CHEST), buffer(), true, RocketItemCargoSelection.NoMove.DEVICE_EMPTY);
        assertNoMove(snapshot(CHEST), buffer(), false, RocketItemCargoSelection.NoMove.NO_CARGO);
        assertNoMove(snapshot(CHEST, all.toArray(CompoundTag[]::new)), buffer(stack(Items.STONE, 2)), true,
                RocketItemCargoSelection.NoMove.CARGO_FULL);
        assertNoMove(snapshot(CHEST, entry(0, stack(Items.DIRT, 2))),
                buffer(stack(Items.STONE, 64), stack(Items.STONE, 64), stack(Items.STONE, 64), stack(Items.STONE, 64)), false,
                RocketItemCargoSelection.NoMove.DEVICE_FULL);
        assertNoMove(snapshot(CHEST, entry(0, stack(Items.DIRT, 2))),
                buffer(stack(Items.STONE, 1), stack(Items.STONE, 1), stack(Items.STONE, 1), stack(Items.STONE, 1)), false,
                RocketItemCargoSelection.NoMove.INCOMPATIBLE_CARGO);
    }
    private static void assertNoMove(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot before,
            io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer buffer, boolean load,
            RocketItemCargoSelection.NoMove expected) {
        var cursor = new RocketItemCargoSelection.Cursor(before, buffer, load, delta -> RocketCargoSelection.Status.ELIGIBLE);
        var budget = new RocketCargoWorkBudget(); RocketItemCargoSelection.Result result = null;
        for (int tick = 0; result == null && tick < 10; tick++) { result = cursor.advance(tick, budget); }
        assertNotNull(result); assertNull(result.delta()); assertEquals(expected, cursor.noMove());
    }
}
