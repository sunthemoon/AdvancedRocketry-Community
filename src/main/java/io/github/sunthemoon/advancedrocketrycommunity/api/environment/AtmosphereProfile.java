package io.github.sunthemoon.advancedrocketrycommunity.api.environment;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Immutable configured atmosphere, not local room oxygen or player protection. */
public record AtmosphereProfile(
        double pressure,
        boolean breathable,
        double temperatureKelvin,
        ResourceLocation profile
) {
    public AtmosphereProfile {
        Objects.requireNonNull(profile, "profile");
        if (profile.toString().length() > 128) {
            throw new IllegalArgumentException("Atmosphere profile ID exceeds 128 characters");
        }
        if (!Double.isFinite(pressure) || pressure < 0.0D || pressure > 10.0D
                || !Double.isFinite(temperatureKelvin) || temperatureKelvin < 0.0D
                || temperatureKelvin > 2_000.0D) {
            throw new IllegalArgumentException("Atmosphere values exceed the supported bounds");
        }
        if (breathable && pressure <= 0.0D) {
            throw new IllegalArgumentException("Breathable atmosphere requires positive pressure");
        }
    }
}
