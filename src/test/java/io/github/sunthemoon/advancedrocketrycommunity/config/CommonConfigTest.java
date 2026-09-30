package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistryLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.scan.ScanSettings;
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
        // Two atmosphere values, the three ADR-044 station warp values, the ADR-041 rev. 2 write spacing,
        // the three ADR-049 survey scan limits and the ten ADR-050 registry limits.
        assertEquals(19, countValues(CommonConfig.SPEC.getValues()));
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
    void surveyScanLimitsDefaultToTheirHardMaximaAndCannotBeLoosened() {
        assertRange("satellites.surveyScanJobLimit", CommonConfig.SURVEY_SCAN_JOB_LIMIT, 1, 4);
        assertRange("satellites.surveyScanReadsPerTick", CommonConfig.SURVEY_SCAN_READS_PER_TICK, 1, 16_384);
        ForgeConfigSpec.ValueSpec cooldown = assertInstanceOf(ForgeConfigSpec.ValueSpec.class,
                CommonConfig.SPEC.getSpec().get("satellites.surveyScanCooldownTicks"));
        ForgeConfigSpec.Range<Integer> range = cooldown.getRange();
        assertEquals(100, range.getMin());
        assertEquals(72_000, range.getMax());
        assertFalse(cooldown.test(99));
        assertEquals(4, CommonConfig.SURVEY_SCAN_JOB_LIMIT.getDefault());
        assertEquals(100, CommonConfig.SURVEY_SCAN_COOLDOWN_TICKS.getDefault());
        assertEquals(16_384, CommonConfig.SURVEY_SCAN_READS_PER_TICK.getDefault());
        assertEquals(ScanSettings.DEFAULTS, CommonConfig.surveyScanSettings(), "Defaults apply until the config loads");
    }

    @Test
    void registryLimitsDefaultToTheirHardMaximaAndIntervalsCanOnlyGrow() {
        assertRange("satellites.unfinishedMissionsGlobal", CommonConfig.UNFINISHED_MISSIONS_GLOBAL, 1, 1_024);
        assertRange("satellites.unfinishedMissionsPerOwner", CommonConfig.UNFINISHED_MISSIONS_PER_OWNER, 1, 64);
        assertRange("satellites.finishedMissionsPerOwner", CommonConfig.FINISHED_MISSIONS_PER_OWNER, 1, 128);
        assertRange("satellites.missionsTotal", CommonConfig.MISSIONS_TOTAL, 1, 3_072);
        assertRange("satellites.satellitesGlobal", CommonConfig.SATELLITES_GLOBAL, 1, 4_096);
        assertRange("satellites.satellitesPerOwner", CommonConfig.SATELLITES_PER_OWNER, 1, 256);
        assertRange("satellites.asteroidInstancesGlobal", CommonConfig.INSTANCES_GLOBAL, 1, 2_048);
        assertRange("satellites.asteroidInstancesPerOwner", CommonConfig.INSTANCES_PER_OWNER, 1, 16);
        for (var entry : java.util.Map.of(
                "satellites.intentIntervalTicks", 10,
                "satellites.selectionIntervalTicks", 2).entrySet()) {
            ForgeConfigSpec.ValueSpec spec = assertInstanceOf(ForgeConfigSpec.ValueSpec.class,
                    CommonConfig.SPEC.getSpec().get(entry.getKey()));
            ForgeConfigSpec.Range<Integer> range = spec.getRange();
            assertEquals(entry.getValue(), range.getMin());
            assertEquals(1_200, range.getMax());
            assertFalse(spec.test(entry.getValue() - 1));
        }
        assertEquals(10, CommonConfig.INTENT_INTERVAL_TICKS.getDefault());
        assertEquals(2, CommonConfig.SELECTION_INTERVAL_TICKS.getDefault());
        assertEquals(RegistryLimits.DEFAULTS, CommonConfig.registryLimits(), "Defaults apply until the config loads");
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
