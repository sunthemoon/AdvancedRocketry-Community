package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ADR-065 section 5.1 and W07 review R2: the controller's 5-block core reach, which both the request and the
 * confirmation of a {@code warp_controller} screen check. A removed, distant, unloaded or foreign core is not in
 * reach, and an unloaded position is never read.
 */
final class WarpCoreReachTest {
    private static final int X = 100;
    private static final int Y = 64;
    private static final int Z = -40;

    @Test
    void aLoadedCoreOfTheStationWithinFiveBlocksIsInReach() {
        for (int[] offset : List.of(new int[] {5, 0, 0}, new int[] {0, -5, 0}, new int[] {0, 0, 5},
                new int[] {3, 4, 0}, new int[] {1, 0, 0}, new int[] {-2, 2, -2})) {
            World world = new World().withCore(X + offset[0], Y + offset[1], Z + offset[2]);
            assertTrue(WarpCoreReach.reachable(X, Y, Z, world), () -> "offset " + List.of(offset[0], offset[1],
                    offset[2]));
        }
    }

    @Test
    void aRemovedOrTooDistantCoreIsNotInReach() {
        assertFalse(WarpCoreReach.reachable(X, Y, Z, new World()), "no core");
        for (int[] offset : List.of(new int[] {6, 0, 0}, new int[] {4, 3, 1}, new int[] {5, 1, 0},
                new int[] {3, 3, 3}, new int[] {0, 0, -6})) {
            World world = new World().withCore(X + offset[0], Y + offset[1], Z + offset[2]);
            assertFalse(WarpCoreReach.reachable(X, Y, Z, world), () -> "offset " + List.of(offset[0], offset[1],
                    offset[2]));
        }
    }

    @Test
    void anUnloadedCoreIsNotInReachAndItsBlockIsNeverRead() {
        World world = new World().withCore(X + 2, Y, Z).unload(X + 2, Y, Z);
        assertFalse(WarpCoreReach.reachable(X, Y, Z, world), "a core in an unloaded chunk");
        assertTrue(world.readUnloaded.isEmpty(), "an unloaded position was read: " + world.readUnloaded);
        world.loaded.add(List.of(X + 2, Y, Z));
        assertTrue(WarpCoreReach.reachable(X, Y, Z, world), "the same core once loaded");
    }

    @Test
    void aCoreOfAnotherStationIsNotInReach() {
        World world = new World().withCore(X + 1, Y, Z);
        world.foreignX = X + 1;
        assertFalse(WarpCoreReach.reachable(X, Y, Z, world), "a core outside this station's region");
    }

    @Test
    void theScanIsBoundedToTheReachSphere() {
        World world = new World();
        assertFalse(WarpCoreReach.reachable(X, Y, Z, world));
        assertEquals(11 * 11 * 11, WarpCoreReach.MAX_PROBES);
        assertTrue(world.probes <= WarpCoreReach.MAX_PROBES, "probes " + world.probes);
        // Exactly the integer points of the closed 5-block sphere are probed.
        int sphere = 0;
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    sphere += dx * dx + dy * dy + dz * dz <= 25 ? 1 : 0;
                }
            }
        }
        assertEquals(sphere, world.probes);
    }

    /** A map of cores, a set of unloaded positions and one optional foreign column. */
    private static final class World implements WarpCoreReach.Probe {
        final Set<List<Integer>> cores = new HashSet<>();
        final Set<List<Integer>> unloaded = new HashSet<>();
        final Set<List<Integer>> loaded = new HashSet<>();
        final Set<List<Integer>> readUnloaded = new HashSet<>();
        int foreignX = Integer.MIN_VALUE;
        int probes;

        World withCore(int x, int y, int z) {
            cores.add(List.of(x, y, z));
            return this;
        }

        World unload(int x, int y, int z) {
            unloaded.add(List.of(x, y, z));
            return this;
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            probes++;
            List<Integer> position = List.of(x, y, z);
            return !unloaded.contains(position) || loaded.contains(position);
        }

        @Override
        public boolean core(int x, int y, int z) {
            List<Integer> position = List.of(x, y, z);
            if (unloaded.contains(position) && !loaded.contains(position)) {
                readUnloaded.add(position);
            }
            return cores.contains(position);
        }

        @Override
        public boolean inStation(int x, int z) {
            return x != foreignX;
        }
    }
}
