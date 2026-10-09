package io.github.sunthemoon.advancedrocketrycommunity.equipment.tool;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class JackhammerConfigTest {
    @Test void exactEquipmentSwitchIsRegisteredBeforeSpecAndDefaultsEnabled() {
        assertSame(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, CommonConfig.SPEC.getValues().get("equipment.classicEnabled"));
        assertTrue(CommonConfig.CLASSIC_EQUIPMENT_ENABLED.getDefault());
        assertTrue(CommonConfig.classicEquipmentEnabled());
    }

    @Test void nativeBooleanSwitchAcceptsOnlyBooleanAndRecognizedStringForms() {
        var spec = assertInstanceOf(ForgeConfigSpec.ValueSpec.class, CommonConfig.SPEC.getSpec().get("equipment.classicEnabled"));
        assertTrue(spec.test(true)); assertTrue(spec.test(false));
        assertTrue(spec.test("true")); assertTrue(spec.test("FALSE"));
        assertFalse(spec.test("yes")); assertFalse(spec.test(" true "));
        assertFalse(spec.test(1)); assertFalse(spec.test(null));
    }

    @Test void registeredOverrideDisablesOnlyEquipmentAndClearsWithoutConfigWrite() {
        boolean devices = CommonConfig.classicDevicesEnabled();
        boolean gravity = CommonConfig.classicGravityEnabled();
        SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, false);
        try {
            assertFalse(CommonConfig.classicEquipmentEnabled());
            assertEquals(devices, CommonConfig.classicDevicesEnabled());
            assertEquals(gravity, CommonConfig.classicGravityEnabled());
            SwitchOverrides.set(CommonConfig.CLASSIC_EQUIPMENT_ENABLED, true);
            assertTrue(CommonConfig.classicEquipmentEnabled());
        } finally { SwitchOverrides.clear(CommonConfig.CLASSIC_EQUIPMENT_ENABLED); }
        assertTrue(CommonConfig.classicEquipmentEnabled());
    }
}
