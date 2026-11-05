package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** File-backed COMMON loading without a watcher, modeling a missed file notification deterministically. */
final class CommonConfigReloadTest {
    private static final List<ForgeConfigSpec.BooleanValue> SWITCHES = List.of(
            CommonConfig.ENDGAME_LASER_DRILL, CommonConfig.ENDGAME_LASER_PHYSICAL,
            CommonConfig.ENDGAME_RAILGUN, CommonConfig.ENDGAME_BLACK_HOLE_GENERATOR,
            CommonConfig.ENDGAME_GRAVITY_FIELD, CommonConfig.ENDGAME_SPACE_ELEVATOR);

    @TempDir
    Path directory;

    @AfterEach
    void unload() {
        CommonConfig.SPEC.setConfig(null);
    }

    @Test
    void fileEditsAndCacheClearingAloneDoNotReloadTheLoadedConfiguration() throws IOException {
        Path path = directory.resolve("advancedrocketrycommunity-common.toml");
        try (CommentedFileConfig loaded = loadDefaults(path)) {
            assertSwitches(false);
            invertFile(path);
            assertSwitches(false);
            CommonConfig.SPEC.afterReload();
            assertSwitches(false);
            try (CommentedFileConfig disk = CommentedFileConfig.builder(path).sync().build()) {
                disk.load();
                for (var value : SWITCHES) {
                    assertEquals(!value.getDefault(), disk.<Boolean>get(value.getPath()).booleanValue(),
                            "the edit reached disk");
                }
            }
        }
    }

    @Test
    void successfulForgeReloadNeedsBothFileLoadingAndCacheInvalidation() throws IOException {
        Path path = directory.resolve("advancedrocketrycommunity-common.toml");
        try (CommentedFileConfig loaded = loadDefaults(path)) {
            assertSwitches(false); // Populate every Forge BooleanValue cache before the edit.
            invertFile(path);
            loaded.load();
            assertSwitches(false);
            CommonConfig.SPEC.afterReload();
            assertSwitches(true);
        }
    }

    @Test
    void freshStartupLoadsEveryEditedSwitchEvenIfNoWatchNotificationArrived() throws IOException {
        Path path = directory.resolve("advancedrocketrycommunity-common.toml");
        try (CommentedFileConfig loaded = loadDefaults(path)) {
            assertSwitches(false);
            invertFile(path);
            assertSwitches(false);
            CommonConfig.SPEC.setConfig(null);
            assertFalse(CommonConfig.SPEC.isLoaded());
        }
        try (CommentedFileConfig restarted = CommentedFileConfig.builder(path).sync().build()) {
            restarted.load();
            CommonConfig.SPEC.setConfig(restarted);
            assertTrue(CommonConfig.SPEC.isLoaded());
            assertSwitches(true);
        }
    }

    private static CommentedFileConfig loadDefaults(Path path) {
        CommentedFileConfig config = CommentedFileConfig.builder(path).sync().build();
        CommonConfig.SPEC.correct(config);
        config.save();
        CommonConfig.SPEC.setConfig(config);
        return config;
    }

    private static void invertFile(Path path) throws IOException {
        try (CommentedFileConfig edited = CommentedFileConfig.builder(path).sync().build()) {
            edited.load();
            SWITCHES.forEach(value -> edited.set(value.getPath(), !value.getDefault()));
            edited.save();
        }
        assertTrue(Files.size(path) > 0);
    }

    private static void assertSwitches(boolean inverted) {
        var settings = CommonConfig.endgameSettings();
        assertEquals(!inverted, settings.laserDrill());
        assertEquals(inverted, settings.laserPhysicalMining());
        assertEquals(!inverted, settings.railgun());
        assertEquals(!inverted, settings.blackHoleGenerator());
        assertEquals(!inverted, settings.gravityField());
        assertEquals(!inverted, settings.spaceElevator());
    }
}
