package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

/** Pure finite transition used only by the authoritative server adapter. */
public final class PlayerLifeSupportEngine {
    public static final float VACUUM_DAMAGE = 2.0F;
    /** ADR-066 section 3.3: the finite upper bound of the configured vacuum damage per scheduled second. */
    public static final double MAX_VACUUM_DAMAGE = 20.0D;

    private PlayerLifeSupportEngine() {
    }

    /**
     * ADR-066 section 3.3 {@code vacuumDamage}: the configured health points replace the damage of a scheduled
     * vacuum attempt. Zero removes only the damage; the decision's status, oxygen and phase are unchanged.
     */
    public static float vacuumDamage(PlayerLifeSupportDecision decision, double configured) {
        if (!Double.isFinite(configured) || configured < 0.0D || configured > MAX_VACUUM_DAMAGE) {
            throw new IllegalArgumentException("Vacuum damage must be finite and between 0 and 20");
        }
        return decision.damage() > 0.0F ? (float) configured : 0.0F;
    }

    public static PlayerLifeSupportDecision tick(PlayerLifeSupportInput input) {
        if (input.baseAtmosphereBreathable()) {
            return protectedDecision(
                    PlayerProtectionStatus.BREATHABLE_ENVIRONMENT,
                    input.oxygenUnits()
            );
        }
        if (input.volumeState() == BreathabilityState.BREATHABLE) {
            return protectedDecision(
                    PlayerProtectionStatus.BREATHABLE_VOLUME,
                    input.oxygenUnits()
            );
        }

        int nextPhase = (input.vacuumPhase() + 1)
                % PlayerLifeSupportInput.TICKS_PER_OXYGEN_OR_DAMAGE;
        boolean intervalElapsed = nextPhase == 0;
        if (input.equippedSuitPieces() == PlayerLifeSupportInput.COMPLETE_SUIT_PIECES
                && input.oxygenUnits() > 0) {
            int consumed = intervalElapsed ? 1 : 0;
            return new PlayerLifeSupportDecision(
                    PlayerProtectionStatus.SUIT_OXYGEN,
                    input.oxygenUnits() - consumed,
                    consumed,
                    nextPhase,
                    0.0F
            );
        }

        PlayerProtectionStatus status;
        if (input.volumeState() == BreathabilityState.PENDING) {
            status = PlayerProtectionStatus.VOLUME_PENDING;
        } else if (input.equippedSuitPieces() > 0
                && input.equippedSuitPieces() < PlayerLifeSupportInput.COMPLETE_SUIT_PIECES) {
            status = PlayerProtectionStatus.PARTIAL_SUIT;
        } else if (input.equippedSuitPieces() == PlayerLifeSupportInput.COMPLETE_SUIT_PIECES) {
            status = PlayerProtectionStatus.OXYGEN_EMPTY;
        } else {
            status = PlayerProtectionStatus.EXPOSED;
        }
        return new PlayerLifeSupportDecision(
                status,
                input.oxygenUnits(),
                0,
                nextPhase,
                intervalElapsed ? VACUUM_DAMAGE : 0.0F
        );
    }

    private static PlayerLifeSupportDecision protectedDecision(
            PlayerProtectionStatus status,
            int oxygenUnits
    ) {
        return new PlayerLifeSupportDecision(status, oxygenUnits, 0, 0, 0.0F);
    }
}
