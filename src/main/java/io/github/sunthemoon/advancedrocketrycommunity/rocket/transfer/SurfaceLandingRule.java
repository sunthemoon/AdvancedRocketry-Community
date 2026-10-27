package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * ADR-033's surface landing rule, which ADR-075 extends to the Moon. The selector reads the Level and passes the facts
 * here: the surface is the highest column of the footprint, and the rocket is supported when one block of its lowest
 * layer stands on a sturdy top and none stands over fluid.
 */
public final class SurfaceLandingRule {
    /**
     * ADR-075 section 3: the old Moon floor. A recorded Moon destination whose lowest block is at or above it may have
     * been selected without support, so its recheck stays the legacy one. A literal, so that it does not follow the
     * developer platform's height.
     */
    public static final int MOON_LEGACY_RECHECK_Y = 80;

    /** The block directly below one block of the rocket's lowest layer. */
    public enum Below {
        /** Its upward face is sturdy. */
        STURDY,
        /** Neither sturdy nor fluid: the rocket block overhangs it. */
        OPEN,
        /** It holds fluid. */
        FLUID
    }

    private SurfaceLandingRule() {
    }

    /** The height of the rocket's lowest layer: the highest column value, and at least the build minimum plus one. */
    public static int surface(int minBuildHeight, int[] columnHeights) {
        Objects.requireNonNull(columnHeights, "columnHeights");
        int surface = minBuildHeight + 1;
        for (int height : columnHeights) {
            surface = Math.max(surface, height);
        }
        return surface;
    }

    /** Supported: at least one sturdy top and no fluid below the lowest layer. Overhangs are allowed. */
    public static boolean supported(List<Below> below) {
        Objects.requireNonNull(below, "below");
        return !below.contains(Below.FLUID) && below.contains(Below.STURDY);
    }

    /** Selection checks support on every surface except Earth (spawn-based, ADR-033) and shared Space. */
    public static boolean supportAtSelection(ResourceKey<Level> dimension) {
        Objects.requireNonNull(dimension, "dimension");
        return !dimension.equals(Level.OVERWORLD) && !dimension.equals(CelestialIds.SPACE_LEVEL);
    }

    /**
     * A recheck of a recorded destination checks support as selection does, except on the Moon at or above
     * {@link #MOON_LEGACY_RECHECK_Y}, where the destination may have been recorded under the old rule (ADR-075 section 3).
     */
    public static boolean supportAtRecheck(ResourceKey<Level> dimension, int lowestY) {
        if (!supportAtSelection(dimension)) {
            return false;
        }
        return !dimension.equals(CelestialIds.MOON_LEVEL) || lowestY < MOON_LEGACY_RECHECK_Y;
    }
}
