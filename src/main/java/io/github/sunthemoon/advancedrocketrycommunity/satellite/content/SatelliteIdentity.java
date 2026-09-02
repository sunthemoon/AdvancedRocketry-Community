package io.github.sunthemoon.advancedrocketrycommunity.satellite.content;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable identity shared by one assembled package and its control chip. */
public record SatelliteIdentity(
        UUID satelliteId,
        UUID ownerId,
        ResourceLocation definitionId
) {
    public SatelliteIdentity {
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(definitionId, "definitionId");
    }
}
