package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;
import java.util.Objects;

public record VentSupplyInput(
        VolumeScanOutcome scanOutcome,
        boolean electedProvider,
        int oxygenUnits,
        int energyStored,
        int oxygenPhase
) {
    public static final int TICKS_PER_OXYGEN = 20;

    public VentSupplyInput {
        Objects.requireNonNull(scanOutcome, "scanOutcome");
        if (oxygenUnits < 0 || oxygenUnits > AtmosphereLimits.VENT_OXYGEN_CAPACITY) {
            throw new IllegalArgumentException("Vent oxygen is outside capacity");
        }
        if (energyStored < 0 || energyStored > AtmosphereLimits.VENT_ENERGY_CAPACITY) {
            throw new IllegalArgumentException("Vent energy is outside capacity");
        }
        if (oxygenPhase < 0 || oxygenPhase >= TICKS_PER_OXYGEN) {
            throw new IllegalArgumentException("Vent oxygen phase must be between 0 and 19");
        }
    }
}
