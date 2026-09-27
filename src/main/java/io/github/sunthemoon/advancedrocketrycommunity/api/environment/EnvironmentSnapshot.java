package io.github.sunthemoon.advancedrocketrycommunity.api.environment;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Detached configured environment and logical identity, not effective entity physics.
 * Station records do not define an atmosphere profile; its absence is not breathable air.
 */
public record EnvironmentSnapshot(
        ResourceLocation bodyId,
        Locus locus,
        Optional<UUID> instanceId,
        double gravityMultiplier,
        boolean vacuum,
        Optional<AtmosphereProfile> atmosphere
) {
    public EnvironmentSnapshot {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(locus, "locus");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(atmosphere, "atmosphere");
        if (bodyId.toString().length() > 128) {
            throw new IllegalArgumentException("Body ID exceeds 128 characters");
        }
        if (!Double.isFinite(gravityMultiplier) || gravityMultiplier < 0.0D || gravityMultiplier > 10.0D) {
            throw new IllegalArgumentException("Gravity exceeds the supported environment bounds");
        }
        if (locus == Locus.SURFACE) {
            if (instanceId.isPresent() || atmosphere.isEmpty()
                    || vacuum != (atmosphere.orElseThrow().pressure() == 0.0D)) {
                throw new IllegalArgumentException("Surface identity or atmosphere is inconsistent");
            }
        } else if (instanceId.isEmpty() || atmosphere.isPresent()) {
            throw new IllegalArgumentException("Station identity or atmosphere is inconsistent");
        }
    }

    public enum Locus {
        SURFACE,
        STATION_ORBIT
    }
}
