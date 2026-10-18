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
        int destinationMatches,
        // W90 (R21 item 6): k/n or "invalid" per end, NONE/LOADING/CENSUS/HOLD:reason, and the last refreshed set size.
        String sourceMargin,
        String destinationMargin,
        String readinessWait,
        int marginTickets
) {
    private static final int MAX_FIELD_LENGTH = 64;

    public RocketTransferInspection {
        for (String field : new String[] {sourceMargin, destinationMargin, readinessWait}) {
            if (Objects.requireNonNull(field, "margin field").length() > MAX_FIELD_LENGTH) {
                throw new IllegalArgumentException("Transfer inspection margin field exceeds its bound");
            }
        }
        if (marginTickets < 0 || marginTickets > 2 * RocketFlightLimits.MAX_LANDING_CHUNKS) {
            throw new IllegalArgumentException("Transfer inspection ticket count is outside its bound");
        }
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
