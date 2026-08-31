package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.Objects;

public record VentSupplyResult(
        VentOperatingStatus status,
        boolean breathable,
        int oxygenUnits,
        int oxygenConsumed,
        int energyStored,
        int energyConsumed,
        int oxygenPhase
) {
    public VentSupplyResult {
        Objects.requireNonNull(status, "status");
        if (oxygenUnits < 0 || oxygenUnits > AtmosphereLimits.VENT_OXYGEN_CAPACITY
                || energyStored < 0 || energyStored > AtmosphereLimits.VENT_ENERGY_CAPACITY) {
            throw new IllegalArgumentException("Vent result storage is outside capacity");
        }
        if (oxygenConsumed < 0 || oxygenConsumed > 1
                || energyConsumed < 0
                || energyConsumed > AtmosphereLimits.VENT_ENERGY_PER_TICK) {
            throw new IllegalArgumentException("Vent result consumption exceeds one tick");
        }
        if (oxygenPhase < 0 || oxygenPhase >= VentSupplyInput.TICKS_PER_OXYGEN) {
            throw new IllegalArgumentException("Vent result phase is outside its finite range");
        }
        if (breathable != (status == VentOperatingStatus.ACTIVE)) {
            throw new IllegalArgumentException("Only an active provider makes a volume breathable");
        }
    }
}
