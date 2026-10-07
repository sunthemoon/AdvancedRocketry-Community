package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        // the three ADR-049 survey scan limits, the ten ADR-050 registry limits, the three ADR-051/052
        // resource mission values, the eleven ADR-054 framework values, the seven ADR-055 laser drill values,
        // the five ADR-058 gravity field caps and the three ADR-057 black-hole generator values, and the v1.8
        // Classic settings: C15a/b/c (ADR-063), combustion, tank capacity and pump operation (ADR-064),
        // the classic life-support instrument switch (ADR-066), and the single solar generator's
        // enable switch and output multiplier (ADR-065), plus the accepted C18a living-tick gravity switch.
        assertSame(CommonConfig.CLASSIC_GRAVITY_ENABLED,
                CommonConfig.SPEC.getValues().get("environment.classicGravityEnabled"));
        assertEquals(73, countValues(CommonConfig.SPEC.getValues()));
    }

    @Test
    void solarGeneratorExposesItsExactEnableAndOutputBounds() {
        ForgeConfigSpec.ValueSpec enabled = assertInstanceOf(ForgeConfigSpec.ValueSpec.class,
                CommonConfig.SPEC.getSpec().get("machines.solarGeneratorEnabled"));
        assertTrue(enabled.test(true));
        assertTrue(enabled.test(false));
        assertTrue(CommonConfig.SOLAR_GENERATOR_ENABLED.getDefault());
        assertTrue(CommonConfig.solarGeneratorEnabled());
        assertTrue(CommonConfig.serverSwitch(CommonConfig.SOLAR_GENERATOR_ENABLED));
        assertRangeAndDefault("energy.solarGeneratorMultiplier", CommonConfig.SOLAR_GENERATOR_MULTIPLIER, 1, 4, 1);
        assertEquals(1, CommonConfig.solarGeneratorMultiplier());
    }

    @Test
    void tankAndPumpExposeOnlyTheirAcceptedDefaultsAndBounds() {
        ForgeConfigSpec.ValueSpec tank = assertInstanceOf(ForgeConfigSpec.ValueSpec.class,
                CommonConfig.SPEC.getSpec().get("machines.tankCapacityMultiplier"));
        ForgeConfigSpec.Range<Double> range = tank.getRange();
        assertEquals(0.25, range.getMin());
        assertEquals(4.0, range.getMax());
        assertEquals(1.0, CommonConfig.TANK_CAPACITY_MULTIPLIER.getDefault());
        assertEquals(1.0, CommonConfig.tankCapacityMultiplier());
        assertTrue(tank.test(0.25));
        assertTrue(tank.test(4.0));
        assertFalse(tank.test(0.249));
        assertFalse(tank.test(4.001));
        assertInstanceOf(ForgeConfigSpec.ValueSpec.class,
                CommonConfig.SPEC.getSpec().get("machines.pumpEnabled"));
        assertEquals(Boolean.TRUE, CommonConfig.PUMP_ENABLED.getDefault());
        assertTrue(CommonConfig.pumpEnabled());
    }

    @Test
    void endgameFrameworkValuesHaveTheirContractDefaultsAndRanges() {
        assertEquals(Boolean.TRUE, CommonConfig.ENDGAME_LASER_DRILL.getDefault());
        assertEquals(Boolean.FALSE, CommonConfig.ENDGAME_LASER_PHYSICAL.getDefault(), "physical mining is opt-in");
        assertEquals(Boolean.TRUE, CommonConfig.ENDGAME_RAILGUN.getDefault());
        assertEquals(Boolean.TRUE, CommonConfig.ENDGAME_BLACK_HOLE_GENERATOR.getDefault());
        assertEquals(Boolean.TRUE, CommonConfig.ENDGAME_GRAVITY_FIELD.getDefault());
        assertEquals(Boolean.TRUE, CommonConfig.ENDGAME_SPACE_ELEVATOR.getDefault());
        assertRangeAndDefault("endgame.intentIntervalTicks", CommonConfig.ENDGAME_INTENT_INTERVAL_TICKS, 10, 200, 10);
        assertRangeAndDefault("endgame.selectionIntervalTicks", CommonConfig.ENDGAME_SELECTION_INTERVAL_TICKS, 2, 200,
                2);
        assertRange("endgame.endpointsGlobal", CommonConfig.ENDGAME_ENDPOINTS_GLOBAL, 1, 2048);
        assertRange("endgame.transitRecords", CommonConfig.ENDGAME_TRANSIT_RECORDS, 1, 256);
        assertRange("endgame.transitPerOwner", CommonConfig.ENDGAME_TRANSIT_PER_OWNER, 1, 32);
        assertRange("endgame.endpointsPerOwner", CommonConfig.ENDGAME_ENDPOINTS_PER_OWNER, 1, 64);
        assertRange("endgame.zones", CommonConfig.ENDGAME_ZONES, 1, 256);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings.DEFAULTS,
                CommonConfig.endgameSettings(), "the defaults apply until the config loads");
    }

    @Test
    void laserDrillValuesHaveTheirContractDefaultsAndRanges() {
        assertRangeAndDefault("endgame.laserDrill.energyPercent", CommonConfig.LASER_DRILL_ENERGY_PERCENT, 10, 1_000,
                100);
        assertRangeAndDefault("endgame.laserDrill.operationIntervalTicks",
                CommonConfig.LASER_DRILL_OPERATION_INTERVAL_TICKS, 20, 1_200, 20);
        assertRangeAndDefault("endgame.laserDrill.maxDepth", CommonConfig.LASER_DRILL_MAX_DEPTH, 1, 256, 64);
        assertRange("endgame.laserDrill.activeGlobal", CommonConfig.LASER_DRILL_ACTIVE_GLOBAL, 1, 32);
        assertRange("endgame.laserDrill.activePerOwner", CommonConfig.LASER_DRILL_ACTIVE_PER_OWNER, 1, 4);
        assertRange("endgame.laserDrill.logicalOperationsPerTick", CommonConfig.LASER_DRILL_LOGICAL_OPERATIONS_PER_TICK,
                1, 32);
        assertRange("endgame.laserDrill.layersPerTick", CommonConfig.LASER_DRILL_LAYERS_PER_TICK, 1, 7);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillSettings.DEFAULTS,
                CommonConfig.laserDrillSettings(), "the defaults apply until the config loads");
    }

    @Test
    void blackHoleValuesHaveTheirContractRanges() {
        assertRangeAndDefault("endgame.blackHoleGenerator.energyPercent", CommonConfig.BLACK_HOLE_ENERGY_PERCENT, 10,
                400, 100);
        assertRange("endgame.blackHoleGenerator.activePerOwner", CommonConfig.BLACK_HOLE_ACTIVE_PER_OWNER, 1, 4);
        assertRange("endgame.blackHoleGenerator.activeGlobal", CommonConfig.BLACK_HOLE_ACTIVE_GLOBAL, 1, 64);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleSettings.DEFAULTS,
                CommonConfig.blackHoleSettings(), "the defaults apply until the config loads");
    }

    @Test
    void elevatorValuesHaveTheirContractRanges() {
        assertRangeAndDefault("endgame.spaceElevator.energyPercent", CommonConfig.ELEVATOR_ENERGY_PERCENT, 10, 1000,
                100);
        assertRange("endgame.spaceElevator.launchesPerTick", CommonConfig.ELEVATOR_LAUNCHES_PER_TICK, 1, 4);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorSettings.DEFAULTS,
                CommonConfig.elevatorSettings(), "the defaults apply until the config loads");
    }

    @Test
    void railgunValuesHaveTheirContractRanges() {
        assertRangeAndDefault("endgame.railgun.energyPercent", CommonConfig.RAILGUN_ENERGY_PERCENT, 10, 400, 100);
        assertRange("endgame.railgun.launchesPerTick", CommonConfig.RAILGUN_LAUNCHES_PER_TICK, 1, 4);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunSettings.DEFAULTS,
                CommonConfig.railgunSettings(), "the defaults apply until the config loads");
    }

    @Test
    void gravityFieldCapsCanOnlyBeLowered() {
        assertRange("endgame.gravityField.perChunk", CommonConfig.GRAVITY_FIELDS_PER_CHUNK, 1, 16);
        assertRange("endgame.gravityField.perOwnerPerChunk", CommonConfig.GRAVITY_FIELDS_PER_OWNER_PER_CHUNK, 1, 4);
        assertRange("endgame.gravityField.activePerOwner", CommonConfig.GRAVITY_FIELDS_ACTIVE_PER_OWNER, 1, 8);
        assertRange("endgame.gravityField.activePerLevel", CommonConfig.GRAVITY_FIELDS_ACTIVE_PER_LEVEL, 1, 256);
        assertRange("endgame.gravityField.activeGlobal", CommonConfig.GRAVITY_FIELDS_ACTIVE_GLOBAL, 1, 1024);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldLimits.DEFAULTS,
                CommonConfig.gravityFieldLimits(), "the defaults apply until the config loads");
    }

    @Test
    void resourceMissionValuesHaveTheirContractRanges() {
        assertRangeAndDefault("satellites.asteroidInstanceTtlTicks", CommonConfig.ASTEROID_INSTANCE_TTL_TICKS, 24_000,
                1_728_000, 168_000);
        assertRangeAndDefault("satellites.asteroidMissionTimePercent", CommonConfig.ASTEROID_MISSION_TIME_PERCENT, 10,
                1_000, 100);
        assertRangeAndDefault("satellites.gasMissionTimePercent", CommonConfig.GAS_MISSION_TIME_PERCENT, 10, 1_000, 100);
        assertEquals(168_000, CommonConfig.asteroidInstanceTtlTicks(), "the default applies until the config loads");
        assertEquals(100, CommonConfig.asteroidMissionTimePercent());
        assertEquals(100, CommonConfig.gasMissionTimePercent());
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

    private static void assertRangeAndDefault(String path, ForgeConfigSpec.IntValue value, int minimum, int maximum,
                                              int defaultValue) {
        ForgeConfigSpec.ValueSpec spec = assertInstanceOf(ForgeConfigSpec.ValueSpec.class,
                CommonConfig.SPEC.getSpec().get(path));
        ForgeConfigSpec.Range<Integer> range = spec.getRange();
        assertEquals(minimum, range.getMin());
        assertEquals(maximum, range.getMax());
        assertEquals(defaultValue, value.getDefault());
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
