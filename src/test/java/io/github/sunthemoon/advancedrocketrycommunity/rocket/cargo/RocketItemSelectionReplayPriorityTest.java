package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W132-R1, modelled on the W132 review's starvation probe. EscrowRuntime steps the resource queue's item selection
 * before the reconciliation scheduler's replay ticket in each tick, and both draw on the session's one budget. The
 * selection here needs more ticks than a ticket may go without progress (40), so a replay that had to wait for it
 * would expire.
 */
class RocketItemSelectionReplayPriorityTest {
    private static final int CHESTS = 48;
    /** The chests, the fixture's tank and its seat; each pass visits every block once. */
    private static final int BLOCKS = CHESTS + 2;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aReplayTicketActiveBeforeALongSelectionProgressesEveryTickAndTheWalkFollows() {
        var snapshot = fullChests();
        try (var replay = replay()) {
            var selection = selection(snapshot);
            long perTick = RocketCargoWorkBudget.MAX_BYTES / selection.containerBytes(chest(snapshot));
            assertTrue(2L * CHESTS / perTick > 40, "The selection must outlast a ticket lifetime: " + perTick + " visits per tick");
            RocketItemCargoSelection.Result result = null; int visits = 0; boolean published = false;
            for (long tick = 0; result == null && tick < 400; tick++) {
                var step = selectionStep(replay, selection, tick); result = step.result(); int walked = step.walked(); visits += walked;
                if (published) { continue; }
                assertNull(result, "Selection ran ahead of the active replay ticket at tick " + tick);
                assertEquals(0, walked, "Selection reserved work while the ticket was active");
                assertTrue(replay.ticket.valid(), "The replay ticket expired at tick " + tick);
                long before = replay.cursor().chargedBytes();
                boolean ready = replay.ticket.advance();
                assertTrue(ready || replay.cursor().chargedBytes() != before, "The replay ticket was starved at tick " + tick);
                if (ready) {
                    assertTrue(replay.cursor().failure().isEmpty());
                    replay.ticket.close(); published = true; // Its publication consumes the ticket.
                }
            }
            assertTrue(published);
            assertSameAsUndisturbed(snapshot, selection, result);
            assertEquals(2 * BLOCKS, visits, "The deferred walk visited each block once per pass");
        }
    }

    @Test void aReplayBeginningDuringASelectionWaitsAtMostThatTickAndTheWalkResumesInPlace() {
        var snapshot = fullChests();
        try (var replay = replay()) {
            replay.ticket.close(); // The session starts idle; the replay begins at tick 9, after that tick's selection step.
            var selection = selection(snapshot);
            RocketItemCargoSelection.Result result = null; int visits = 0, starved = 0; long readyAt = -1;
            RocketCargoPreparations.Ticket ticket = null;
            for (long tick = 0; result == null && tick < 400; tick++) {
                var step = selectionStep(replay, selection, tick); result = step.result(); int walked = step.walked(); visits += walked;
                if (tick == 9) { ticket = replay.reopen(tick); }
                if (ticket == null || readyAt >= 0) { continue; }
                if (tick > 9) {
                    assertNull(result, "Selection ran ahead of the active replay ticket at tick " + tick);
                    assertEquals(0, walked, "Selection reserved work while the ticket was active");
                }
                assertTrue(ticket.valid(), "The replay ticket expired at tick " + tick);
                long before = replay.cursor().chargedBytes();
                boolean ready = ticket.advance();
                if (!ready && replay.cursor().chargedBytes() == before) { starved++; }
                if (ready) { assertTrue(replay.cursor().failure().isEmpty()); ticket.close(); readyAt = tick; }
            }
            assertTrue(readyAt > 9 && readyAt < 9 + 40, "Replay READY at " + readyAt);
            assertTrue(starved <= 1, "Only the tick in which the replay began may find the budget spent: " + starved);
            assertSameAsUndisturbed(snapshot, selection, result);
            assertEquals(2 * BLOCKS, visits, "The walk resumed in place rather than revisiting or skipping blocks");
        }
    }

    /**
     * The queue's step for this tick, as the item admission takes it: through the session, which defers to an active
     * ticket, with the block visits it reserved this tick; a zero charge first opens the tick's accounting.
     */
    private static Step selectionStep(CargoTicketTestFixture replay, RocketItemCargoSelection.Cursor selection, long tick) {
        replay.at(tick);
        assertTrue(replay.budget().charge(tick, 0, 0, false));
        var result = replay.session().select(selection, tick);
        return new Step(result, replay.budget().chargedVisits());
    }
    private record Step(RocketItemCargoSelection.Result result, int walked) { }
    /** A small unload replay on another rocket: four of ten stones from its chest into the device. */
    private static CargoTicketTestFixture replay() {
        var before = ItemCargoTestFixtures.snapshot(CHEST, entry(0, stack(Items.STONE, 10)));
        return new CargoTicketTestFixture(UUID.randomUUID(), before, delta(before, 0, stack(Items.STONE, 4), 0, 0), false);
    }
    /** A stone load into chests full of dirt: two passes, no move, CARGO_FULL. A heavy buffer makes each visit costly. */
    private static RocketItemCargoSelection.Cursor selection(RocketStructureSnapshot snapshot) {
        return new RocketItemCargoSelection.Cursor(snapshot, heavyBuffer(), true, delta -> RocketCargoSelection.Status.ELIGIBLE);
    }
    private static RocketStructureSnapshot fullChests() {
        var full = new CompoundTag[27];
        for (int slot = 0; slot < 27; slot++) { full[slot] = entry(slot, stack(Items.DIRT, 64)); }
        var blocks = new RocketBlock[CHESTS];
        // At z = 2, clear of the fixture's tank at (9, 0, 0) and seat at (0, 1, 0).
        for (int x = 0; x < CHESTS; x++) { blocks[x] = new RocketBlock(new RocketPosition(x, 0, 2), state(CHEST), container(null, null, full)); }
        return ItemCargoTestFixtures.snapshot(blocks);
    }
    private static EscrowItemBuffer heavyBuffer() {
        var slots = new CompoundTag[EscrowItemBuffer.SLOTS];
        for (int slot = 0; slot < slots.length; slot++) {
            slots[slot] = stack(Items.STONE, 8); var tag = new CompoundTag(); tag.putString("pad", "x".repeat(3_900)); slots[slot].put("tag", tag);
        }
        return buffer(slots);
    }
    private static RocketBlock chest(RocketStructureSnapshot value) {
        return value.blocks().stream().filter(b -> b.position().equals(new RocketPosition(0, 0, 2))).findFirst().orElseThrow();
    }
    private static void assertSameAsUndisturbed(RocketStructureSnapshot snapshot, RocketItemCargoSelection.Cursor selection,
                                                RocketItemCargoSelection.Result result) {
        var undisturbed = selection(snapshot);
        assertNotNull(result);
        assertEquals(undisturbed.advance(0, null), result);
        assertEquals(RocketItemCargoSelection.NoMove.CARGO_FULL, undisturbed.noMove());
        assertEquals(undisturbed.noMove(), selection.noMove());
    }
}
