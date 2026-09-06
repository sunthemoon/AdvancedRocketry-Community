package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
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
        UUID destinationStationId,
        long requiredFuel,
        long createdAtGameTime,
        TravelTarget destinationTarget
) {
    public static final int SCHEMA_VERSION = 3;

    public RocketFlightPlan {
        if (schemaVersion < 1 || schemaVersion > SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported rocket flight plan schema");
        }
        Objects.requireNonNull(requestId, "requestId");
        requireIdentifier(sourceBody, "sourceBody");
        requireIdentifier(destinationBody, "destinationBody");
        requireIdentifier(sourceDimension, "sourceDimension");
        requireIdentifier(destinationDimension, "destinationDimension");
        Objects.requireNonNull(destinationTarget, "destinationTarget");
        if (sourceBody.equals(destinationBody) && sourceDimension.equals(destinationDimension)) {
            throw new IllegalArgumentException("Rocket flight plan must change logical location");
        }
        boolean stationDestination = destinationDimension.equals(ModIdentity.id("space"));
        boolean stationBodyDestination = destinationBody.equals(ModIdentity.id("space"));
        boolean stationSource = sourceDimension.equals(ModIdentity.id("space"));
        boolean stationBodySource = sourceBody.equals(ModIdentity.id("space"));
        if (schemaVersion < SCHEMA_VERSION
                && (stationDestination != stationBodyDestination || stationSource != stationBodySource)) {
            throw new IllegalArgumentException("Space body and dimension identities must remain paired");
        }
        if (schemaVersion == 1 && destinationStationId != null) {
            throw new IllegalArgumentException("Schema-1 flight plans cannot name a station");
        }
        if (schemaVersion == 1 && stationDestination) {
            throw new IllegalArgumentException("Schema-1 flight plans cannot target Space");
        }
        if (schemaVersion == 2 && stationDestination != (destinationStationId != null)) {
            throw new IllegalArgumentException("Space destination must bind exactly one station UUID");
        }
        validateTarget(destinationTarget, destinationBody, destinationDimension, destinationStationId, schemaVersion);
        if (requiredFuel <= 0L || requiredFuel > RocketFlightLimits.MAX_TRAVEL_FUEL) {
            throw new IllegalArgumentException("Rocket flight plan fuel is outside the fixed limit");
        }
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Rocket flight plan game time cannot be negative");
        }
    }

    public RocketFlightPlan(
            int schemaVersion,
            UUID requestId,
            ResourceLocation sourceBody,
            ResourceLocation destinationBody,
            ResourceLocation sourceDimension,
            ResourceLocation destinationDimension,
            UUID destinationStationId,
            long requiredFuel,
            long createdAtGameTime
    ) {
        this(schemaVersion, requestId, sourceBody, destinationBody, sourceDimension,
                destinationDimension, destinationStationId, requiredFuel, createdAtGameTime,
                legacyTarget(destinationBody, destinationDimension, destinationStationId));
    }

    public RocketFlightPlan(
            int schemaVersion,
            UUID requestId,
            ResourceLocation sourceBody,
            ResourceLocation destinationBody,
            ResourceLocation sourceDimension,
            ResourceLocation destinationDimension,
            long requiredFuel,
            long createdAtGameTime
    ) {
        this(schemaVersion, requestId, sourceBody, destinationBody, sourceDimension,
                destinationDimension, null, requiredFuel, createdAtGameTime);
    }

    public java.util.Optional<UUID> destinationStation() {
        return java.util.Optional.ofNullable(destinationStationId);
    }

    private static void requireIdentifier(ResourceLocation value, String name) {
        Objects.requireNonNull(value, name);
        if (value.toString().length() > RocketLimits.MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException(name + " exceeds the fixed identifier limit");
        }
    }

    private static TravelTarget legacyTarget(
            ResourceLocation destinationBody,
            ResourceLocation destinationDimension,
            UUID stationId
    ) {
        if (destinationDimension.equals(ModIdentity.id("space")) && stationId != null) {
            return new TravelTarget.Station(stationId);
        }
        return new TravelTarget.BodySurface(destinationBody);
    }

    private static void validateTarget(
            TravelTarget target,
            ResourceLocation destinationBody,
            ResourceLocation destinationDimension,
            UUID stationId,
            int schemaVersion
    ) {
        if (schemaVersion < SCHEMA_VERSION && !target.equals(
                legacyTarget(destinationBody, destinationDimension, stationId)
        )) {
            throw new IllegalArgumentException("Legacy flight plan target is inconsistent");
        }
        if (target instanceof TravelTarget.BodySurface surface) {
            if (!surface.bodyId().equals(destinationBody) || stationId != null) {
                throw new IllegalArgumentException("Surface target does not match flight destination");
            }
            return;
        }
        if (target instanceof TravelTarget.Orbit orbit) {
            if (!orbit.bodyId().equals(destinationBody) || stationId != null) {
                throw new IllegalArgumentException("Orbit target does not match flight destination");
            }
            return;
        }
        if (target instanceof TravelTarget.Station station) {
            if (!destinationDimension.equals(ModIdentity.id("space"))
                    || !station.instanceId().equals(stationId)) {
                throw new IllegalArgumentException("Station target does not match flight destination");
            }
            return;
        }
        if (stationId != null) {
            throw new IllegalArgumentException("Mission target cannot carry a station UUID");
        }
    }
}
