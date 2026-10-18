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
        Readiness readiness
) {
    /**
     * W90 (Root decision R21 item 6): {@code k/n} or {@code invalid} per recorded end, NONE, LOADING, CENSUS or
     * HOLD:reason, and the size of the last refreshed ticket set. Read from RAM and the loaded index only.
     */
    public record Readiness(String sourceMargin, String destinationMargin, String waitState, int tickets) {
        private static final int MAX_FIELD_LENGTH = 64;

        public Readiness {
            for (String field : new String[] {sourceMargin, destinationMargin, waitState}) {
                if (Objects.requireNonNull(field, "readiness field").length() > MAX_FIELD_LENGTH) {
                    throw new IllegalArgumentException("Transfer inspection readiness field exceeds its bound");
                }
            }
            if (tickets < 0 || tickets > 2 * RocketFlightLimits.MAX_LANDING_CHUNKS) {
                throw new IllegalArgumentException("Transfer inspection ticket count is outside its bound");
            }
        }
    }

    public RocketTransferInspection {
        Objects.requireNonNull(readiness, "readiness");
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
