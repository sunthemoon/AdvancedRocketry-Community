package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * C15b review R1-M4: GameTests hold the server switches in memory instead of writing the config file, which Forge
 * reloads on another thread. Every switch accessor reads its override first, until it is cleared.
 */
class SwitchOverridesTest {
    private static final Map<ForgeConfigSpec.BooleanValue, BooleanSupplier> SWITCHES = Map.ofEntries(
            Map.entry(CommonConfig.SMALL_PLATE_PRESS_ENABLED, CommonConfig::smallPlatePressEnabled),
            Map.entry(CommonConfig.OVERWORLD_ORES_ENABLED, () -> WorldgenSwitches.enabled(WorldgenSwitches.OVERWORLD_ORES)),
            Map.entry(CommonConfig.PLANET_ORES_ENABLED, () -> WorldgenSwitches.enabled(WorldgenSwitches.PLANET_ORES)),
            Map.entry(CommonConfig.CRATERS_ENABLED, () -> WorldgenSwitches.enabled(WorldgenSwitches.CRATERS)),
            Map.entry(CommonConfig.VOLCANOES_ENABLED, () -> WorldgenSwitches.enabled(WorldgenSwitches.VOLCANOES)),
            Map.entry(CommonConfig.GEODES_ENABLED, () -> WorldgenSwitches.enabled(WorldgenSwitches.GEODES)),
            Map.entry(CommonConfig.CHARRED_TREES_ENABLED, () -> WorldgenSwitches.enabled(WorldgenSwitches.CHARRED_TREES)),
            Map.entry(CommonConfig.LIGHTWOOD_TREES_ENABLED,
                    () -> WorldgenSwitches.enabled(WorldgenSwitches.LIGHTWOOD_TREES)),
            Map.entry(CommonConfig.SWAMP_TREES_ENABLED, () -> WorldgenSwitches.enabled(WorldgenSwitches.SWAMP_TREES)),
            Map.entry(CommonConfig.INVERTED_PILLARS_ENABLED,
                    () -> WorldgenSwitches.enabled(WorldgenSwitches.INVERTED_PILLARS)),
            Map.entry(CommonConfig.CRYSTAL_CLUSTERS_ENABLED,
                    () -> WorldgenSwitches.enabled(WorldgenSwitches.CRYSTAL_CLUSTERS)),
            Map.entry(CommonConfig.ELECTRIC_MUSHROOMS_ENABLED,
                    () -> WorldgenSwitches.enabled(WorldgenSwitches.ELECTRIC_MUSHROOMS)));

    @AfterEach
    void release() {
        SWITCHES.keySet().forEach(SwitchOverrides::clear);
    }

    @Test
    void everySwitchReadsItsOverrideUntilItIsCleared() {
        assertEquals(WorldgenSwitches.names().size() + 1, SWITCHES.size(), "the press and every worldgen switch");
        for (Map.Entry<ForgeConfigSpec.BooleanValue, BooleanSupplier> entry : SWITCHES.entrySet()) {
            String name = String.join(".", entry.getKey().getPath());
            assertTrue(entry.getValue().getAsBoolean(), name + " defaults on");
            SwitchOverrides.set(entry.getKey(), false);
            assertFalse(entry.getValue().getAsBoolean(), name + " ignored its override");
            SwitchOverrides.set(entry.getKey(), true);
            assertTrue(entry.getValue().getAsBoolean(), name + " kept an older override");
            SwitchOverrides.set(entry.getKey(), false);
            SwitchOverrides.clear(entry.getKey());
            assertTrue(entry.getValue().getAsBoolean(), name + " kept its override after clear");
        }
    }

    @Test
    void onlyServerSwitchesTakeAnOverride() {
        assertThrows(IllegalArgumentException.class,
                () -> SwitchOverrides.set(CommonConfig.ENDGAME_LASER_PHYSICAL, false));
    }
}
