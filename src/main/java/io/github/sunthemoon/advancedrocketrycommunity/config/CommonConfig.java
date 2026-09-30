package io.github.sunthemoon.advancedrocketrycommunity.config;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
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

    public static final ForgeConfigSpec SPEC = BUILDER.build();

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
