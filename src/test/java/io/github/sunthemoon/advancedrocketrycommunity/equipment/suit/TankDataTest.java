package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class TankDataTest {
    @Test void creationSnapshotsAndOldMaximaSurviveLowerConfiguration() {
        for (int base : List.of(1000, 2000, 4000, 8000)) {
            for (int multiplier = 1; multiplier <= 4; multiplier++) {
                var created = TankData.read(null, base, multiplier); assertEquals(base * multiplier, created.maximum());
                TankData filled = new TankData(TankData.Gas.OXYGEN, created.maximum(), created.maximum());
                assertEquals(filled, TankData.read(filled.encode(), base, 1));
            }
        }
        assertEquals(List.of("OXYGEN", "suitTankCapacity"), CommonConfig.SUIT_TANK_CAPACITY.getPath());
        assertEquals(List.of("OXYGEN", "spaceSuitO2Buffer"), CommonConfig.SPACE_SUIT_O2_BUFFER.getPath());
        assertEquals(1, CommonConfig.suitTankMultiplier()); assertEquals(2000, CommonConfig.suitOxygenTarget());
    }
    @Test void unsupportedMaximaGasTypesAndExactFieldTypesRefuseWithoutClamping() {
        CompoundTag saved = new TankData(TankData.Gas.OXYGEN, 1000, 2000).encode();
        saved.putInt("maximum", 1500); CompoundTag before = saved.copy(); assertNull(TankData.read(saved, 1000, 1)); assertEquals(before, saved);
        saved.putInt("maximum", 5000); assertNull(TankData.read(saved, 1000, 1));
        saved.putInt("maximum", 2000); saved.putString("gas", "NITROGEN"); assertNull(TankData.read(saved, 1000, 1));
        saved.putString("gas", "EMPTY"); assertNull(TankData.read(saved, 1000, 1));
        saved.putString("gas", "OXYGEN"); saved.putByte("stored", (byte) 1); assertNull(TankData.read(saved, 1000, 1));
        assertThrows(IllegalArgumentException.class, () -> new TankData(TankData.Gas.EMPTY, 1, 1000));
        assertThrows(IllegalArgumentException.class, () -> new TankData(TankData.Gas.OXYGEN, 0, 32001));
    }
}
