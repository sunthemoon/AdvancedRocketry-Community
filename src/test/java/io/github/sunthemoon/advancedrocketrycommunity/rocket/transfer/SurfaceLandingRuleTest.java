package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.SurfaceLandingRule.Below.FLUID;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.SurfaceLandingRule.Below.OPEN;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.SurfaceLandingRule.Below.STURDY;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-075: the heightmap and support rule, now also on the Moon, without a running server. */
class SurfaceLandingRuleTest {
    /** The Moon's build minimum ({@code min_y} 0). */
    private static final int MOON_MIN = 0;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void theSurfaceIsTheHighestColumnBelowAtOrAboveTheOldFloor() {
        assertEquals(37, SurfaceLandingRule.surface(MOON_MIN, new int[] {37, 37}));
        assertEquals(80, SurfaceLandingRule.surface(MOON_MIN, new int[] {80, 37}));
        assertEquals(95, SurfaceLandingRule.surface(MOON_MIN, new int[] {95, 12}));
        // No Moon floor: a low surface stays low.
        assertTrue(SurfaceLandingRule.surface(MOON_MIN, new int[] {13}) < SurfaceLandingRule.MOON_LEGACY_RECHECK_Y);
    }

    @Test void emptyColumnsGiveTheBuildMinimumPlusOne() {
        assertEquals(MOON_MIN + 1, SurfaceLandingRule.surface(MOON_MIN, new int[] {MOON_MIN, MOON_MIN}));
        assertEquals(MOON_MIN + 1, SurfaceLandingRule.surface(MOON_MIN, new int[0]));
        assertEquals(-63, SurfaceLandingRule.surface(-64, new int[] {-64}));
    }

    @Test void mixedColumnsRestOnTheHighest() {
        assertEquals(31, SurfaceLandingRule.surface(MOON_MIN, new int[] {21, 31, 13, 30}));
    }

    @Test void supportNeedsOneSturdyTopAndNoFluid() {
        assertTrue(SurfaceLandingRule.supported(List.of(STURDY)));
        assertTrue(SurfaceLandingRule.supported(List.of(OPEN, STURDY, OPEN)), "One sturdy top with overhang");
        assertFalse(SurfaceLandingRule.supported(List.of(OPEN, OPEN)), "No sturdy top");
        assertFalse(SurfaceLandingRule.supported(List.of()), "No lowest-layer block read");
        assertFalse(SurfaceLandingRule.supported(List.of(STURDY, FLUID)), "Fluid below");
        assertFalse(SurfaceLandingRule.supported(List.of(FLUID)), "Fluid below");
    }

    @Test void selectionChecksSupportOnTheMoonAndOtherSurfacesButNotOnEarthOrInSpace() {
        assertTrue(SurfaceLandingRule.supportAtSelection(CelestialIds.MOON_LEVEL));
        assertTrue(SurfaceLandingRule.supportAtSelection(PlanetaryContent.level(PlanetaryContent.MARS)));
        assertTrue(SurfaceLandingRule.supportAtSelection(PlanetaryContent.level(PlanetaryContent.VENUS)));
        assertFalse(SurfaceLandingRule.supportAtSelection(Level.OVERWORLD));
        assertFalse(SurfaceLandingRule.supportAtSelection(CelestialIds.SPACE_LEVEL));
    }

    @Test void theMoonRecheckThresholdIsTheLiteralOldFloor() {
        assertEquals(80, SurfaceLandingRule.MOON_LEGACY_RECHECK_Y);
        assertTrue(SurfaceLandingRule.supportAtRecheck(CelestialIds.MOON_LEVEL, 79));
        assertFalse(SurfaceLandingRule.supportAtRecheck(CelestialIds.MOON_LEVEL, 80));
        assertFalse(SurfaceLandingRule.supportAtRecheck(CelestialIds.MOON_LEVEL, 81));
        assertTrue(SurfaceLandingRule.supportAtRecheck(CelestialIds.MOON_LEVEL, 13));
    }

    @Test void earthSpaceAndMarsRechecksAreUnchanged() {
        for (int y : new int[] {13, 79, 80, 81, 200}) {
            assertFalse(SurfaceLandingRule.supportAtRecheck(Level.OVERWORLD, y));
            assertFalse(SurfaceLandingRule.supportAtRecheck(CelestialIds.SPACE_LEVEL, y));
            assertTrue(SurfaceLandingRule.supportAtRecheck(PlanetaryContent.level(PlanetaryContent.MARS), y));
            assertTrue(SurfaceLandingRule.supportAtRecheck(PlanetaryContent.level(PlanetaryContent.VENUS), y));
        }
    }
}
