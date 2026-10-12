package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AnalyzerReading;

/** ADR-066 predicates classify cells, never equipment or inferred oxygen concentration. */
public enum DetectorMode {
    BREATHABLE, VACUUM, NON_BREATHABLE_ATMOSPHERE, HIGH_PRESSURE, HOT, COLD;
    public boolean matches(AnalyzerReading reading) {
        if (reading.state() != AnalyzerReading.State.BREATHABLE && reading.state() != AnalyzerReading.State.NON_BREATHABLE) { return false; }
        if (reading.supplied()) { return this == BREATHABLE; }
        boolean breathable = reading.state() == AnalyzerReading.State.BREATHABLE;
        var ambient = reading.ambient().orElseThrow();
        return switch (this) {
            case BREATHABLE -> breathable;
            case VACUUM -> !breathable && ambient.pressure() == 0;
            case NON_BREATHABLE_ATMOSPHERE -> !breathable && ambient.pressure() > 0;
            case HIGH_PRESSURE -> ambient.pressure() > 2;
            case HOT -> ambient.temperatureKelvin() > 330;
            case COLD -> ambient.temperatureKelvin() < 240;
        };
    }
}
