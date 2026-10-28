package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W185 / W170-R65: a 1.5-block-wide rocket can intersect the census from the preceding low-edge column. */
final class RocketDiscoveryLowEdgeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyLowEdgeIntersectingRocketColumnIsCheckedAndTicketed() {
        var bounds = new RocketBounds(new RocketPosition(0, 0, 0), new RocketPosition(0, 1, 0));
        for (int origin : new int[] {-31, -15, 1, 17, 33}) {
            var columns = RocketDiscoveryColumns.of(origin, origin, bounds);
            var census = new AABB(origin - 1D, -64, origin - 1D, origin + 2D, 320, origin + 2D);
            for (double x : new double[] {origin - 1.74, origin - 1.25, origin + .5}) {
                for (double z : new double[] {origin - 1.74, origin - 1.25, origin + .5}) {
                    var copy = new AABB(x - .75, 80, z - .75, x + .75, 81.5, z + .75);
                    assertTrue(census.intersects(copy), "control: the physical copy intersects the actual census box");
                    var home = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
                    assertTrue(columns.columns().contains(home), "the checked columns missed " + home + " from " + origin);
                    assertTrue(columns.refreshSet().contains(home), "R1 must ticket every checked low-edge column");
                    assertTrue(columns.releaseSet().contains(home), "the ticket must also be released");
                }
            }
        }
    }
}
