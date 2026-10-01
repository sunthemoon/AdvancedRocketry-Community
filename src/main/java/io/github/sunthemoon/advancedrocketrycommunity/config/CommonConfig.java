package io.github.sunthemoon.advancedrocketrycommunity.config;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistryLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.scan.ScanSettings;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpSettings;
import net.minecraftforge.common.ForgeConfigSpec;

public final class CommonConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.IntValue MAX_ATMOSPHERE_VOLUME = BUILDER
            .comment(
                    "Maximum traversable cells in one sealed atmosphere volume.",
                    "Lower values reject large rooms sooner; values cannot exceed the hard safety limit."
            )
            .defineInRange(
                    "atmosphere.maxVolumeCells",
                    AtmosphereLimits.MAX_VOLUME_CELLS,
                    1,
                    AtmosphereLimits.MAX_VOLUME_CELLS
            );

    public static final ForgeConfigSpec.IntValue MAX_ATMOSPHERE_INSPECTIONS_PER_TICK = BUILDER
            .comment(
                    "Maximum atmosphere cell inspections per loaded Level per server tick.",
                    "Lower values reduce per-tick work but make room updates take longer."
            )
            .defineInRange(
                    "atmosphere.maxInspectionsPerLevelTick",
                    AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK,
                    1,
                    AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK
            );

    public static final ForgeConfigSpec.BooleanValue WARP_ENABLED = BUILDER
            .comment(
                    "Allow station warp requests, confirmations and commits (ADR-044).",
                    "Charging warp cores, stations and access are unaffected when disabled."
            )
            .define("stations.warpEnabled", true);

    public static final ForgeConfigSpec.IntValue WARP_COST_IN_SYSTEM = BUILDER
            .comment("Forge Energy a station warp costs within one star system.")
            .defineInRange(
                    "stations.warpCostInSystem",
                    WarpSettings.DEFAULTS.inSystemCost(),
                    StationLimits.MIN_WARP_COST,
                    StationLimits.MAX_WARP_COST
            );

    public static final ForgeConfigSpec.IntValue WARP_COST_INTERSTELLAR = BUILDER
            .comment(
                    "Forge Energy a station warp costs between star systems,",
                    "or when leaving an orbit body that is no longer available."
            )
            .defineInRange(
                    "stations.warpCostInterstellar",
                    WarpSettings.DEFAULTS.interstellarCost(),
                    StationLimits.MIN_WARP_COST,
                    StationLimits.MAX_WARP_COST
            );

    public static final ForgeConfigSpec.IntValue CHECKED_WRITE_TICKS_PER_100_STATIONS = BUILDER
            .comment(
                    "Server-wide spacing between checked station writes (expansion, gravity, warp commit),",
                    "in ticks per 100 registry records: each such write rewrites the whole station file.",
                    "3 allows one write about every 6 seconds at 4,096 stations; 0 disables the spacing."
            )
            .defineInRange("stations.checkedWriteTicksPer100Stations", 3, 0, 100);

    public static final ForgeConfigSpec.IntValue SURVEY_SCAN_JOB_LIMIT = BUILDER
            .comment("Survey area scans that may run at once on the server (ADR-049; at most 4).")
            .defineInRange("satellites.surveyScanJobLimit", ScanSettings.MAX_JOBS, 1, ScanSettings.MAX_JOBS);

    public static final ForgeConfigSpec.IntValue SURVEY_SCAN_COOLDOWN_TICKS = BUILDER
            .comment("Ticks a player waits between survey area scans (ADR-049; at least 100).")
            .defineInRange("satellites.surveyScanCooldownTicks", ScanSettings.MIN_COOLDOWN_TICKS,
                    ScanSettings.MIN_COOLDOWN_TICKS, ScanSettings.MAX_COOLDOWN_TICKS);

    public static final ForgeConfigSpec.IntValue SURVEY_SCAN_READS_PER_TICK = BUILDER
            .comment(
                    "Block states one survey scan reads per server tick (ADR-049; at most 16,384).",
                    "Lower values spread a scan over more ticks."
            )
            .defineInRange("satellites.surveyScanReadsPerTick", ScanSettings.MAX_READS_PER_TICK, 1,
                    ScanSettings.MAX_READS_PER_TICK);

    public static final ForgeConfigSpec.IntValue UNFINISHED_MISSIONS_GLOBAL = limit(
            "satellites.unfinishedMissionsGlobal", "Unfinished satellite missions on the server (ADR-050).",
            RegistryLimits.DEFAULTS.unfinishedGlobal());
    public static final ForgeConfigSpec.IntValue UNFINISHED_MISSIONS_PER_OWNER = limit(
            "satellites.unfinishedMissionsPerOwner", "Unfinished satellite missions per owner.",
            RegistryLimits.MAX_UNFINISHED_PER_OWNER);
    public static final ForgeConfigSpec.IntValue FINISHED_MISSIONS_PER_OWNER = limit(
            "satellites.finishedMissionsPerOwner", "Finished missions kept per owner before the oldest are pruned.",
            RegistryLimits.MAX_FINISHED_PER_OWNER);
    public static final ForgeConfigSpec.IntValue MISSIONS_TOTAL = limit(
            "satellites.missionsTotal", "Missions of every status admitted on the server.",
            RegistryLimits.MAX_MISSIONS_TOTAL);
    public static final ForgeConfigSpec.IntValue SATELLITES_GLOBAL = limit(
            "satellites.satellitesGlobal", "Satellites on the server.", RegistryLimits.DEFAULTS.satellitesGlobal());
    public static final ForgeConfigSpec.IntValue SATELLITES_PER_OWNER = limit(
            "satellites.satellitesPerOwner", "Satellites per owner.", RegistryLimits.DEFAULTS.satellitesPerOwner());
    public static final ForgeConfigSpec.IntValue INSTANCES_GLOBAL = limit(
            "satellites.asteroidInstancesGlobal", "Live asteroid instances on the server.",
            RegistryLimits.DEFAULTS.instancesGlobal());
    public static final ForgeConfigSpec.IntValue INSTANCES_PER_OWNER = limit(
            "satellites.asteroidInstancesPerOwner", "Live asteroid instances per owner.",
            RegistryLimits.MAX_INSTANCES_PER_OWNER);
    public static final ForgeConfigSpec.IntValue INTENT_INTERVAL_TICKS = BUILDER
            .comment("Ticks between a player's state-changing satellite intents (start, claim, cancel...; at least 10).")
            .defineInRange("satellites.intentIntervalTicks", RegistryLimits.MIN_INTENT_TICKS,
                    RegistryLimits.MIN_INTENT_TICKS, RegistryLimits.MAX_INTERVAL_TICKS);
    public static final ForgeConfigSpec.IntValue SELECTION_INTERVAL_TICKS = BUILDER
            .comment("Ticks between a player's satellite selection intents (previous/next; at least 2).")
            .defineInRange("satellites.selectionIntervalTicks", RegistryLimits.MIN_SELECTION_TICKS,
                    RegistryLimits.MIN_SELECTION_TICKS, RegistryLimits.MAX_INTERVAL_TICKS);

    public static final ForgeConfigSpec.IntValue ASTEROID_INSTANCE_TTL_TICKS = BUILDER
            .comment("Logical ticks a surveyed asteroid instance stays AVAILABLE (ADR-051; 24,000..1,728,000).")
            .defineInRange("satellites.asteroidInstanceTtlTicks", 168_000, 24_000, 1_728_000);
    public static final ForgeConfigSpec.IntValue ASTEROID_MISSION_TIME_PERCENT = BUILDER
            .comment("Asteroid mission duration in percent of the asteroid-v1 formula (ADR-052; 10..1,000).")
            .defineInRange("satellites.asteroidMissionTimePercent", 100, 10, 1_000);
    public static final ForgeConfigSpec.IntValue GAS_MISSION_TIME_PERCENT = BUILDER
            .comment("Gas mission duration in percent of the gas-v1 base duration (ADR-052; 10..1,000).")
            .defineInRange("satellites.gasMissionTimePercent", 100, 10, 1_000);

    // ADR-054 section 1: per-system switches, read at each use. A disabled system keeps its content and state and
    // still settles and recovers; it only starts nothing new.
    public static final ForgeConfigSpec.BooleanValue ENDGAME_LASER_DRILL = system(
            "endgame.laserDrill.enabled", "Orbital laser drills may operate (ADR-055).", true);
    public static final ForgeConfigSpec.BooleanValue ENDGAME_LASER_PHYSICAL = system(
            "endgame.laserDrill.physicalMining",
            "Laser drills may dig real blocks at owned laser targets (ADR-055 physical mode; off by default).", false);
    public static final ForgeConfigSpec.BooleanValue ENDGAME_RAILGUN = system(
            "endgame.railgun.enabled", "Railguns may launch cargo (ADR-056).", true);
    public static final ForgeConfigSpec.BooleanValue ENDGAME_BLACK_HOLE_GENERATOR = system(
            "endgame.blackHoleGenerator.enabled", "Black-hole generators may burn fuel (ADR-057).", true);
    public static final ForgeConfigSpec.BooleanValue ENDGAME_GRAVITY_FIELD = system(
            "endgame.gravityField.enabled", "Area gravity fields may be active (ADR-058).", true);
    public static final ForgeConfigSpec.BooleanValue ENDGAME_SPACE_ELEVATOR = system(
            "endgame.spaceElevator.enabled", "Space elevators may bind, ride and ship (ADR-059).", true);
    public static final ForgeConfigSpec.IntValue ENDGAME_INTENT_INTERVAL_TICKS = BUILDER
            .comment("Ticks between a player's state-changing endgame intents (ADR-054; at least 10).")
            .defineInRange("endgame.intentIntervalTicks", EndgameLimits.MIN_INTENT_TICKS,
                    EndgameLimits.MIN_INTENT_TICKS, EndgameLimits.MAX_INTERVAL_TICKS);
    public static final ForgeConfigSpec.IntValue ENDGAME_SELECTION_INTERVAL_TICKS = BUILDER
            .comment("Ticks between a player's endgame selection intents (previous/next; at least 2).")
            .defineInRange("endgame.selectionIntervalTicks", EndgameLimits.MIN_SELECTION_TICKS,
                    EndgameLimits.MIN_SELECTION_TICKS, EndgameLimits.MAX_INTERVAL_TICKS);
    public static final ForgeConfigSpec.IntValue ENDGAME_ENDPOINTS_GLOBAL = limit(
            "endgame.endpointsGlobal", "Registered endgame endpoints on the server (ADR-054 section 9).",
            EndgameLimits.MAX_ENDPOINTS);
    public static final ForgeConfigSpec.IntValue ENDGAME_ENDPOINTS_PER_OWNER = limit(
            "endgame.endpointsPerOwner", "Registered endgame endpoints per owner.", EndgameLimits.MAX_ENDPOINTS_PER_OWNER);
    public static final ForgeConfigSpec.IntValue ENDGAME_ZONES = limit(
            "endgame.zones", "Protected zones on the server (ADR-054 section 6).", EndgameLimits.MAX_ZONES);

    // ADR-055 section 4: laser drill budgets.
    public static final ForgeConfigSpec.IntValue LASER_DRILL_ENERGY_PERCENT = BUILDER
            .comment("Energy per laser drill operation in percent of 10,000 FE (ADR-055; 10..1,000).")
            .defineInRange("endgame.laserDrill.energyPercent", LaserDrillSettings.DEFAULTS.energyPercent(),
                    LaserDrillSettings.MIN_ENERGY_PERCENT, LaserDrillSettings.MAX_ENERGY_PERCENT);
    public static final ForgeConfigSpec.IntValue LASER_DRILL_OPERATION_INTERVAL_TICKS = BUILDER
            .comment("Ticks between two operations of one laser drill (ADR-055; 20..1,200).")
            .defineInRange("endgame.laserDrill.operationIntervalTicks", LaserDrillSettings.MIN_INTERVAL_TICKS,
                    LaserDrillSettings.MIN_INTERVAL_TICKS, LaserDrillSettings.MAX_INTERVAL_TICKS);
    public static final ForgeConfigSpec.IntValue LASER_DRILL_MAX_DEPTH = BUILDER
            .comment("Layers a physical laser shaft may dig below its marker (ADR-055; 1..256).")
            .defineInRange("endgame.laserDrill.maxDepth", LaserDrillSettings.DEFAULTS.maxDepth(), 1,
                    LaserDrillSettings.MAX_DEPTH);
    public static final ForgeConfigSpec.IntValue LASER_DRILL_ACTIVE_GLOBAL = limit(
            "endgame.laserDrill.activeGlobal", "Running laser drills on the server (ADR-055).",
            LaserDrillSettings.MAX_ACTIVE_GLOBAL);
    public static final ForgeConfigSpec.IntValue LASER_DRILL_ACTIVE_PER_OWNER = limit(
            "endgame.laserDrill.activePerOwner", "Running laser drills per owner (ADR-055).",
            LaserDrillSettings.MAX_ACTIVE_PER_OWNER);
    public static final ForgeConfigSpec.IntValue LASER_DRILL_LOGICAL_OPERATIONS_PER_TICK = limit(
            "endgame.laserDrill.logicalOperationsPerTick", "Logical laser drill operations per server tick (ADR-055).",
            LaserDrillSettings.MAX_LOGICAL_OPERATIONS_PER_TICK);
    public static final ForgeConfigSpec.IntValue LASER_DRILL_LAYERS_PER_TICK = limit(
            "endgame.laserDrill.layersPerTick", "Physical laser shaft layers per server tick (ADR-055).",
            LaserDrillSettings.MAX_LAYERS_PER_TICK);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** ADR-054 framework settings; the defaults (the limits at their maxima) until the COMMON config is loaded. */
    public static EndgameSettings endgameSettings() {
        if (!SPEC.isLoaded()) {
            return EndgameSettings.DEFAULTS;
        }
        return new EndgameSettings(ENDGAME_LASER_DRILL.get(), ENDGAME_LASER_PHYSICAL.get(), ENDGAME_RAILGUN.get(),
                ENDGAME_BLACK_HOLE_GENERATOR.get(), ENDGAME_GRAVITY_FIELD.get(), ENDGAME_SPACE_ELEVATOR.get(),
                ENDGAME_INTENT_INTERVAL_TICKS.get(), ENDGAME_SELECTION_INTERVAL_TICKS.get(),
                ENDGAME_ENDPOINTS_GLOBAL.get(), ENDGAME_ENDPOINTS_PER_OWNER.get(), ENDGAME_ZONES.get());
    }

    /** ADR-055 section 4 laser drill budgets; the defaults until the COMMON config is loaded. */
    public static LaserDrillSettings laserDrillSettings() {
        if (!SPEC.isLoaded()) {
            return LaserDrillSettings.DEFAULTS;
        }
        return new LaserDrillSettings(LASER_DRILL_ENERGY_PERCENT.get(), LASER_DRILL_OPERATION_INTERVAL_TICKS.get(),
                LASER_DRILL_MAX_DEPTH.get(), LASER_DRILL_ACTIVE_GLOBAL.get(), LASER_DRILL_ACTIVE_PER_OWNER.get(),
                LASER_DRILL_LOGICAL_OPERATIONS_PER_TICK.get(), LASER_DRILL_LAYERS_PER_TICK.get());
    }

    private static ForgeConfigSpec.BooleanValue system(String path, String comment, boolean defaultValue) {
        return BUILDER.comment(comment, "Disabling keeps blocks, items and saved state; settlement continues.")
                .define(path, defaultValue);
    }

    /** ADR-050 section 6 admission limits; the defaults (also the maxima) until the COMMON config is loaded. */
    public static RegistryLimits registryLimits() {
        if (!SPEC.isLoaded()) {
            return RegistryLimits.DEFAULTS;
        }
        return new RegistryLimits(UNFINISHED_MISSIONS_GLOBAL.get(), UNFINISHED_MISSIONS_PER_OWNER.get(),
                FINISHED_MISSIONS_PER_OWNER.get(), MISSIONS_TOTAL.get(), SATELLITES_GLOBAL.get(),
                SATELLITES_PER_OWNER.get(), INSTANCES_GLOBAL.get(), INSTANCES_PER_OWNER.get(),
                INTENT_INTERVAL_TICKS.get(), SELECTION_INTERVAL_TICKS.get());
    }

    private static ForgeConfigSpec.IntValue limit(String path, String comment, int maximum) {
        return BUILDER.comment(comment, "Can only be lowered; the default is the hard maximum.")
                .defineInRange(path, maximum, 1, maximum);
    }

    /** Current survey scan limits; the defaults (also the maxima) until the COMMON config is loaded. */
    public static ScanSettings surveyScanSettings() {
        if (!SPEC.isLoaded()) {
            return ScanSettings.DEFAULTS;
        }
        return new ScanSettings(SURVEY_SCAN_JOB_LIMIT.get(), SURVEY_SCAN_COOLDOWN_TICKS.get(),
                SURVEY_SCAN_READS_PER_TICK.get());
    }

    /** ADR-051/052 resource mission settings; the defaults until the COMMON config is loaded. */
    public static int asteroidInstanceTtlTicks() {
        return SPEC.isLoaded() ? ASTEROID_INSTANCE_TTL_TICKS.get() : ASTEROID_INSTANCE_TTL_TICKS.getDefault();
    }

    public static int asteroidMissionTimePercent() {
        return SPEC.isLoaded() ? ASTEROID_MISSION_TIME_PERCENT.get() : ASTEROID_MISSION_TIME_PERCENT.getDefault();
    }

    public static int gasMissionTimePercent() {
        return SPEC.isLoaded() ? GAS_MISSION_TIME_PERCENT.get() : GAS_MISSION_TIME_PERCENT.getDefault();
    }

    /** Current warp settings; defaults until the COMMON config is loaded. */
    public static WarpSettings warpSettings() {
        if (!SPEC.isLoaded()) {
            return WarpSettings.DEFAULTS;
        }
        return new WarpSettings(WARP_ENABLED.get(), WARP_COST_IN_SYSTEM.get(), WARP_COST_INTERSTELLAR.get());
    }

    /** Current checked-write spacing; the default until the COMMON config is loaded. */
    public static int checkedWriteTicksPer100Stations() {
        return SPEC.isLoaded() ? CHECKED_WRITE_TICKS_PER_100_STATIONS.get()
                : CHECKED_WRITE_TICKS_PER_100_STATIONS.getDefault();
    }

    private CommonConfig() {
    }
}
