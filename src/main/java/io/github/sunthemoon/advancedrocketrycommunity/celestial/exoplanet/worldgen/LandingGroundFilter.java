package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * A placement filter that drops positions less than {@code radius} blocks (horizontally) from the world origin
 * (ADR-063 section 6, revision 6): the Tau Ceti features and the alien forest grass stay off the landing ground, so
 * the fixed landing pads (ADR-006) find free ground.
 */
public final class LandingGroundFilter extends PlacementFilter {
    /**
     * Reads the plain radius and checks it as a result, so an out-of-range radius is a codec error; a map codec, so
     * the placement JSON stays flat: {@code {"type": ..., "radius": 224}}.
     */
    public static final Codec<LandingGroundFilter> CODEC = Codec.INT.fieldOf("radius").<LandingGroundFilter>flatXmap(
            radius -> valid(radius) ? DataResult.success(new LandingGroundFilter(radius))
                    : DataResult.error(() -> "Landing ground radius " + radius + " outside 1-"
                            + LandingGroundFloor.MAX_RADIUS),
            filter -> DataResult.success(filter.radius)).codec();

    private final int radius;

    public LandingGroundFilter(int radius) {
        if (!valid(radius)) {
            throw new IllegalArgumentException("Landing ground radius out of range: " + radius);
        }
        this.radius = radius;
    }

    private static boolean valid(int radius) {
        return radius >= 1 && radius <= LandingGroundFloor.MAX_RADIUS;
    }

    public int radius() {
        return radius;
    }

    /** Whether a column lies outside the landing ground. */
    public boolean outside(int x, int z) {
        return (long) x * x + (long) z * z >= (long) radius * radius;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos position) {
        return outside(position.getX(), position.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return ExoplanetWorldgen.LANDING_GROUND_FILTER.get();
    }
}
