package io.github.sunthemoon.advancedrocketrycommunity.celestial.environment;

/** Finite surface inputs. No world lookup or persistent exposure state. */
public record EnvironmentalConditions(boolean enabled, double pressure, double temperatureKelvin,
                                      double solarIntensity) {
    public static final EnvironmentalConditions DISABLED = new EnvironmentalConditions(false, 1, 288, 1);

    public EnvironmentalConditions {
        finite(pressure, 10, "pressure");
        finite(temperatureKelvin, 2000, "temperature");
        finite(solarIntensity, 16, "solar intensity");
    }

    public boolean requiresClimateControl() {
        return enabled && (pressure > 2 || temperatureKelvin < 240 || temperatureKelvin > 330);
    }

    private static void finite(double value, double maximum, String name) {
        if (!Double.isFinite(value) || value < 0 || value > maximum) {
            throw new IllegalArgumentException("Invalid environmental " + name);
        }
    }
}
