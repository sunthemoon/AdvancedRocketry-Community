package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.Objects;

public record PlayerLifeSupportDecision(
        PlayerProtectionStatus status,
        int oxygenUnits,
        int oxygenConsumed,
        int vacuumPhase,
        float damage
) {
    public PlayerLifeSupportDecision {
        Objects.requireNonNull(status, "status");
        if (oxygenUnits < 0 || oxygenUnits > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
            throw new IllegalArgumentException("Decision oxygen is outside suit capacity");
        }
        if (oxygenConsumed < 0 || oxygenConsumed > 1) {
            throw new IllegalArgumentException("One player tick can consume at most one oxygen unit");
        }
        if (vacuumPhase < 0
                || vacuumPhase >= PlayerLifeSupportInput.TICKS_PER_OXYGEN_OR_DAMAGE) {
            throw new IllegalArgumentException("Decision vacuum phase is outside its finite range");
        }
        if (!Float.isFinite(damage) || damage < 0.0F) {
            throw new IllegalArgumentException("Damage must be finite and non-negative");
        }
        if (status.protectedFromVacuum() && damage != 0.0F) {
            throw new IllegalArgumentException("A protected decision cannot apply vacuum damage");
        }
    }
}
