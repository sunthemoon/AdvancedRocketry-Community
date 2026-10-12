package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CrystallizerGravityConfigTest {
    @AfterEach void clear() { SwitchOverrides.clear(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY); }
    @Test void defaultAndExactBoundsAreCommonConfiguration() {
        assertEquals(List.of("machines", "crystallizerMaximumGravity"), CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY.getPath());
        assertEquals(0.0, CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY.getDefault());
        var spec = (net.minecraftforge.common.ForgeConfigSpec.ValueSpec) CommonConfig.SPEC.getSpec().get(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY.getPath());
        assertEquals(0.0, spec.getRange().getMin()); assertEquals(10.0, spec.getRange().getMax());
        assertEquals(0.0, CommonConfig.crystallizerMaximumGravity());
    }
    @Test void testOverrideDoesNotWriteAndRejectsOutOfRangeOrOtherKeys() {
        SwitchOverrides.set(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY, 0.5);
        assertEquals(0.5, CommonConfig.crystallizerMaximumGravity());
        SwitchOverrides.clear(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY); assertEquals(0.0, CommonConfig.crystallizerMaximumGravity());
        assertThrows(IllegalArgumentException.class, () -> SwitchOverrides.set(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> SwitchOverrides.set(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY, -1));
        assertThrows(IllegalArgumentException.class, () -> SwitchOverrides.set(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY, 11));
        assertThrows(IllegalArgumentException.class, () -> SwitchOverrides.set(CommonConfig.TANK_CAPACITY_MULTIPLIER, 1));
    }
}
