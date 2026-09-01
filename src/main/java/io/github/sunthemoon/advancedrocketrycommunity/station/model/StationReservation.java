package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Durable pre-generation station reservation. */
public record StationReservation(
        UUID stationId,
        UUID ownerId,
        String name,
        StationGridCell cell,
        ResourceLocation orbitBody,
        long createdAtGameTime
) {
    public StationReservation {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(ownerId, "ownerId");
        name = StationState.requireName(name);
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(orbitBody, "orbitBody");
        if (orbitBody.toString().length() > 128) {
            throw new IllegalArgumentException("Station orbit body identifier is too long");
        }
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("Station reservation time cannot be negative");
        }
    }

    public StationRegion region() {
        return cell.region();
    }

    public StationPosition landingPad() {
        return cell.landingPad();
    }
}

