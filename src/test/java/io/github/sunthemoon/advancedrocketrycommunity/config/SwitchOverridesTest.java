package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
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
    private static final Map<ForgeConfigSpec.BooleanValue, BooleanSupplier> ENDGAME_SWITCHES = Map.ofEntries(
            Map.entry(CommonConfig.ENDGAME_LASER_DRILL, () -> CommonConfig.endgameSettings().laserDrill()),
            Map.entry(CommonConfig.ENDGAME_LASER_PHYSICAL, () -> CommonConfig.endgameSettings().laserPhysicalMining()),
            Map.entry(CommonConfig.ENDGAME_RAILGUN, () -> CommonConfig.endgameSettings().railgun()),
            Map.entry(CommonConfig.ENDGAME_BLACK_HOLE_GENERATOR,
                    () -> CommonConfig.endgameSettings().blackHoleGenerator()),
            Map.entry(CommonConfig.ENDGAME_GRAVITY_FIELD, () -> CommonConfig.endgameSettings().gravityField()),
            Map.entry(CommonConfig.ENDGAME_SPACE_ELEVATOR, () -> CommonConfig.endgameSettings().spaceElevator()));

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
        ENDGAME_SWITCHES.keySet().forEach(SwitchOverrides::clear);
        CommonConfig.SPEC.setConfig(null);
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
                () -> SwitchOverrides.set(ClientConfig.SKY_PLANET_OVERRIDE, false));
    }

    @Test
    void endgameOverridesAreIsolatedAndReleaseToUnloadedDefaults() {
        assertFalse(CommonConfig.SPEC.isLoaded());
        assertEquals(EndgameSettings.DEFAULTS, CommonConfig.endgameSettings());
        for (var entry : ENDGAME_SWITCHES.entrySet()) {
            for (boolean enabled : new boolean[]{false, true}) {
                SwitchOverrides.set(entry.getKey(), enabled);
                assertEndgameValues(entry.getKey(), enabled, false);
                assertLimitsEqual(EndgameSettings.DEFAULTS, CommonConfig.endgameSettings());
            }
            SwitchOverrides.clear(entry.getKey());
            assertEquals(EndgameSettings.DEFAULTS, CommonConfig.endgameSettings());
        }
    }

    @Test
    void loadedMemoryConfigIsUnchangedAndReloadCannotReplaceOverrides() {
        CommentedConfig config = CommentedConfig.inMemory();
        CommonConfig.SPEC.correct(config);
        ENDGAME_SWITCHES.keySet().forEach(value -> config.set(value.getPath(), !value.getDefault()));
        config.set(CommonConfig.ENDGAME_INTENT_INTERVAL_TICKS.getPath(), 20);
        config.set(CommonConfig.ENDGAME_SELECTION_INTERVAL_TICKS.getPath(), 4);
        config.set(CommonConfig.ENDGAME_ENDPOINTS_GLOBAL.getPath(), 11);
        config.set(CommonConfig.ENDGAME_ENDPOINTS_PER_OWNER.getPath(), 3);
        config.set(CommonConfig.ENDGAME_ZONES.getPath(), 7);
        CommonConfig.SPEC.setConfig(config);
        assertTrue(CommonConfig.SPEC.isLoaded());
        String original = config.toString();
        EndgameSettings baseline = CommonConfig.endgameSettings();
        for (var entry : ENDGAME_SWITCHES.entrySet()) {
            for (boolean enabled : new boolean[]{false, true}) {
                SwitchOverrides.set(entry.getKey(), enabled);
                CommonConfig.SPEC.afterReload();
                assertEndgameValues(entry.getKey(), enabled, true);
                assertLimitsEqual(baseline, CommonConfig.endgameSettings());
                assertEquals(original, config.toString(), "override must not mutate loaded config data");
                ENDGAME_SWITCHES.keySet().forEach(value ->
                        assertEquals(!value.getDefault(), value.get(), "raw loaded switch must stay unchanged"));
            }
            SwitchOverrides.clear(entry.getKey());
            assertEquals(baseline, CommonConfig.endgameSettings(), "clear returns to the loaded config");
        }
    }

    private static void assertEndgameValues(ForgeConfigSpec.BooleanValue changed, boolean enabled, boolean loaded) {
        for (var entry : ENDGAME_SWITCHES.entrySet()) {
            boolean expected = entry.getKey() == changed ? enabled
                    : loaded ? !entry.getKey().getDefault() : entry.getKey().getDefault();
            assertEquals(expected, entry.getValue().getAsBoolean(), String.join(".", entry.getKey().getPath()));
        }
    }

    private static void assertLimitsEqual(EndgameSettings expected, EndgameSettings actual) {
        assertEquals(expected.intentIntervalTicks(), actual.intentIntervalTicks());
        assertEquals(expected.selectionIntervalTicks(), actual.selectionIntervalTicks());
        assertEquals(expected.endpointsGlobal(), actual.endpointsGlobal());
        assertEquals(expected.endpointsPerOwner(), actual.endpointsPerOwner());
        assertEquals(expected.zones(), actual.zones());
    }
}
