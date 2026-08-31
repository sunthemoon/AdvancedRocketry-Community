package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;
import org.junit.jupiter.api.Test;

class VentSupplyEngineTest {
    @Test
    void electedProviderConsumesEnergyEachTickAndOxygenEachSecond() {
        VentSupplyResult result = VentSupplyEngine.tick(new VentSupplyInput(
                VolumeScanOutcome.SEALED,
                true,
                10,
                1_000,
                19
        ));

        assertEquals(VentOperatingStatus.ACTIVE, result.status());
        assertTrue(result.breathable());
        assertEquals(9, result.oxygenUnits());
        assertEquals(1, result.oxygenConsumed());
        assertEquals(980, result.energyStored());
        assertEquals(AtmosphereLimits.VENT_ENERGY_PER_TICK, result.energyConsumed());
        assertEquals(0, result.oxygenPhase());
    }

    @Test
    void secondaryVentDoesNotConsumeOrMultiplySupply() {
        VentSupplyResult result = VentSupplyEngine.tick(new VentSupplyInput(
                VolumeScanOutcome.SEALED,
                false,
                100,
                1_000,
                19
        ));

        assertEquals(VentOperatingStatus.STANDBY, result.status());
        assertFalse(result.breathable());
        assertEquals(100, result.oxygenUnits());
        assertEquals(1_000, result.energyStored());
        assertEquals(0, result.oxygenPhase());
    }

    @Test
    void missingSupplyAndScanFailuresExposeExactStatus() {
        assertEquals(VentOperatingStatus.NO_OXYGEN, status(VolumeScanOutcome.SEALED, 0, 100));
        assertEquals(VentOperatingStatus.NO_POWER, status(VolumeScanOutcome.SEALED, 100, 19));
        assertEquals(VentOperatingStatus.OPEN, status(VolumeScanOutcome.OPEN, 100, 100));
        assertEquals(VentOperatingStatus.TOO_LARGE, status(VolumeScanOutcome.TOO_LARGE, 100, 100));
        assertEquals(VentOperatingStatus.PENDING, status(VolumeScanOutcome.PENDING, 100, 100));
        assertEquals(VentOperatingStatus.BUSY, status(VolumeScanOutcome.BUSY, 100, 100));
        assertEquals(VentOperatingStatus.SCANNING, status(VolumeScanOutcome.SCANNING, 100, 100));
    }

    private static VentOperatingStatus status(VolumeScanOutcome outcome, int oxygen, int energy) {
        return VentSupplyEngine.tick(new VentSupplyInput(outcome, true, oxygen, energy, 0)).status();
    }
}
