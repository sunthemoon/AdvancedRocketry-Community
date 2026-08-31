package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Immutable environment profile data; damage and life support begin in v0.4.0. */
public record AtmosphereDefinition(
        double pressure,
        boolean breathable,
        double temperatureKelvin,
        ResourceLocation profile
) {
    public static final double MAX_PRESSURE = 10.0D;
    public static final double MAX_TEMPERATURE_KELVIN = 2_000.0D;

    private static final Codec<AtmosphereDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(0.0D, MAX_PRESSURE)
                    .fieldOf("pressure")
                    .forGetter(AtmosphereDefinition::pressure),
            Codec.BOOL.fieldOf("breathable").forGetter(AtmosphereDefinition::breathable),
            Codec.doubleRange(0.0D, MAX_TEMPERATURE_KELVIN)
                    .fieldOf("temperature_kelvin")
                    .forGetter(AtmosphereDefinition::temperatureKelvin),
            BoundedCelestialCodecs.RESOURCE_LOCATION
                    .fieldOf("profile")
                    .forGetter(AtmosphereDefinition::profile)
    ).apply(instance, AtmosphereDefinition::new));

    public static final Codec<AtmosphereDefinition> CODEC = BoundedCelestialCodecs.validated(
            RAW_CODEC,
            AtmosphereDefinition::validate
    );

    public AtmosphereDefinition {
        Objects.requireNonNull(profile, "profile");
    }

    private static DataResult<AtmosphereDefinition> validate(AtmosphereDefinition value) {
        if (!Double.isFinite(value.pressure) || !Double.isFinite(value.temperatureKelvin)) {
            return DataResult.error(() -> "Atmosphere values must be finite");
        }
        if (value.breathable && value.pressure <= 0.0D) {
            return DataResult.error(() -> "A breathable atmosphere must have positive pressure");
        }
        return DataResult.success(value);
    }
}
