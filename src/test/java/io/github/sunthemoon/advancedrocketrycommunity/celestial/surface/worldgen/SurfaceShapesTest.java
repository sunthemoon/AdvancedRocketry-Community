package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-063 section 5 (A1 bounds): crater, volcano and geode shapes stay inside their stated limits. */
class SurfaceShapesTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void cratersStayWithinTheirRadiusRimAndReach() {
        Random random = new Random(1801);
        int large = 0;
        for (int i = 0; i < 2_000; i++) {
            CraterShape shape = CraterShape.random(random, CraterShape.MIN_RADIUS, CraterShape.MAX_RADIUS);
            assertTrue(shape.radius() >= 8 && shape.radius() <= 48, "radius " + shape.radius());
            assertTrue(shape.depth() <= (shape.radius() > 32 ? 15 : 11), "depth " + shape.depth());
            assertTrue(shape.rimHeight() >= 1 && shape.rimHeight() <= 8);
            large += shape.radius() > 32 ? 1 : 0;
            int reach = shape.reach();
            // The structure start's references reach 8 chunks; a crater never needs more than 5.
            assertTrue(reach <= 5 * 16, "reach " + reach);
            for (int dx = -reach - 2; dx <= reach + 2; dx += 3) {
                for (int dz = -reach - 2; dz <= reach + 2; dz += 3) {
                    int depth = shape.bowlDepth(dx, dz);
                    int rise = shape.rimRise(dx, dz);
                    assertTrue(depth >= 0 && depth <= shape.depth());
                    assertTrue(rise >= 0 && rise <= shape.rimHeight());
                    assertTrue(depth == 0 || rise == 0, "bowl and rim overlap");
                    if (Math.abs(dx) > reach || Math.abs(dz) > reach) {
                        assertEquals(0, depth + rise, "a write beyond the reach at " + dx + "," + dz);
                    }
                }
            }
            assertEquals(shape.depth(), shape.bowlDepth(0, 0));
        }
        // The legacy weighting favours small craters.
        assertTrue(large < 400, "large craters " + large);
    }

    @Test
    void craterRangesAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> new CraterShape(7, 2, 1, new int[4]));
        assertThrows(IllegalArgumentException.class, () -> new CraterShape(49, 2, 1, new int[4]));
        assertThrows(IllegalArgumentException.class, () -> new CraterShape(20, 2, 9, new int[4]));
        assertThrows(IllegalArgumentException.class, () -> new CraterShape(20, 2, 1, new int[] {6, 0, 0, 0}));
        assertThrows(IllegalArgumentException.class, () -> CraterShape.random(new Random(), 4, 48));
    }

    @Test
    void volcanoesStayWithinRadius32AndHeight48WithAContainedLavaPool() {
        Random random = new Random(1802);
        for (int i = 0; i < 500; i++) {
            VolcanoShape shape = VolcanoShape.random(random);
            assertTrue(shape.radius() <= VolcanoShape.MAX_RADIUS && shape.height() <= VolcanoShape.MAX_HEIGHT);
            assertTrue(shape.craterRadius() > VolcanoShape.CORE_RADIUS, "the core must lie inside the crater");
            // The piece fills lava up to the pool level wherever the cone is lower than that; the pool lies under
            // the rim, so it cannot spill (C15bR1-M2).
            int pool = shape.poolRise();
            assertTrue(pool < shape.coneRise(shape.craterRadius()), "pool " + pool + " reaches the rim");
            int reach = shape.radius() + 1;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    int rise = shape.coneRise(distance);
                    assertTrue(rise >= 0 && rise <= shape.height(), "rise " + rise);
                    if (distance >= shape.radius()) {
                        assertEquals(0, rise, "a cone block beyond the radius");
                    }
                    boolean lava = shape.inCrater(distance) && rise < pool;
                    if (!lava) {
                        continue;
                    }
                    // Lava spreads sideways only into an open block at its own level: every neighbour holds it.
                    for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        double next = Math.sqrt((dx + side[0]) * (dx + side[0]) + (dz + side[1]) * (dz + side[1]));
                        int nextRise = shape.coneRise(next);
                        boolean nextLava = shape.inCrater(next) && nextRise < pool;
                        assertTrue(nextLava || nextRise >= pool,
                                "lava at " + dx + "," + dz + " can flow out at level " + pool);
                    }
                }
            }
        }
    }

    @Test
    void geodesStayWithinRadius24AndKeepAnOpenMiddle() {
        for (int radius = GeodeShape.MIN_RADIUS; radius <= GeodeShape.MAX_RADIUS; radius++) {
            GeodeShape shape = new GeodeShape(radius);
            for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                for (int dz = -radius - 1; dz <= radius + 1; dz++) {
                    int half = shape.halfHeight(dx, dz);
                    if (dx * dx + dz * dz > radius * radius) {
                        assertTrue(half < 0, "outside the lens at " + dx + "," + dz);
                    }
                    assertTrue(half <= shape.verticalReach());
                    int length = shape.clusterLength(dx, dz, 99L);
                    assertTrue(length >= 0 && length <= GeodeShape.MAX_CLUSTER);
                    if (length > 0) {
                        // Roof and floor clusters never meet: at least one open block remains between them.
                        assertTrue(2 * length < 2 * half - 1, "clusters close the hollow at " + dx + "," + dz);
                    }
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new GeodeShape(25));
    }

    @Test
    void venusPatchesUseEveryBiomeAndAreStable() {
        PatchBiomeSource first = PatchBiomeSourceTestSupport.source(2, 32, 7L);
        PatchBiomeSource second = PatchBiomeSourceTestSupport.source(2, 32, 7L);
        Set<Integer> seen = new HashSet<>();
        int changes = 0;
        int previous = first.index(0, 0);
        for (int x = -400; x <= 400; x += 4) {
            int index = first.index(x, x / 3);
            assertEquals(index, second.index(x, x / 3));
            seen.add(index);
            changes += index != previous ? 1 : 0;
            previous = index;
        }
        assertEquals(Set.of(0, 1), seen);
        // Patches, not noise: neighbouring samples mostly agree.
        assertTrue(changes < 60, "changes " + changes);
    }
}
