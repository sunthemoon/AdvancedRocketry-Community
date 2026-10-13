package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.environment.EnvironmentalExposure;
import java.util.Objects;
import java.util.Optional;

/**
 * ADR-066 section 3.3 pure decisions for a non-player living entity. The atmosphere attempt reuses the player
 * transition with no suit and no oxygen, then the ADR-034 hazard transition; spawn admission reuses the same
 * breathability and hazard inputs. No world lookup and no per-entity state.
 */
public final class NonPlayerAtmosphere {
    public static final int INTERVAL = PlayerLifeSupportInput.TICKS_PER_OXYGEN_OR_DAMAGE;
    private static final int LAST_PHASE = INTERVAL - 1;

    private NonPlayerAtmosphere() {
    }

    /**
     * One scheduled tick in every {@link #INTERVAL} entity ticks, from the entity's own lifecycle fields. The entity
     * ID spreads the attempts of a freshly loaded group over the interval.
     */
    public static boolean scheduled(int tickCount, int entityId) {
        return Math.floorMod((long) tickCount + entityId, INTERVAL) == 0;
    }

    /**
     * The single attempt of one scheduled tick: suffocation first, otherwise the ADR-034 primary hazard. A configured
     * vacuum damage of zero removes only the suffocation damage, so the exposure attempt still happens.
     */
    public static Attempt attempt(boolean baseAtmosphereBreathable, BreathabilityState eye, int hazards,
                                  double vacuumDamage) {
        Objects.requireNonNull(eye, "eye");
        PlayerLifeSupportDecision suffocation = PlayerLifeSupportEngine.tick(
                new PlayerLifeSupportInput(baseAtmosphereBreathable, eye, 0, 0, LAST_PHASE));
        float damage = PlayerLifeSupportEngine.vacuumDamage(suffocation, vacuumDamage);
        if (damage > 0.0F) {
            return new Attempt(Kind.VACUUM, 0, damage);
        }
        EnvironmentalExposure.Decision exposure = EnvironmentalExposure.tick(hazards, LAST_PHASE);
        return exposure.damage() > 0.0F
                ? new Attempt(Kind.ENVIRONMENTAL, exposure.hazards(), exposure.damage())
                : Attempt.NONE;
    }

    /**
     * Natural and spawner admission for a type without the bypass tag. A nonbreathable Level needs a known breathable
     * eye cell (an unavailable, PENDING or vacuum cell is denied), and any unprotected hazard of an opted-in profile
     * is denied. A breathable Level does not depend on the cell's loaded volume.
     */
    public static boolean admitSpawn(boolean baseAtmosphereBreathable, Optional<BreathabilityState> eye,
                                     int unprotectedHazards) {
        Objects.requireNonNull(eye, "eye");
        if ((unprotectedHazards & ~EnvironmentalExposure.ALL) != 0) {
            throw new IllegalArgumentException("Invalid environmental hazard mask");
        }
        if (!baseAtmosphereBreathable && eye.filter(BreathabilityState.BREATHABLE::equals).isEmpty()) {
            return false;
        }
        return unprotectedHazards == 0;
    }

    public enum Kind {
        NONE,
        VACUUM,
        ENVIRONMENTAL
    }

    public record Attempt(Kind kind, int hazards, float damage) {
        public static final Attempt NONE = new Attempt(Kind.NONE, 0, 0.0F);

        public Attempt {
            Objects.requireNonNull(kind, "kind");
            if (!Float.isFinite(damage) || damage < 0.0F || (kind == Kind.NONE) != (damage == 0.0F)) {
                throw new IllegalArgumentException("An attempt has positive finite damage exactly when it is not NONE");
            }
            if ((hazards & ~EnvironmentalExposure.ALL) != 0 || (kind == Kind.ENVIRONMENTAL) != (hazards != 0)) {
                throw new IllegalArgumentException("Only an environmental attempt carries a hazard mask");
            }
        }
    }
}
