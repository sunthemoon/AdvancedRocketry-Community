package io.github.sunthemoon.advancedrocketrycommunity.config;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RegistryLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.scan.ScanSettings;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpSettings;
import java.util.Set;
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
    public static final ForgeConfigSpec.IntValue ENDGAME_TRANSIT_RECORDS = limit(
            "endgame.transitRecords", "Transit records on the server, stubs and known outbox entries included "
                    + "(ADR-054 section 11).", EndgameLimits.MAX_TRANSIT_RECORDS);
    public static final ForgeConfigSpec.IntValue ENDGAME_TRANSIT_PER_OWNER = limit(
            "endgame.transitPerOwner", "Live transit records and outbox entries per owner.",
            TransitLimits.MAX_PER_OWNER);

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

    // ADR-058 section 4: area gravity field caps.
    public static final ForgeConfigSpec.IntValue GRAVITY_FIELDS_PER_CHUNK = limit(
            "endgame.gravityField.perChunk", "Active gravity fields listed in one chunk (ADR-058).",
            GravityFieldLimits.MAX_PER_CHUNK);
    public static final ForgeConfigSpec.IntValue GRAVITY_FIELDS_PER_OWNER_PER_CHUNK = limit(
            "endgame.gravityField.perOwnerPerChunk", "Active gravity fields of one owner in one chunk (ADR-058).",
            GravityFieldLimits.MAX_PER_OWNER_PER_CHUNK);
    public static final ForgeConfigSpec.IntValue GRAVITY_FIELDS_ACTIVE_PER_OWNER = limit(
            "endgame.gravityField.activePerOwner", "Active gravity fields per owner (ADR-058).",
            GravityFieldLimits.MAX_ACTIVE_PER_OWNER);
    public static final ForgeConfigSpec.IntValue GRAVITY_FIELDS_ACTIVE_PER_LEVEL = limit(
            "endgame.gravityField.activePerLevel", "Active gravity fields per Level (ADR-058).",
            GravityFieldLimits.MAX_ACTIVE_PER_LEVEL);
    public static final ForgeConfigSpec.IntValue GRAVITY_FIELDS_ACTIVE_GLOBAL = limit(
            "endgame.gravityField.activeGlobal", "Active gravity fields on the server (ADR-058).",
            GravityFieldLimits.MAX_ACTIVE_GLOBAL);

    // ADR-057 sections 4 and 5: black-hole generator output and limits.
    public static final ForgeConfigSpec.IntValue BLACK_HOLE_ENERGY_PERCENT = BUILDER
            .comment("Black-hole generator output in percent of its singularity's rate (ADR-057; 10..400).")
            .defineInRange("endgame.blackHoleGenerator.energyPercent", 100, BlackHoleSettings.MIN_PERCENT,
                    BlackHoleSettings.MAX_PERCENT);
    public static final ForgeConfigSpec.IntValue BLACK_HOLE_ACTIVE_PER_OWNER = limit(
            "endgame.blackHoleGenerator.activePerOwner", "Burning black-hole generators per owner (ADR-057).",
            BlackHoleSettings.MAX_ACTIVE_PER_OWNER);
    public static final ForgeConfigSpec.IntValue BLACK_HOLE_ACTIVE_GLOBAL = limit(
            "endgame.blackHoleGenerator.activeGlobal", "Burning black-hole generators on the server (ADR-057).",
            BlackHoleSettings.MAX_ACTIVE_GLOBAL);

    // ADR-056 sections 3 and 4: railgun launch cost and the server's launches per tick.
    public static final ForgeConfigSpec.IntValue RAILGUN_ENERGY_PERCENT = BUILDER
            .comment("Railgun launch cost in percent of the route class's cost (ADR-056; 10..400).")
            .defineInRange("endgame.railgun.energyPercent", 100, RailgunSettings.MIN_PERCENT,
                    RailgunSettings.MAX_PERCENT);
    public static final ForgeConfigSpec.IntValue RAILGUN_LAUNCHES_PER_TICK = limit(
            "endgame.railgun.launchesPerTick", "Railgun launches the server starts in one tick (ADR-056).",
            RailgunSettings.MAX_LAUNCHES_PER_TICK);

    // ADR-059 sections 6 and 8: elevator cargo and ride costs, and the cargo launches per tick.
    public static final ForgeConfigSpec.IntValue ELEVATOR_ENERGY_PERCENT = BUILDER
            .comment("Space elevator cargo (20,000 FE) and ride (50,000 FE) costs in percent (ADR-059; 10..1000).")
            .defineInRange("endgame.spaceElevator.energyPercent", 100, ElevatorRules.MIN_PERCENT,
                    ElevatorRules.MAX_PERCENT);
    public static final ForgeConfigSpec.IntValue ELEVATOR_LAUNCHES_PER_TICK = limit(
            "endgame.spaceElevator.launchesPerTick", "Elevator cargo launches the server starts in one tick (ADR-059).",
            ElevatorSettings.MAX_LAUNCHES_PER_TICK);

    // ADR-061 section 3.5 and ADR-063: server switches for classic content. Disabling keeps blocks, items and
    // already generated chunks; it stops the press acting and the features generating in new chunks.
    public static final ForgeConfigSpec.BooleanValue SMALL_PLATE_PRESS_ENABLED = BUILDER
            .comment("Let the small plate press act on a rising redstone edge (ADR-063 section 3).",
                    "Disabling keeps the blocks; a powered press then does nothing.")
            .define("classic.smallPlatePress", true);
    public static final ForgeConfigSpec.BooleanValue OVERWORLD_ORES_ENABLED = BUILDER
            .comment("Generate the classic tin, rutile, aluminum and dilithium ores in new Overworld chunks",
                    "(ADR-063 section 4). Chunks already generated keep their ores.")
            .define("worldgen.overworldOres", true);
    public static final ForgeConfigSpec.BooleanValue PLANET_ORES_ENABLED = BUILDER
            .comment("Generate the classic ores on the Moon and Mars in new chunks (ADR-063 section 4).",
                    "Chunks already generated keep their ores.")
            .define("worldgen.planetOres", true);
    public static final ForgeConfigSpec.BooleanValue CRATERS_ENABLED = BUILDER
            .comment("Start impact craters on the Moon and Mars in new chunks (ADR-063 section 5).",
                    "Craters already started still finish.")
            .define("worldgen.craters", true);
    public static final ForgeConfigSpec.BooleanValue VOLCANOES_ENABLED = BUILDER
            .comment("Start volcanoes on Venus in new chunks (ADR-063 section 5).",
                    "Volcanoes already started still finish.")
            .define("worldgen.volcanoes", true);
    public static final ForgeConfigSpec.BooleanValue GEODES_ENABLED = BUILDER
            .comment("Start ore geodes on Venus in new chunks (ADR-063 section 5).",
                    "Geodes already started still finish.")
            .define("worldgen.geodes", true);
    public static final ForgeConfigSpec.BooleanValue CHARRED_TREES_ENABLED = BUILDER
            .comment("Place charred trees on Venus and in the Tau Ceti g stormland in new chunks",
                    "(ADR-063 sections 5 and 6).")
            .define("worldgen.charredTrees", true);
    public static final ForgeConfigSpec.BooleanValue LIGHTWOOD_TREES_ENABLED = BUILDER
            .comment("Place lightwood trees in the Tau Ceti f alien forest in new chunks (ADR-063 section 6).",
                    "Saplings still grow.")
            .define("worldgen.lightwoodTrees", true);
    public static final ForgeConfigSpec.BooleanValue SWAMP_TREES_ENABLED = BUILDER
            .comment("Place giant swamp trees in the Tau Ceti f deep swamp in new chunks (ADR-063 section 6).")
            .define("worldgen.swampTrees", true);
    public static final ForgeConfigSpec.BooleanValue INVERTED_PILLARS_ENABLED = BUILDER
            .comment("Place inverted pillars in the Tau Ceti f ocean spires in new chunks (ADR-063 section 6).")
            .define("worldgen.invertedPillars", true);
    public static final ForgeConfigSpec.BooleanValue CRYSTAL_CLUSTERS_ENABLED = BUILDER
            .comment("Place crystal clusters in the Tau Ceti g crystal chasms in new chunks (ADR-063 section 6).")
            .define("worldgen.crystalClusters", true);
    public static final ForgeConfigSpec.BooleanValue ELECTRIC_MUSHROOMS_ENABLED = BUILDER
            .comment("Place electric mushrooms in the Tau Ceti g stormland in new chunks (ADR-063 section 6).")
            .define("worldgen.electricMushrooms", true);

    public static final ForgeConfigSpec.BooleanValue COMBUSTION_GENERATOR_ENABLED = BUILDER
            .comment("Allow combustion generation and FE export; disabling retains blocks, fuel and burn credit.")
            .define("machines.combustionGeneratorEnabled", true);

    public static final ForgeConfigSpec.BooleanValue SOLAR_GENERATOR_ENABLED = BUILDER
            .comment("Allow solar generation and FE export; disabling retains blocks and stored energy.")
            .define("machines.solarGeneratorEnabled", true);
    public static final ForgeConfigSpec.IntValue SOLAR_GENERATOR_MULTIPLIER = BUILDER
            .comment("Solar generator output multiplier; does not change stored energy capacity.")
            .defineInRange("energy.solarGeneratorMultiplier", 1, 1, 4);

    /** ADR-064 section 6: reducing capacity retains existing overflow. */
    public static final ForgeConfigSpec.DoubleValue TANK_CAPACITY_MULTIPLIER = BUILDER
            .comment("Pressurized tank capacity multiplier; overflow remains drainable and refuses further fills.")
            .defineInRange("machines.tankCapacityMultiplier", 1.0, 0.25, 4.0);

    public static final ForgeConfigSpec.BooleanValue PUMP_ENABLED = BUILDER
            .comment("Allow pump source draining and fluid export; disabling retains resources and owner.")
            .define("machines.pumpEnabled", true);

    public static final ForgeConfigSpec.BooleanValue CLASSIC_DEVICES_ENABLED = BUILDER
            .comment("Allow classic life-support instruments; disabling retains their items and recipes.")
            .define("lifeSupport.classicDevicesEnabled", true);

    public static final ForgeConfigSpec.BooleanValue CLASSIC_GRAVITY_ENABLED = BUILDER
            .comment("Apply Level gravity to non-player living entities; disabling removes only the owned modifier.",
                    "Existing player field, station and Level gravity remain enabled.")
            .define("environment.classicGravityEnabled", true);

    public static final ForgeConfigSpec.BooleanValue CLASSIC_EQUIPMENT_ENABLED = BUILDER
            .comment("Allow classic equipment use; disabling retains items, recipes and safe repair.")
            .define("equipment.classicEnabled", true);

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

    /** ADR-054 section 11 transit limits; the maxima until the COMMON config is loaded. */
    public static TransitLimits transitLimits() {
        if (!SPEC.isLoaded()) {
            return TransitLimits.DEFAULTS;
        }
        return new TransitLimits(ENDGAME_TRANSIT_RECORDS.get(), ENDGAME_TRANSIT_PER_OWNER.get());
    }

    /** ADR-057 black-hole generator settings; the defaults until the COMMON config is loaded. */
    public static BlackHoleSettings blackHoleSettings() {
        if (!SPEC.isLoaded()) {
            return BlackHoleSettings.DEFAULTS;
        }
        return new BlackHoleSettings(BLACK_HOLE_ENERGY_PERCENT.get(), BLACK_HOLE_ACTIVE_PER_OWNER.get(),
                BLACK_HOLE_ACTIVE_GLOBAL.get());
    }

    /** ADR-056 railgun settings; the defaults until the COMMON config is loaded. */
    public static RailgunSettings railgunSettings() {
        if (!SPEC.isLoaded()) {
            return RailgunSettings.DEFAULTS;
        }
        return new RailgunSettings(RAILGUN_ENERGY_PERCENT.get(), RAILGUN_LAUNCHES_PER_TICK.get());
    }

    /** ADR-059 elevator settings; the defaults until the COMMON config is loaded. */
    public static ElevatorSettings elevatorSettings() {
        if (!SPEC.isLoaded()) {
            return ElevatorSettings.DEFAULTS;
        }
        return new ElevatorSettings(ELEVATOR_ENERGY_PERCENT.get(), ELEVATOR_LAUNCHES_PER_TICK.get());
    }

    /** ADR-058 section 4 gravity field caps; the defaults until the COMMON config is loaded. */
    public static GravityFieldLimits gravityFieldLimits() {
        if (!SPEC.isLoaded()) {
            return GravityFieldLimits.DEFAULTS;
        }
        return new GravityFieldLimits(GRAVITY_FIELDS_PER_CHUNK.get(), GRAVITY_FIELDS_PER_OWNER_PER_CHUNK.get(),
                GRAVITY_FIELDS_ACTIVE_PER_OWNER.get(), GRAVITY_FIELDS_ACTIVE_PER_LEVEL.get(),
                GRAVITY_FIELDS_ACTIVE_GLOBAL.get());
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

    /** ADR-063 section 3: whether the small plate press acts; the default until the COMMON config is loaded. */
    /** Server switches whose tests can hold an ephemeral in-memory value. */
    private static final Set<ForgeConfigSpec.BooleanValue> SERVER_SWITCHES = Set.of(SMALL_PLATE_PRESS_ENABLED,
            COMBUSTION_GENERATOR_ENABLED, SOLAR_GENERATOR_ENABLED, PUMP_ENABLED, CLASSIC_DEVICES_ENABLED,
            CLASSIC_GRAVITY_ENABLED, CLASSIC_EQUIPMENT_ENABLED,
            OVERWORLD_ORES_ENABLED, PLANET_ORES_ENABLED, CRATERS_ENABLED, VOLCANOES_ENABLED, GEODES_ENABLED,
            CHARRED_TREES_ENABLED, LIGHTWOOD_TREES_ENABLED, SWAMP_TREES_ENABLED, INVERTED_PILLARS_ENABLED,
            CRYSTAL_CLUSTERS_ENABLED, ELECTRIC_MUSHROOMS_ENABLED);

    static boolean serverSwitch(ForgeConfigSpec.BooleanValue value) {
        return SERVER_SWITCHES.contains(value);
    }

    /** An in-memory override ({@link SwitchOverrides}, tests only), else the loaded value, else the default. */
    private static boolean serverSwitchValue(ForgeConfigSpec.BooleanValue value) {
        Boolean override = SwitchOverrides.get(value);
        if (override != null) {
            return override;
        }
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static boolean smallPlatePressEnabled() {
        return serverSwitchValue(SMALL_PLATE_PRESS_ENABLED);
    }

    public static boolean combustionGeneratorEnabled() {
        return serverSwitchValue(COMBUSTION_GENERATOR_ENABLED);
    }

    public static boolean solarGeneratorEnabled() {
        return serverSwitchValue(SOLAR_GENERATOR_ENABLED);
    }

    public static int solarGeneratorMultiplier() {
        return SPEC.isLoaded() ? SOLAR_GENERATOR_MULTIPLIER.get() : SOLAR_GENERATOR_MULTIPLIER.getDefault();
    }

    public static double tankCapacityMultiplier() {
        return SPEC.isLoaded() ? TANK_CAPACITY_MULTIPLIER.get() : TANK_CAPACITY_MULTIPLIER.getDefault();
    }

    public static boolean pumpEnabled() {
        return serverSwitchValue(PUMP_ENABLED);
    }

    public static boolean classicDevicesEnabled() {
        return serverSwitchValue(CLASSIC_DEVICES_ENABLED);
    }

    public static boolean classicGravityEnabled() {
        return serverSwitchValue(CLASSIC_GRAVITY_ENABLED);
    }

    public static boolean classicEquipmentEnabled() {
        return serverSwitchValue(CLASSIC_EQUIPMENT_ENABLED);
    }

    /** ADR-063 section 4: whether the Overworld ores generate; the default until the COMMON config is loaded. */
    public static boolean overworldOresEnabled() {
        return serverSwitchValue(OVERWORLD_ORES_ENABLED);
    }

    /** ADR-063 sections 4 and 5: the C15b world-feature switches; each reads its default until the config loads. */
    public static boolean planetOresEnabled() {
        return serverSwitchValue(PLANET_ORES_ENABLED);
    }

    public static boolean cratersEnabled() {
        return serverSwitchValue(CRATERS_ENABLED);
    }

    public static boolean volcanoesEnabled() {
        return serverSwitchValue(VOLCANOES_ENABLED);
    }

    public static boolean geodesEnabled() {
        return serverSwitchValue(GEODES_ENABLED);
    }

    public static boolean charredTreesEnabled() {
        return serverSwitchValue(CHARRED_TREES_ENABLED);
    }

    /** ADR-063 section 6: the C15c world-feature switches; each reads its default until the config loads. */
    public static boolean lightwoodTreesEnabled() {
        return serverSwitchValue(LIGHTWOOD_TREES_ENABLED);
    }

    public static boolean swampTreesEnabled() {
        return serverSwitchValue(SWAMP_TREES_ENABLED);
    }

    public static boolean invertedPillarsEnabled() {
        return serverSwitchValue(INVERTED_PILLARS_ENABLED);
    }

    public static boolean crystalClustersEnabled() {
        return serverSwitchValue(CRYSTAL_CLUSTERS_ENABLED);
    }

    public static boolean electricMushroomsEnabled() {
        return serverSwitchValue(ELECTRIC_MUSHROOMS_ENABLED);
    }

    private CommonConfig() {
    }
}
