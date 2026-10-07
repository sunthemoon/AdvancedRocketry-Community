package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** CLIENT-only effects: ADR-063 section 6 and the existing-category subset of ADR-066 section 7.2. */
class ClientConfigTest {
    @AfterEach
    void releaseLoadedConfig() {
        ClientConfig.SPEC.setConfig(null);
    }

    @Test
    void exactClientCategoriesRetainTheElectricMushroomSetting() {
        assertEquals(Set.of("effects", "sky"), ClientConfig.SPEC.getValues().valueMap().keySet());
        assertEquals(Set.of("electricMushroomFlashes"), assertInstanceOf(UnmodifiableConfig.class,
                ClientConfig.SPEC.getValues().get("effects")).valueMap().keySet());
        assertEquals(Set.of("planetOverride", "stationOverride"), assertInstanceOf(UnmodifiableConfig.class,
                ClientConfig.SPEC.getValues().get("sky")).valueMap().keySet());
        assertSame(ClientConfig.ELECTRIC_MUSHROOM_FLASHES,
                ClientConfig.SPEC.getValues().get("effects.electricMushroomFlashes"));
        assertEquals(Boolean.TRUE, ClientConfig.ELECTRIC_MUSHROOM_FLASHES.getDefault());
        assertEquals(List.of("effects", "electricMushroomFlashes"), ClientConfig.ELECTRIC_MUSHROOM_FLASHES.getPath());
        assertTrue(ClientConfig.electricMushroomFlashes(), "the default applies before the config is loaded");
    }

    @Test
    void bothExactSkyKeysDefaultToEnabledWithoutLoadedConfig() {
        assertFalse(ClientConfig.SPEC.isLoaded());
        assertSame(ClientConfig.SKY_PLANET_OVERRIDE, ClientConfig.SPEC.getValues().get("sky.planetOverride"));
        assertSame(ClientConfig.SKY_STATION_OVERRIDE, ClientConfig.SPEC.getValues().get("sky.stationOverride"));
        assertEquals(List.of("sky", "planetOverride"), ClientConfig.SKY_PLANET_OVERRIDE.getPath());
        assertEquals(List.of("sky", "stationOverride"), ClientConfig.SKY_STATION_OVERRIDE.getPath());
        assertEquals(Boolean.TRUE, ClientConfig.SKY_PLANET_OVERRIDE.getDefault());
        assertEquals(Boolean.TRUE, ClientConfig.SKY_STATION_OVERRIDE.getDefault());
        assertTrue(ClientConfig.planetSkyOverride());
        assertTrue(ClientConfig.stationSkyOverride());
    }

    @Test
    void loadedSkySwitchesAreIndependentAndCanBeReenabled() {
        ClientConfig.SPEC.setConfig(CommentedConfig.inMemory());
        assertTrue(ClientConfig.SPEC.isLoaded());
        assertTrue(ClientConfig.planetSkyOverride());
        assertTrue(ClientConfig.stationSkyOverride());
        assertTrue(ClientConfig.electricMushroomFlashes());

        ClientConfig.SKY_PLANET_OVERRIDE.set(false);
        assertFalse(ClientConfig.planetSkyOverride());
        assertTrue(ClientConfig.stationSkyOverride());
        ClientConfig.SKY_STATION_OVERRIDE.set(false);
        assertFalse(ClientConfig.planetSkyOverride());
        assertFalse(ClientConfig.stationSkyOverride());
        ClientConfig.SKY_PLANET_OVERRIDE.set(true);
        assertTrue(ClientConfig.planetSkyOverride());
        assertFalse(ClientConfig.stationSkyOverride());
        ClientConfig.SKY_STATION_OVERRIDE.set(true);
        assertTrue(ClientConfig.planetSkyOverride());
        assertTrue(ClientConfig.stationSkyOverride());
        assertTrue(ClientConfig.electricMushroomFlashes());
    }
}
