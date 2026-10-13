package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W24: the legacy selector's unclipped discovery and its translated refusal pass-through. */
class LegacyLandingPadOccupancyTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void surfaceDiscoveryWithoutAStationReachesAnyLegalOriginAndClipsOnlyHeight() {
        var arrival = region(9, 80, 0, 11, 82, 0);
        var box = NamedLandingPadOccupancy.discoveryBox(arrival, null, -64, 320);
        double reach = RocketLimits.MAX_BOUNDING_VOLUME - 1;
        // The review's wide occupant: origin (0,80,0), structure X=0..15, entity box 1.5 x 4.
        var entityBox = new AABB(-0.25, 80, -0.25, 1.25, 84, 1.25);
        assertFalse(entityBox.intersects(new AABB(9, 80, 0, 12, 83, 1)));
        assertTrue(entityBox.intersects(box));
        assertEquals(9 - reach, box.minX); assertEquals(12 + reach, box.maxX);
        assertEquals(0 - reach, box.minZ); assertEquals(1 + reach, box.maxZ);
        assertEquals(-64, box.minY);
        assertEquals(320 + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS + 4, box.maxY);
        // An occupant whose origin is the far edge of a full-length structure is still found.
        assertTrue(box.intersects(new AABB(9 - reach, 80, 0, 9 - reach + 1.5, 84, 1)));
    }

    @Test void onlyStatedPadReasonsReachThePlayer() {
        assertEquals(NamedLandingPadOccupancy.OCCUPIED,
                RocketLandingPadSelection.failure(1, 1, NamedLandingPadOccupancy.OCCUPIED).refusalKey());
        assertEquals(NamedLandingPadOccupancy.ENTITY_BUDGET,
                RocketLandingPadSelection.failure(1, 0, NamedLandingPadOccupancy.ENTITY_BUDGET).refusalKey());
        assertNull(RocketLandingPadSelection.failure(8, 3, "all fixed pads are occupied or unsafe").refusalKey());
        assertNull(RocketLandingPadSelection.failure(1, 1, RocketLandingPadSelector.UNSAFE).refusalKey());
        assertNull(RocketLandingPadSelection.success(snapshot(), 1, 1).refusalKey());
        for (String key : List.of(NamedLandingPadOccupancy.OCCUPIED, NamedLandingPadOccupancy.ENTITY_BUDGET)) {
            var result = new RocketFlightRequestResult(RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE, 100, key);
            assertEquals(key, result.detailKey());
        }
    }

    private static RocketStructureSnapshot snapshot() {
        var block = new RocketBlock(new RocketPosition(0, 0, 0),
                new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()));
        return RocketStructureSnapshot.create(UUID.randomUUID(), OVERWORLD, new RocketPosition(0, 80, 0),
                List.of(block), List.of(), new RocketStats(1, 1, 0, 0, 0, 0, 0, 0), 0);
    }

    private static RocketRegion region(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new RocketRegion(OVERWORLD, new RocketPosition(x1, y1, z1), new RocketPosition(x2, y2, z2));
    }
}
