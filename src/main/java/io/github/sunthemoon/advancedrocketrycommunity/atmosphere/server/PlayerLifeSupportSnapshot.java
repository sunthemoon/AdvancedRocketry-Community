package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerLifeSupportInput;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.PlayerProtectionStatus;
import java.util.Objects;

/** Finite display-only view produced by the authoritative server. */
public record PlayerLifeSupportSnapshot(
        PlayerProtectionStatus status,
        BreathabilityState breathability,
        int equippedSuitPieces,
        int oxygenUnits
) {
    public PlayerLifeSupportSnapshot {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(breathability, "breathability");
        if (equippedSuitPieces < 0
                || equippedSuitPieces > PlayerLifeSupportInput.COMPLETE_SUIT_PIECES) {
            throw new IllegalArgumentException("Suit-piece count is outside its finite range");
        }
        if (oxygenUnits < 0 || oxygenUnits > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
            throw new IllegalArgumentException("Suit oxygen is outside its finite range");
        }
    }
}
