package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ExoplanetWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-063 section 6, revision 6 (A0): the landing ground covers the fixed pads with the widest rocket footprint and a
 * feature's reach, Tau Ceti f's floor keeps it above the sea, and both codecs keep their bounds.
 */
class LandingGroundTest {
    private static final int RADIUS = ExoplanetWorldgen.LANDING_GROUND_RADIUS;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theRadiusCoversEachPadsWidestFootprintAndAFeaturesReach() {
        // Pads are centred on chunk corners up to 64 blocks out along each axis (ADR-006); a landing may span 16
        // chunks, so a footprint centred on a chunk corner is at most 4 chunks, 64 blocks, wide.
        int widest = (int) Math.sqrt(RocketFlightLimits.MAX_LANDING_CHUNKS) * 16;
        double farthestFootprint = Math.hypot(64 + widest / 2.0D, 64 + widest / 2.0D);
        assertEquals(64, widest);
        assertTrue(farthestFootprint + ExoplanetShapes.MAX_REACH <= RADIUS,
                "footprint " + farthestFootprint + " plus reach " + ExoplanetShapes.MAX_REACH + " passes " + RADIUS);
    }

    @Test
    void theFloorHoldsItsLevelInsideAndFallsOnePerSlopeWidthOutside() {
        LandingGroundFloor floor = new LandingGroundFloor(RADIUS, V180ExoplanetWorldgen.LANDING_SLOPE,
                V180ExoplanetWorldgen.LANDING_FLOOR);
        assertEquals(96, V180ExoplanetWorldgen.LANDING_SLOPE);
        assertEquals(0.2D, compute(floor, 0, 0), 1.0E-9);
        assertEquals(0.2D, compute(floor, 100, 100), 1.0E-9);
        assertEquals(0.2D, compute(floor, RADIUS, 0), 1.0E-9);
        assertEquals(0.2D - 0.5D, compute(floor, 0, -(RADIUS + 48)), 1.0E-9);
        assertEquals(0.2D - 1.0D, compute(floor, RADIUS + 96, 0), 1.0E-9);
        assertEquals(0.2D, floor.maxValue());
        assertTrue(floor.minValue() <= compute(floor, 30_000_000, 30_000_000));
        // The floor lies in the alien forest band, and the top block (mid + span n) above the sea (water to y 62).
        assertTrue(V180ExoplanetWorldgen.LANDING_FLOOR >= V180ExoplanetWorldgen.SWAMP_BELOW);
        assertTrue(V180ExoplanetWorldgen.F_SURFACE_MID + V180ExoplanetWorldgen.F_SURFACE_SPAN
                * V180ExoplanetWorldgen.LANDING_FLOOR >= V180ExoplanetWorldgen.SEA_LEVEL + 3);
    }

    @Test
    void theFilterDropsPositionsInsideTheRadiusOnly() {
        LandingGroundFilter filter = new LandingGroundFilter(RADIUS);
        assertFalse(filter.outside(0, 0));
        assertFalse(filter.outside(RADIUS - 1, 0));
        assertTrue(filter.outside(RADIUS, 0));
        assertTrue(filter.outside(0, -RADIUS));
        assertFalse(filter.outside(113, 113), "159.8 blocks out");
        assertTrue(filter.outside(114, 114), "161.2 blocks out");
        assertTrue(filter.outside(30_000_000, -30_000_000), "no overflow at the border");
    }

    @Test
    void bothCodecsRoundTripAndRefuseOutOfRangeValues() {
        // Type dispatch inlines only map codecs; any other codec would be read from a nested "value" field.
        assertTrue(LandingGroundFloor.CODEC.codec() instanceof MapCodec.MapCodecCodec);
        assertTrue(LandingGroundFilter.CODEC instanceof MapCodec.MapCodecCodec);
        LandingGroundFloor floor = new LandingGroundFloor(RADIUS, 96, 0.2D);
        JsonObject encoded = LandingGroundFloor.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, floor).getOrThrow(
                false, message -> { }).getAsJsonObject();
        assertEquals(JsonParser.parseString("{\"inner_radius\":160,\"slope_width\":96,\"level\":0.2}"), encoded);
        assertEquals(floor, LandingGroundFloor.MAP_CODEC.codec().parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
        for (String bad : new String[] {"{\"inner_radius\":2000,\"slope_width\":96,\"level\":0.2}",
                "{\"inner_radius\":160,\"slope_width\":0,\"level\":0.2}",
                "{\"inner_radius\":160,\"slope_width\":96,\"level\":1.5}"}) {
            assertTrue(LandingGroundFloor.MAP_CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(bad))
                    .result().isEmpty(), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> new LandingGroundFloor(-1, 96, 0.2D));

        JsonObject filter = LandingGroundFilter.CODEC.encodeStart(JsonOps.INSTANCE, new LandingGroundFilter(RADIUS))
                .getOrThrow(false, message -> { }).getAsJsonObject();
        assertEquals(JsonParser.parseString("{\"radius\":160}"), filter);
        assertEquals(RADIUS, LandingGroundFilter.CODEC.parse(JsonOps.INSTANCE, filter).result().orElseThrow().radius());
        assertTrue(LandingGroundFilter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"radius\":0}"))
                .result().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new LandingGroundFilter(2_000));
    }

    private static double compute(LandingGroundFloor floor, int x, int z) {
        return floor.compute(new DensityFunction.SinglePointContext(x, 64, z));
    }
}
