package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpSettings;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class CommonConfigTest {
    @Test
    void atmosphereSettingsExposeExactSafeDefaultsAndBounds() {
        assertRange(
                "atmosphere.maxVolumeCells",
                CommonConfig.MAX_ATMOSPHERE_VOLUME,
                1,
                AtmosphereLimits.MAX_VOLUME_CELLS
        );
        assertRange(
                "atmosphere.maxInspectionsPerLevelTick",
                CommonConfig.MAX_ATMOSPHERE_INSPECTIONS_PER_TICK,
                1,
                AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK
        );
    }

    @Test
    void obsoleteUnconsumedLifecycleToggleIsNotExposed() {
        assertFalse(CommonConfig.SPEC.getValues().contains("logLifecycleEvents"));
        // Two atmosphere values, the three ADR-044 station warp values and the ADR-041 rev. 2 write spacing.
        assertEquals(6, countValues(CommonConfig.SPEC.getValues()));
    }

    @Test
    void warpSettingsDefaultToEnabledAndCostsCannotBeZero() {
        assertEquals(Boolean.TRUE, CommonConfig.WARP_ENABLED.getDefault());
        for (var entry : java.util.Map.of(
                "stations.warpCostInSystem", CommonConfig.WARP_COST_IN_SYSTEM,
                "stations.warpCostInterstellar", CommonConfig.WARP_COST_INTERSTELLAR).entrySet()) {
            ForgeConfigSpec.ValueSpec spec = assertInstanceOf(
                    ForgeConfigSpec.ValueSpec.class, CommonConfig.SPEC.getSpec().get(entry.getKey()));
            ForgeConfigSpec.Range<Integer> range = spec.getRange();
            assertEquals(StationLimits.MIN_WARP_COST, range.getMin());
            assertEquals(StationLimits.MAX_WARP_COST, range.getMax());
            assertFalse(spec.test(0));
            assertFalse(spec.test(StationLimits.MIN_WARP_COST - 1));
            assertFalse(spec.test(StationLimits.MAX_WARP_COST + 1));
        }
        assertEquals(2_000_000, CommonConfig.WARP_COST_IN_SYSTEM.getDefault());
        assertEquals(8_000_000, CommonConfig.WARP_COST_INTERSTELLAR.getDefault());
        assertEquals(WarpSettings.DEFAULTS, CommonConfig.warpSettings(), "Defaults apply until the config loads");
    }

    @Test
    void checkedWriteSpacingDefaultsToThreeTicksPerHundredStations() {
        ForgeConfigSpec.ValueSpec spec = assertInstanceOf(ForgeConfigSpec.ValueSpec.class,
                CommonConfig.SPEC.getSpec().get("stations.checkedWriteTicksPer100Stations"));
        ForgeConfigSpec.Range<Integer> range = spec.getRange();
        assertEquals(0, range.getMin());
        assertEquals(100, range.getMax());
        assertEquals(3, CommonConfig.CHECKED_WRITE_TICKS_PER_100_STATIONS.getDefault());
        assertEquals(3, CommonConfig.checkedWriteTicksPer100Stations(), "The default applies until the config loads");
        assertFalse(spec.test(-1));
        assertFalse(spec.test(101));
    }

    private static void assertRange(
            String path,
            ForgeConfigSpec.IntValue value,
            int minimum,
            int maximum
    ) {
        ForgeConfigSpec.ValueSpec spec = assertInstanceOf(
                ForgeConfigSpec.ValueSpec.class,
                CommonConfig.SPEC.getSpec().get(path)
        );
        ForgeConfigSpec.Range<Integer> range = spec.getRange();
        assertEquals(minimum, range.getMin());
        assertEquals(maximum, range.getMax());
        assertEquals(maximum, value.getDefault());
        assertTrue(spec.test(minimum));
        assertTrue(spec.test(maximum));
        assertFalse(spec.test(minimum - 1));
        assertFalse(spec.test(maximum + 1));
    }

    private static int countValues(com.electronwill.nightconfig.core.UnmodifiableConfig config) {
        int count = 0;
        for (Object value : config.valueMap().values()) {
            if (value instanceof ForgeConfigSpec.ConfigValue<?>) {
                count++;
            } else if (value instanceof com.electronwill.nightconfig.core.UnmodifiableConfig nested) {
                count += countValues(nested);
            }
        }
        return count;
    }
}
