package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Bounded destination kinds; station coordinates remain server-owned. */
public enum RocketDestination {
    EARTH(0, RocketFlightPlanner.EARTH),
    MOON(1, RocketFlightPlanner.MOON),
    SPACE_STATION(2, RocketFlightPlanner.SPACE_STATION);

    private final int networkId;
    private final RocketTravelProfile profile;

    RocketDestination(int networkId, RocketTravelProfile profile) {
        this.networkId = networkId;
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public int networkId() {
        return networkId;
    }

    public RocketTravelProfile profile() {
        return profile;
    }

    public ResourceLocation bodyId() {
        return profile.bodyId();
    }

    public ResourceLocation dimensionId() {
        return profile.dimensionId();
    }

    public RocketDestination opposite() {
        return switch (this) {
            case EARTH -> MOON;
            case MOON, SPACE_STATION -> EARTH;
        };
    }

    public static RocketDestination fromNetworkId(int networkId) {
        for (RocketDestination destination : values()) {
            if (destination.networkId == networkId) {
                return destination;
            }
        }
        throw new IllegalArgumentException("Unknown rocket destination id " + networkId);
    }

    public static RocketDestination fromDimension(ResourceLocation dimensionId) {
        Objects.requireNonNull(dimensionId, "dimensionId");
        for (RocketDestination destination : values()) {
            if (destination.dimensionId().equals(dimensionId)) {
                return destination;
            }
        }
        throw new IllegalArgumentException("Unsupported rocket dimension " + dimensionId);
    }

    public static RocketDestination fromBody(ResourceLocation bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        for (RocketDestination destination : values()) {
            if (destination.bodyId().equals(bodyId)) {
                return destination;
            }
        }
        throw new IllegalArgumentException("Unsupported rocket body " + bodyId);
    }
}
