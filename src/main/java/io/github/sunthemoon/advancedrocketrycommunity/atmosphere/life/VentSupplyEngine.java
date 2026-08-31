package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;

/** Pure provider selection result; storage mutation remains in the server adapter. */
public final class VentSupplyEngine {
    private VentSupplyEngine() {
    }

    public static VentSupplyResult tick(VentSupplyInput input) {
        VentOperatingStatus inactive = statusBeforeSupply(input);
        if (inactive != VentOperatingStatus.ACTIVE) {
            return new VentSupplyResult(
                    inactive,
                    false,
                    input.oxygenUnits(),
                    0,
                    input.energyStored(),
                    0,
                    0
            );
        }

        int nextPhase = (input.oxygenPhase() + 1) % VentSupplyInput.TICKS_PER_OXYGEN;
        int oxygenConsumed = nextPhase == 0 ? 1 : 0;
        return new VentSupplyResult(
                VentOperatingStatus.ACTIVE,
                true,
                input.oxygenUnits() - oxygenConsumed,
                oxygenConsumed,
                input.energyStored() - AtmosphereLimits.VENT_ENERGY_PER_TICK,
                AtmosphereLimits.VENT_ENERGY_PER_TICK,
                nextPhase
        );
    }

    private static VentOperatingStatus statusBeforeSupply(VentSupplyInput input) {
        if (input.scanOutcome() != VolumeScanOutcome.SEALED) {
            return switch (input.scanOutcome()) {
                case SCANNING -> VentOperatingStatus.SCANNING;
                case OPEN -> VentOperatingStatus.OPEN;
                case TOO_LARGE -> VentOperatingStatus.TOO_LARGE;
                case PENDING -> VentOperatingStatus.PENDING;
                case CANCELLED -> VentOperatingStatus.CANCELLED;
                case SEALED -> throw new IllegalStateException("unreachable");
            };
        }
        if (!input.electedProvider()) {
            return VentOperatingStatus.STANDBY;
        }
        if (input.oxygenUnits() <= 0) {
            return VentOperatingStatus.NO_OXYGEN;
        }
        if (input.energyStored() < AtmosphereLimits.VENT_ENERGY_PER_TICK) {
            return VentOperatingStatus.NO_POWER;
        }
        return VentOperatingStatus.ACTIVE;
    }
}
