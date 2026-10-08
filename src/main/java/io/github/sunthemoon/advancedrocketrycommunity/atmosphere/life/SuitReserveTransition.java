package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.Objects;

/**
 * Pure calculation that may refill the active suit buffer from a reserve on the
 * scheduled debit tick, then runs the unchanged engine once. A computed result
 * is not a committed oxygen payment and grants no authority by itself.
 */
public final class SuitReserveTransition {
    private SuitReserveTransition() {
    }

    public static Result tick(
            PlayerLifeSupportInput input,
            int reserveUnits,
            int reserveCapacity,
            int targetUnits,
            boolean reserveEligible
    ) {
        Objects.requireNonNull(input, "input");
        if (reserveCapacity < 0) {
            throw new IllegalArgumentException("Reserve capacity must be non-negative");
        }
        if (reserveUnits < 0 || reserveUnits > reserveCapacity) {
            throw new IllegalArgumentException("Reserve oxygen is outside its capacity");
        }
        if (targetUnits < 1 || targetUnits > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
            throw new IllegalArgumentException("Active oxygen target must be between 1 and 2000");
        }

        // Both operands are within 0..2000, so the deficit cannot overflow and
        // active plus transfer never exceeds max(active, target).
        int transferred = 0;
        if (transferAdmitted(input, reserveEligible)) {
            int deficit = Math.max(0, targetUnits - input.oxygenUnits());
            transferred = Math.min(deficit, reserveUnits);
        }
        PlayerLifeSupportDecision decision = PlayerLifeSupportEngine.tick(new PlayerLifeSupportInput(
                input.baseAtmosphereBreathable(),
                input.volumeState(),
                input.equippedSuitPieces(),
                input.oxygenUnits() + transferred,
                input.vacuumPhase()
        ));
        return new Result(decision, reserveUnits - transferred, transferred);
    }

    private static boolean transferAdmitted(PlayerLifeSupportInput input, boolean reserveEligible) {
        return reserveEligible
                && input.equippedSuitPieces() == PlayerLifeSupportInput.COMPLETE_SUIT_PIECES
                && !input.baseAtmosphereBreathable()
                && input.volumeState() != BreathabilityState.BREATHABLE
                && input.vacuumPhase() == PlayerLifeSupportInput.TICKS_PER_OXYGEN_OR_DAMAGE - 1;
    }

    public record Result(
            PlayerLifeSupportDecision decision,
            int reserveUnits,
            int transferredUnits
    ) {
        public Result {
            Objects.requireNonNull(decision, "decision");
            if (reserveUnits < 0) {
                throw new IllegalArgumentException("Reserve oxygen must be non-negative");
            }
            if (transferredUnits < 0 || transferredUnits > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
                throw new IllegalArgumentException("Transferred oxygen is outside the active buffer");
            }
        }
    }
}
