package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class ClassicDevicesConfigTest {
    @AfterEach
    void release() {
        SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED);
        CommonConfig.SPEC.setConfig(null);
    }

    @Test
    void stablePathDefaultsToEnabledWithoutLoadedConfig() {
        assertFalse(CommonConfig.SPEC.isLoaded());
        assertEquals(List.of("lifeSupport", "classicDevicesEnabled"), CommonConfig.CLASSIC_DEVICES_ENABLED.getPath());
        assertTrue(CommonConfig.classicDevicesEnabled());
    }

    @Test
    void loadedSwitchIsReadWithoutChangingRegistration() {
        CommonConfig.SPEC.setConfig(CommentedConfig.inMemory());
        assertTrue(CommonConfig.classicDevicesEnabled());
        CommonConfig.CLASSIC_DEVICES_ENABLED.set(false);
        assertFalse(CommonConfig.classicDevicesEnabled());
        CommonConfig.CLASSIC_DEVICES_ENABLED.set(true);
        assertTrue(CommonConfig.classicDevicesEnabled());
    }

    @Test
    void testOverrideIsEphemeralAndWinsUntilCleared() {
        SwitchOverrides.set(CommonConfig.CLASSIC_DEVICES_ENABLED, false);
        assertFalse(CommonConfig.classicDevicesEnabled());
        SwitchOverrides.clear(CommonConfig.CLASSIC_DEVICES_ENABLED);
        assertTrue(CommonConfig.classicDevicesEnabled());
    }
}
