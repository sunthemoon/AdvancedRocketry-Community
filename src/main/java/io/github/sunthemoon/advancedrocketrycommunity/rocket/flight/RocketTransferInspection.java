package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Bounded operator-facing projection of one durable transfer record. */
public record RocketTransferInspection(
        UUID transferId,
        UUID logicalRocketId,
        RocketTransferPhase phase,
        ResourceLocation sourceDimension,
        ResourceLocation destinationDimension,
        UUID sourceEntityId,
        Optional<UUID> destinationEntityId,
        long fuelBefore,
        long fuelAfter,
        int passengerCount,
        String checksum,
        int sourceMatches,
        int destinationMatches
) {
    public RocketTransferInspection {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(logicalRocketId, "logicalRocketId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(sourceDimension, "sourceDimension");
        Objects.requireNonNull(destinationDimension, "destinationDimension");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        destinationEntityId = Objects.requireNonNull(destinationEntityId, "destinationEntityId");
        Objects.requireNonNull(checksum, "checksum");
        if (fuelBefore < 0L || fuelAfter < 0L
                || passengerCount < 0 || passengerCount > RocketFlightLimits.MAX_PASSENGERS
                || sourceMatches < 0 || destinationMatches < 0) {
            throw new IllegalArgumentException("Transfer inspection values are outside fixed bounds");
        }
    }
}
