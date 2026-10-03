package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.ExoplanetShapes.PillarPart;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * ADR-063 section 6 with revision 6 (A0): the C15c feature shapes stay within 12 blocks of their origin horizontally
 * for 2,000 seeds each, with the legacy sizes, and their leaves within six steps of a log.
 */
class ExoplanetShapesTest {
    private static final int SEEDS = 2_000;

    @Test
    void lightwoodTreesStayWithinTheBoundWithLegacyHeightsAndLivingLeaves() {
        Set<Integer> heights = new HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            TreePlan plan = ExoplanetShapes.lightwoodTree(new Random(seed));
            assertTrue(plan.reach() <= 11, "reach " + plan.reach() + " for seed " + seed);
            int height = 0;
            while (plan.logs().contains(new BlockPos(0, height, 0))) {
                height++;
            }
            assertTrue(height >= 20 && height <= 29, "trunk " + height + " for seed " + seed);
            for (int y = 0; y < 20; y++) {
                for (BlockPos corner : List.of(new BlockPos(0, y, 0), new BlockPos(1, y, 0), new BlockPos(0, y, 1),
                        new BlockPos(1, y, 1))) {
                    assertTrue(plan.logs().contains(corner), "2 x 2 trunk gap at " + corner);
                }
            }
            heights.add(height);
            assertLivingLeaves(plan, seed);
            assertFalse(plan.leaves().isEmpty());
        }
        assertTrue(heights.size() >= 10, "heights " + heights);
    }

    @Test
    void giantSwampTreesStayWithinTheBoundWithRootsAndACanopy() {
        for (long seed = 0; seed < SEEDS; seed++) {
            TreePlan plan = ExoplanetShapes.giantSwampTree(new Random(seed));
            assertTrue(plan.reach() <= ExoplanetShapes.MAX_REACH, "reach " + plan.reach() + " for seed " + seed);
            int bottom = plan.logs().stream().mapToInt(BlockPos::getY).min().orElseThrow();
            int top = plan.logs().stream().mapToInt(BlockPos::getY).max().orElseThrow();
            assertEquals(-20, bottom, "roots");
            assertTrue(top >= 39 && top <= 48, "trunk top " + top);
            assertTrue(plan.leaves().keySet().stream().anyMatch(leaf -> Math.abs(leaf.getX()) >= 10),
                    "no wide canopy for seed " + seed);
            assertLivingLeaves(plan, seed);
        }
    }

    @Test
    void invertedPillarsWidenUpwardWithTheLegacyMaterials() {
        for (long seed = 0; seed < SEEDS; seed++) {
            Map<BlockPos, PillarPart> pillar = ExoplanetShapes.invertedPillar(new Random(seed));
            assertTrue(ExoplanetShapes.reach(pillar.keySet()) <= 5, "pillar reach for seed " + seed);
            int top = pillar.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
            assertTrue(top >= 19 && top <= 32, "pillar top " + top);
            assertEquals(0, pillar.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow());
            assertEquals(PillarPart.MOSSY_COBBLESTONE, pillar.get(BlockPos.ZERO));
            assertEquals(PillarPart.GRASS, pillar.get(new BlockPos(0, top, 0)));
            assertTrue(width(pillar.keySet(), top) > width(pillar.keySet(), 0), "not wider at the top");
        }
    }

    @Test
    void crystalClustersStayWithinTheBoundRootedAndLeaningAsLegacy() {
        int[] leans = new int[3];
        Set<Integer> colours = new HashSet<>();
        for (long seed = 0; seed < SEEDS; seed++) {
            ExoplanetShapes.Crystal crystal = ExoplanetShapes.crystalCluster(new Random(seed));
            assertTrue(ExoplanetShapes.reach(crystal.cells()) <= ExoplanetShapes.MAX_REACH, "seed " + seed);
            int bottom = crystal.cells().stream().mapToInt(BlockPos::getY).min().orElseThrow();
            int top = crystal.cells().stream().mapToInt(BlockPos::getY).max().orElseThrow();
            assertEquals(-3, bottom, "rooting");
            assertTrue(top >= 9 && top <= 48, "crystal top " + top);
            assertTrue(crystal.colour() >= 0 && crystal.colour() < 6);
            colours.add(crystal.colour());
            int minX = crystal.cells().stream().filter(cell -> cell.getY() == top).mapToInt(BlockPos::getX).min()
                    .orElseThrow();
            int maxX = crystal.cells().stream().filter(cell -> cell.getY() == top).mapToInt(BlockPos::getX).max()
                    .orElseThrow();
            leans[Integer.signum(minX + maxX) + 1]++;
        }
        assertEquals(6, colours.size());
        // Legacy: one time in six to each side, otherwise upright (2,000 seeds, generous margins).
        assertTrue(leans[0] > 200 && leans[0] < 470 && leans[2] > 200 && leans[2] < 470, "leans " + leans[0]
                + "/" + leans[1] + "/" + leans[2]);
    }

    @Test
    void leafDistancesFollowSixNeighbourStepsAndDropFarLeaves() {
        Set<BlockPos> logs = Set.of(BlockPos.ZERO);
        List<BlockPos> line = new java.util.ArrayList<>();
        for (int x = 1; x <= 8; x++) {
            line.add(new BlockPos(x, 0, 0));
        }
        TreePlan plan = TreePlan.of(logs, line);
        for (int x = 1; x <= 6; x++) {
            assertEquals(x, plan.leaves().get(new BlockPos(x, 0, 0)));
        }
        assertFalse(plan.leaves().containsKey(new BlockPos(7, 0, 0)), "a leaf seven steps out would decay");
        assertFalse(TreePlan.of(logs, List.of(new BlockPos(3, 3, 3))).leaves().containsKey(new BlockPos(3, 3, 3)),
                "an unconnected leaf");
    }

    private static void assertLivingLeaves(TreePlan plan, long seed) {
        for (Map.Entry<BlockPos, Integer> leaf : plan.leaves().entrySet()) {
            int distance = leaf.getValue();
            assertTrue(distance >= 1 && distance <= TreePlan.MAX_LEAF_DISTANCE, "distance " + distance);
            assertFalse(plan.logs().contains(leaf.getKey()), "a leaf on a log for seed " + seed);
            boolean supported = false;
            for (Direction direction : Direction.values()) {
                BlockPos next = leaf.getKey().relative(direction);
                Integer other = plan.leaves().get(next);
                supported |= distance == 1 ? plan.logs().contains(next) : other != null && other == distance - 1;
            }
            assertTrue(supported, "leaf " + leaf + " has no neighbour one step nearer a log, seed " + seed);
        }
    }

    private static int width(Set<BlockPos> cells, int y) {
        return (int) cells.stream().filter(cell -> cell.getY() == y && cell.getZ() == 0).count();
    }
}
