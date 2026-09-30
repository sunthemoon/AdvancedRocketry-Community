package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpSettings;
import org.junit.jupiter.api.Test;

/**
 * Final v1.5 review A2: the loaded COMMON config reaches the warp settings and the checked-write
 * spacing. Uses an in-memory config: no file, so no Forge file watcher can race a change.
 */
final class CommonConfigWiringTest {
    @Test
    void loadedValuesReachTheWarpSettingsAndTheWriteSpacing() {
        CommonConfig.SPEC.setConfig(CommentedConfig.inMemory());
        try {
            assertTrue(CommonConfig.SPEC.isLoaded());
            assertEquals(WarpSettings.DEFAULTS, CommonConfig.warpSettings(), "A corrected config holds the defaults");
            CommonConfig.WARP_ENABLED.set(false);
            assertFalse(CommonConfig.warpSettings().enabled(), "The kill switch does not reach the warp settings");
            CommonConfig.WARP_COST_IN_SYSTEM.set(3_000_000);
            CommonConfig.WARP_COST_INTERSTELLAR.set(9_000_000);
            assertEquals(new WarpSettings(false, 3_000_000, 9_000_000), CommonConfig.warpSettings());
            CommonConfig.CHECKED_WRITE_TICKS_PER_100_STATIONS.set(7);
            assertEquals(7, CommonConfig.checkedWriteTicksPer100Stations());
        } finally {
            CommonConfig.SPEC.setConfig(null);
        }
        assertFalse(CommonConfig.SPEC.isLoaded());
        assertEquals(WarpSettings.DEFAULTS, CommonConfig.warpSettings(), "Defaults apply again once unloaded");
        assertEquals(3, CommonConfig.checkedWriteTicksPer100Stations());
    }
}
