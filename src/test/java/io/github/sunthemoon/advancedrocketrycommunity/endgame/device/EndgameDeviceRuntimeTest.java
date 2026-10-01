package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-054 sections 2.1 and 7: work caps in ID order, active admission and the structure box index. */
final class EndgameDeviceRuntimeTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void grantsFollowCanonicalStringOrderNotUuidCompareTo() {
        // UUID.compareTo compares signed halves: 8000... sorts before 0000... there, after it as a string.
        UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertTrue(high.compareTo(low) < 0);
        RoundRobinBudget budget = new RoundRobinBudget();
        budget.request(high);
        budget.request(low);
        assertEquals(List.of(low), budget.endTick(1));
    }

    @Test
    void workBeyondTheCapWaitsAndIsServedRoundRobin() {
        List<UUID> devices = ids(5);
        RoundRobinBudget budget = new RoundRobinBudget();
        List<UUID> served = new ArrayList<>();
        for (int tick = 0; tick < 5; tick++) {
            devices.forEach(budget::request);
            List<UUID> granted = budget.endTick(2);
            assertEquals(2, granted.size());
            assertEquals(3, budget.waitingLastTick());
            served.addAll(granted);
        }
        // Two per tick, continuing after the last served device: every device twice in ten grants.
        assertEquals(devices, served.subList(0, 5));
        assertEquals(devices, served.subList(5, 10));
    }

    @Test
    void aGrantIsTakenOnceAndLapsesAfterItsTick() {
        UUID device = UUID.randomUUID();
        RoundRobinBudget budget = new RoundRobinBudget();
        assertFalse(budget.take(device), "nothing is granted without a request");
        budget.request(device);
        budget.endTick(8);
        assertTrue(budget.take(device));
        assertFalse(budget.take(device), "one grant is one unit of work");
        assertEquals(1, budget.servedLastTick());
        budget.request(device);
        budget.endTick(8);
        budget.endTick(8);
        assertFalse(budget.take(device), "an untaken grant lapses at the end of its tick");
        assertTrue(budget.endTick(0).isEmpty());
    }

    @Test
    void admissionIsFirstComeFirstServedPerOwnerAndGlobally() {
        EndgameActiveDevices active = new EndgameActiveDevices();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        List<UUID> devices = ids(7);
        for (int i = 0; i < 4; i++) {
            assertTrue(active.admit(EndgameSystem.LASER_DRILL, devices.get(i), alice, 4, 6));
        }
        assertFalse(active.admit(EndgameSystem.LASER_DRILL, devices.get(4), alice, 4, 6), "per-owner limit");
        assertTrue(active.admit(EndgameSystem.LASER_DRILL, devices.get(4), bob, 4, 6));
        assertTrue(active.admit(EndgameSystem.LASER_DRILL, devices.get(5), bob, 4, 6));
        assertFalse(active.admit(EndgameSystem.LASER_DRILL, devices.get(6), bob, 4, 6), "global limit");
        assertTrue(active.admit(EndgameSystem.LASER_DRILL, devices.get(0), alice, 1, 1),
                "an active device stays active when the limits are lowered");
        assertTrue(active.admit(EndgameSystem.RAILGUN, devices.get(6), bob, 4, 6), "limits are per system");
        active.release(EndgameSystem.LASER_DRILL, devices.get(5));
        assertTrue(active.admit(EndgameSystem.LASER_DRILL, devices.get(6), bob, 4, 6), "a released place is reused");
        assertEquals(6, active.count(EndgameSystem.LASER_DRILL));
        assertEquals(4, active.ownerCount(EndgameSystem.LASER_DRILL, alice));
    }

    @Test
    void onlyChangesInsideABoxMarkItsController() {
        EndgameStructureTracker tracker = new EndgameStructureTracker();
        BlockPos controller = new BlockPos(15, 64, 15);
        tracker.track(Level.OVERWORLD, controller, new BoundingBox(14, 63, 15, 16, 65, 17));
        assertTrue(tracker.consumeDirty(Level.OVERWORLD, controller), "a new box validates first");
        assertFalse(tracker.consumeDirty(Level.OVERWORLD, controller));
        tracker.changed(Level.OVERWORLD, new BlockPos(13, 64, 16));
        tracker.changed(Level.NETHER, new BlockPos(15, 64, 16));
        assertFalse(tracker.consumeDirty(Level.OVERWORLD, controller), "outside the box or in another Level");
        tracker.changed(Level.OVERWORLD, new BlockPos(16, 65, 17));
        assertTrue(tracker.consumeDirty(Level.OVERWORLD, controller), "a box corner in the next chunk");
        tracker.untrack(Level.OVERWORLD, controller);
        tracker.changed(Level.OVERWORLD, new BlockPos(15, 64, 16));
        assertFalse(tracker.consumeDirty(Level.OVERWORLD, controller));
        assertEquals(0, tracker.tracked());
    }

    private static List<UUID> ids(int count) {
        Set<UUID> ids = new HashSet<>();
        while (ids.size() < count) {
            ids.add(UUID.randomUUID());
        }
        List<UUID> sorted = new ArrayList<>(ids);
        sorted.sort(Comparator.comparing(UUID::toString));
        return sorted;
    }
}
