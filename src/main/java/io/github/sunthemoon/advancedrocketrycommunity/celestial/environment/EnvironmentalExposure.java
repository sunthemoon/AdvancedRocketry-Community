package io.github.sunthemoon.advancedrocketrycommunity.celestial.environment;

import java.util.Objects;

/** Pure protection matrix and bounded tick transition; radiation is intentionally metadata only. */
public final class EnvironmentalExposure {
    public static final int COLD = 1;
    public static final int HEAT = 2;
    public static final int PRESSURE = 4;
    public static final int SOLAR = 8;
    public static final int ALL = COLD | HEAT | PRESSURE | SOLAR;
    public static final int INTERVAL = 20;
    public static final float DAMAGE = 2.0F;

    private EnvironmentalExposure() { }

    public static int hazards(EnvironmentalConditions conditions, Protection protection,
                              boolean controlledRoom, boolean directSunlight) {
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(protection, "protection");
        if (!conditions.enabled() || controlledRoom) {
            return 0;
        }
        int mask = 0;
        if (!protection.thermal()) {
            if (conditions.temperatureKelvin() < 240) { mask |= COLD; }
            if (conditions.temperatureKelvin() > 330) { mask |= HEAT; }
        }
        if (conditions.pressure() > 2 && !protection.pressure()) { mask |= PRESSURE; }
        if (conditions.solarIntensity() > 1.5 && directSunlight && !protection.solar()) { mask |= SOLAR; }
        return mask;
    }

    public static Decision tick(int hazards, int phase) {
        if ((hazards & ~ALL) != 0 || phase < 0 || phase >= INTERVAL) {
            throw new IllegalArgumentException("Invalid environmental exposure state");
        }
        int nextPhase = hazards == 0 ? 0 : (phase + 1) % INTERVAL;
        return new Decision(hazards, nextPhase, hazards != 0 && nextPhase == 0 ? DAMAGE : 0);
    }

    public static String primary(int hazards) {
        if ((hazards & ~ALL) != 0 || hazards == 0) {
            throw new IllegalArgumentException("Expected a nonempty environmental hazard mask");
        }
        if ((hazards & PRESSURE) != 0) { return "pressure"; }
        if ((hazards & HEAT) != 0) { return "heat"; }
        return (hazards & COLD) != 0 ? "cold" : "solar";
    }

    public record Protection(boolean thermal, boolean pressure, boolean solar) {
        public static final Protection NONE = new Protection(false, false, false);
    }

    public record Decision(int hazards, int phase, float damage) { }
}
