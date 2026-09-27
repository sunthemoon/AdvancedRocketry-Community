package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Bounded logical orbit metadata; it does not drive physical simulation. */
public record OrbitDefinition(
        long distance,
        long periodTicks,
        double inclinationDegrees
) {
    public static final long MAX_DISTANCE = 1_000_000_000L;
    public static final long MAX_PERIOD_TICKS = 10_000_000_000L;

    private static final Codec<Long> DISTANCE_CODEC = boundedLong(
            "orbit distance",
            MAX_DISTANCE
    );
    private static final Codec<Long> PERIOD_CODEC = boundedLong(
            "orbit period",
            MAX_PERIOD_TICKS
    );

    private static final Codec<OrbitDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DISTANCE_CODEC
                    .fieldOf("distance")
                    .forGetter(OrbitDefinition::distance),
            PERIOD_CODEC
                    .fieldOf("period_ticks")
                    .forGetter(OrbitDefinition::periodTicks),
            BoundedCelestialCodecs.finiteDouble(-180.0D, 180.0D)
                    .fieldOf("inclination_degrees")
                    .forGetter(OrbitDefinition::inclinationDegrees)
    ).apply(instance, OrbitDefinition::new));

    public static final Codec<OrbitDefinition> CODEC = BoundedCelestialCodecs.guarded(RAW_CODEC);

    public OrbitDefinition {
        if (distance < 0 || distance > MAX_DISTANCE || periodTicks < 0 || periodTicks > MAX_PERIOD_TICKS) {
            throw new IllegalArgumentException("Orbit distance or period is out of bounds");
        }
        BoundedCelestialCodecs.requireRange(inclinationDegrees, -180, 180, "inclination");
        if ((distance == 0L) != (periodTicks == 0L)) {
            throw new IllegalArgumentException("Root orbit distance and period must both be zero");
        }
    }

    private static Codec<Long> boundedLong(String name, long maximum) {
        return BoundedCelestialCodecs.EXACT_LONG.flatXmap(
                value -> validateLong(name, maximum, value),
                value -> validateLong(name, maximum, value)
        );
    }

    private static DataResult<Long> validateLong(String name, long maximum, long value) {
        if (value < 0L || value > maximum) {
            return DataResult.error(() -> name + " must be between 0 and " + maximum);
        }
        return DataResult.success(value);
    }
}
