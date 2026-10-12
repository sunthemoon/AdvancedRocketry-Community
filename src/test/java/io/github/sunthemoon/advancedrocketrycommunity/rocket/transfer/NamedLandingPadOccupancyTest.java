package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NamedLandingPadOccupancyTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void discoveryIncludesWideOriginOutsideArrivalAndClipsToStationAndHeight() {
        var arrival = region(9, 80, 0, 11, 82, 0);
        var station = new StationRegion(-256, -256, 255, 255);
        var box = NamedLandingPadOccupancy.discoveryBox(arrival, station, -64, 320);
        var entityBox = new AABB(-0.25, 80, -0.25, 1.25, 84, 1.25);
        assertFalse(entityBox.intersects(new AABB(9, 80, 0, 12, 83, 1)));
        assertTrue(entityBox.intersects(box));
        assertTrue(arrival.overlaps(region(0, 80, 0, 15, 82, 0)));
        assertEquals(-256, box.minX); assertEquals(-256, box.minZ);
        assertEquals(256, box.maxX); assertEquals(256, box.maxZ);
        assertEquals(-64, box.minY);
        assertEquals(320 + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS + 4, box.maxY);
    }

    @Test void discoveryDoesNotAssumeOriginIsTheCenterOfAnAsymmetricStructure() {
        var station = new StationRegion(0, 0, 511, 511);
        var box = NamedLandingPadOccupancy.discoveryBox(region(510, 100, 510, 511, 102, 511), station, 0, 256);
        assertTrue(box.intersects(new AABB(-0.25, 100, -0.25, 1.25, 104, 1.25)));
        assertTrue(RocketLimits.MAX_BOUNDING_VOLUME - 1 >= 511);
        assertEquals(0, box.minX); assertEquals(512, box.maxX);
    }

    @Test void regionOverlapIsExactAndLandingBudgetsRemainUnchanged() {
        var arrival = region(9, 80, 0, 11, 82, 0);
        assertFalse(arrival.overlaps(region(0, 80, 0, 8, 82, 0)));
        assertFalse(arrival.overlaps(region(12, 80, 0, 15, 82, 0)));
        assertFalse(arrival.overlaps(region(0, 83, 0, 15, 85, 0)));
        assertTrue(arrival.overlaps(region(0, 82, 0, 15, 82, 0)));
        assertEquals(16, RocketFlightLimits.MAX_LANDING_CHUNKS);
        assertEquals(2048, RocketFlightLimits.MAX_LANDING_BLOCK_INSPECTIONS);
        assertEquals(64, NamedLandingPadStaging.MAX_OCCUPANCY_ENTITY_INSPECTIONS);
    }

    private static RocketRegion region(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new RocketRegion(ResourceLocation.tryBuild("advancedrocketrycommunity", "space"),
                new RocketPosition(x1, y1, z1), new RocketPosition(x2, y2, z2));
    }
}
