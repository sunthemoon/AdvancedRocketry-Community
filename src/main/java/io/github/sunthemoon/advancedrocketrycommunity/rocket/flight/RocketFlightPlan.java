package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable server-computed intent; landing coordinates are deliberately absent. */
public record RocketFlightPlan(
        int schemaVersion,
        UUID requestId,
        ResourceLocation sourceBody,
        ResourceLocation destinationBody,
        ResourceLocation sourceDimension,
        ResourceLocation destinationDimension,
        long requiredFuel,
        long createdAtGameTime
) {
    public static final int SCHEMA_VERSION = 1;

    public RocketFlightPlan {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported rocket flight plan schema");
        }
        Objects.requireNonNull(requestId, "requestId");
        requireIdentifier(sourceBody, "sourceBody");
        requireIdentifier(destinationBody, "destinationBody");
        requireIdentifier(sourceDimension, "sourceDimension");
        requireIdentifier(destinationDimension, "destinationDimension");
        if (sourceBody.equals(destinationBody) || sourceDimension.equals(destinationDimension)) {
            throw new IllegalArgumentException("Rocket flight plan must change body and dimension");
        }
        if (requiredFuel <= 0L || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            throw new IllegalArgumentException("Rocket flight plan fuel is outside the fixed limit");
        }
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Rocket flight plan game time cannot be negative");
        }
    }

    private static void requireIdentifier(ResourceLocation value, String name) {
        Objects.requireNonNull(value, name);
        if (value.toString().length() > RocketLimits.MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(name + " exceeds the fixed identifier limit");
        }
    }
}
