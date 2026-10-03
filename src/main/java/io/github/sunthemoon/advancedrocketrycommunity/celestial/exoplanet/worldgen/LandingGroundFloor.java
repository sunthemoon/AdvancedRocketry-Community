package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * A density function of the horizontal distance {@code r} from the world origin (ADR-063 section 6, revision 6):
 * {@code level} within {@code inner_radius}, falling by one for every {@code slope_width} blocks beyond it. Tau Ceti f
 * takes the larger of it and its terrain noise, so the ground under the fixed landing pads (ADR-006) is never sea.
 */
public record LandingGroundFloor(int innerRadius, int slopeWidth, double level)
        implements DensityFunction.SimpleFunction {
    public static final int MAX_RADIUS = 1_024;
    /** The farthest a column can lie from the origin inside the world border. */
    private static final double MAX_DISTANCE = 30_000_000.0D * Math.sqrt(2.0D);

    /** Reads the plain values and checks them as a result, so an out-of-range value is a codec error. */
    public static final MapCodec<LandingGroundFloor> MAP_CODEC = RecordCodecBuilder.<Raw>mapCodec(instance -> instance
            .group(Codec.INT.fieldOf("inner_radius").forGetter(Raw::innerRadius),
                    Codec.INT.fieldOf("slope_width").forGetter(Raw::slopeWidth),
                    Codec.DOUBLE.fieldOf("level").forGetter(Raw::level))
            .apply(instance, Raw::new))
            .flatXmap(Raw::floor, floor -> DataResult.success(new Raw(floor.innerRadius, floor.slopeWidth, floor.level)));
    public static final KeyDispatchDataCodec<LandingGroundFloor> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    public LandingGroundFloor {
        if (!valid(innerRadius, slopeWidth, level)) {
            throw new IllegalArgumentException("Landing ground floor out of range: " + innerRadius + ", " + slopeWidth
                    + ", " + level);
        }
    }

    private static boolean valid(int innerRadius, int slopeWidth, double level) {
        return innerRadius >= 0 && innerRadius <= MAX_RADIUS && slopeWidth >= 1 && slopeWidth <= MAX_RADIUS
                && level >= -1.0D && level <= 1.0D;
    }

    @Override
    public double compute(FunctionContext context) {
        double x = context.blockX();
        double z = context.blockZ();
        return level - Math.max(0.0D, Math.sqrt(x * x + z * z) - innerRadius) / slopeWidth;
    }

    @Override
    public double minValue() {
        return level - MAX_DISTANCE / slopeWidth;
    }

    @Override
    public double maxValue() {
        return level;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    private record Raw(int innerRadius, int slopeWidth, double level) {
        private DataResult<LandingGroundFloor> floor() {
            return valid(innerRadius, slopeWidth, level)
                    ? DataResult.success(new LandingGroundFloor(innerRadius, slopeWidth, level))
                    : DataResult.error(() -> "Landing ground floor out of range: inner_radius " + innerRadius
                            + " (0-" + MAX_RADIUS + "), slope_width " + slopeWidth + " (1-" + MAX_RADIUS
                            + "), level " + level + " (-1-1)");
        }
    }
}
