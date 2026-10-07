package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class ClassicGravityConfigTest {
    @AfterEach
    void release() {
        SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
        CommonConfig.SPEC.setConfig(null);
    }

    @Test
    void stablePathDefaultsToEnabledWithoutLoadedConfig() {
        assertFalse(CommonConfig.SPEC.isLoaded());
        assertEquals(List.of("environment", "classicGravityEnabled"), CommonConfig.CLASSIC_GRAVITY_ENABLED.getPath());
        assertTrue(CommonConfig.classicGravityEnabled());
    }

    @Test
    void loadedSwitchCanDisableAndReenableLivingGravity() {
        CommonConfig.SPEC.setConfig(CommentedConfig.inMemory());
        assertTrue(CommonConfig.classicGravityEnabled());
        CommonConfig.CLASSIC_GRAVITY_ENABLED.set(false);
        assertFalse(CommonConfig.classicGravityEnabled());
        CommonConfig.CLASSIC_GRAVITY_ENABLED.set(true);
        assertTrue(CommonConfig.classicGravityEnabled());
    }

    @Test
    void testOverrideIsEphemeralAndIndependentOfClassicDevices() {
        boolean devices = CommonConfig.classicDevicesEnabled();
        SwitchOverrides.set(CommonConfig.CLASSIC_GRAVITY_ENABLED, false);
        assertFalse(CommonConfig.classicGravityEnabled());
        assertEquals(devices, CommonConfig.classicDevicesEnabled());
        SwitchOverrides.clear(CommonConfig.CLASSIC_GRAVITY_ENABLED);
        assertTrue(CommonConfig.classicGravityEnabled());
    }
}
