package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.Objects;

public record PlayerLifeSupportInput(
        boolean baseAtmosphereBreathable,
        BreathabilityState volumeState,
        int equippedSuitPieces,
        int oxygenUnits,
        int vacuumPhase
) {
    public static final int COMPLETE_SUIT_PIECES = 4;
    public static final int TICKS_PER_OXYGEN_OR_DAMAGE = 20;

    public PlayerLifeSupportInput {
        Objects.requireNonNull(volumeState, "volumeState");
        if (equippedSuitPieces < 0 || equippedSuitPieces > COMPLETE_SUIT_PIECES) {
            throw new IllegalArgumentException("Equipped suit-piece count must be between 0 and 4");
        }
        if (oxygenUnits < 0 || oxygenUnits > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
            throw new IllegalArgumentException("Suit oxygen is outside its hard capacity");
        }
        if (vacuumPhase < 0 || vacuumPhase >= TICKS_PER_OXYGEN_OR_DAMAGE) {
            throw new IllegalArgumentException("Vacuum phase must be between 0 and 19");
        }
    }
}
