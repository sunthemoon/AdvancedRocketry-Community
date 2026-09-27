package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Immutable configured environment, with the same invariants for code and data input. */
public record AtmosphereDefinition(
        double pressure,
        boolean breathable,
        double temperatureKelvin,
        ResourceLocation profile
) {
    public static final double MAX_PRESSURE = 10.0D;
    public static final double MAX_TEMPERATURE_KELVIN = 2_000.0D;

    private static final Codec<AtmosphereDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BoundedCelestialCodecs.finiteDouble(0.0D, MAX_PRESSURE)
                    .fieldOf("pressure")
                    .forGetter(AtmosphereDefinition::pressure),
            BoundedCelestialCodecs.BOOLEAN.fieldOf("breathable").forGetter(AtmosphereDefinition::breathable),
            BoundedCelestialCodecs.finiteDouble(0.0D, MAX_TEMPERATURE_KELVIN)
                    .fieldOf("temperature_kelvin")
                    .forGetter(AtmosphereDefinition::temperatureKelvin),
            BoundedCelestialCodecs.RESOURCE_LOCATION
                    .fieldOf("profile")
                    .forGetter(AtmosphereDefinition::profile)
    ).apply(instance, AtmosphereDefinition::new));

    public static final Codec<AtmosphereDefinition> CODEC = BoundedCelestialCodecs.guarded(RAW_CODEC);

    public AtmosphereDefinition {
        BoundedCelestialCodecs.requireId(profile, "atmosphere profile");
        BoundedCelestialCodecs.requireRange(pressure, 0, MAX_PRESSURE, "pressure");
        BoundedCelestialCodecs.requireRange(temperatureKelvin, 0, MAX_TEMPERATURE_KELVIN, "temperature");
        if (breathable && pressure <= 0.0D) {
            throw new IllegalArgumentException("A breathable atmosphere must have positive pressure");
        }
    }
}
