package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
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
        // W132-R1: each container reserves a fixed multiple of its payload and of 27 buffer comparisons, not the whole tick.
        long bufferBytes = buffer(stack(Items.STONE, 8)).slots().stream().mapToLong(RocketNbtSize::uncompressedBytes).sum();
        long chest = cursor.containerBytes(at(before, 0)), barrel = cursor.containerBytes(at(before, 1));
        assertEquals(16L * (at(before, 0).blockEntityPayload().orElseThrow().uncompressedBytes() + 27 * bufferBytes + 256), chest);
        assertEquals(16L * (at(before, 1).blockEntityPayload().orElseThrow().uncompressedBytes() + 27 * bufferBytes + 256), barrel);
        assertTrue(budget.charge(1, RocketCargoWorkBudget.MAX_BYTES, 0, false));
        assertNull(cursor.advance(1, budget)); assertTrue(offers.isEmpty());
        // Room for the chest alone at tick 2, so the barrel waits for tick 3.
        assertTrue(budget.charge(2, RocketCargoWorkBudget.MAX_BYTES - chest, 0, false));
        assertNull(cursor.advance(2, budget)); assertTrue(offers.isEmpty());
        assertNull(cursor.advance(2, budget)); assertTrue(offers.isEmpty());
        assertEquals(RocketCargoWorkBudget.MAX_BYTES, budget.chargedBytes());
        var chosen = cursor.advance(3, budget);
        assertEquals(1, chosen.delta().cursor().x()); assertEquals(7, chosen.delta().to());
        assertEquals(4, count(chosen.delta().stack())); assertEquals(1, offers.size());
        assertEquals(barrel, budget.chargedBytes());
        assertEquals(1, budget.chargedVisits()); assertEquals(0, budget.reconstructions());
    }
    private static io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock at(
            io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot snapshot, int x) {
        return snapshot.blocks().stream().filter(b -> b.position().x() == x && b.position().y() == 0 && b.position().z() == 0)
                .findFirst().orElseThrow();
    }

    @Test void aRejectedCandidateResumesWithinTheSameContainerWithoutASecondCheckThisTick() {
        var before = snapshot(CHEST, entry(0, stack(Items.STONE, 3)), entry(4, stack(Items.DIRT, 5)));
        var rejected = new HashSet<EscrowItemCargoPayload>();
        var cursor = new RocketItemCargoSelection.Cursor(before, buffer(), false,
                delta -> rejected.contains(delta) ? RocketCargoSelection.Status.CARGO_TOO_LARGE : RocketCargoSelection.Status.ELIGIBLE);
        var budget = new RocketCargoWorkBudget();
        // W132-R1: room for one visit of this container at tick 1; the retry must reserve it again.
        long container = cursor.containerBytes(at(before, 0));
        assertTrue(budget.charge(1, RocketCargoWorkBudget.MAX_BYTES - container, 0, false));
        var first = cursor.advance(1, budget); assertEquals(0, first.delta().from());
        rejected.add(first.delta()); cursor.retry();
        assertNull(cursor.advance(1, budget));
        assertEquals(4, cursor.advance(2, budget).delta().from());
        assertEquals(container, budget.chargedBytes());
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
    /** W132-R2: admission's up-front DEVICE_FULL agrees exactly with the walk's verdict, from the buffer alone. */
    @Test void fullBufferIsFourSlotsEachAtItsStackLimitAndMatchesTheWalk() {
        var rocket = snapshot(CHEST, entry(0, stack(Items.DIRT, 2)));
        var cases = new java.util.LinkedHashMap<io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer, Boolean>();
        cases.put(buffer(stack(Items.STONE, 64), stack(Items.STONE, 64), stack(Items.COBBLESTONE, 64), stack(Items.STONE, 64)), true);
        cases.put(buffer(stack(Items.ENDER_PEARL, 16), stack(Items.EGG, 16), stack(Items.ENDER_PEARL, 16), stack(Items.DIAMOND_SWORD, 1)), true);
        cases.put(buffer(stack(Items.STONE, 64), stack(Items.STONE, 64), stack(Items.STONE, 64), stack(Items.STONE, 63)), false);
        cases.put(buffer(stack(Items.ENDER_PEARL, 15), stack(Items.ENDER_PEARL, 16), stack(Items.ENDER_PEARL, 16), stack(Items.ENDER_PEARL, 16)), false);
        cases.put(buffer(stack(Items.STONE, 64), stack(Items.STONE, 64), stack(Items.STONE, 64)), false);
        cases.put(buffer(), false);
        cases.forEach((buffer, full) -> {
            assertEquals(full, RocketItemCargoSelection.full(buffer.slots()), buffer.toString());
            var cursor = new RocketItemCargoSelection.Cursor(rocket, buffer, false, delta -> RocketCargoSelection.Status.ELIGIBLE);
            assertNotNull(cursor.advance(0, null));
            assertEquals(full, cursor.noMove() == RocketItemCargoSelection.NoMove.DEVICE_FULL, buffer.toString());
        });
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
