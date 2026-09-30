package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

/** Fixed safety budgets for definitions, runtime state, persistence, and scheduling. */
public final class SatelliteLimits {
    public static final int DEFINITION_SCHEMA_VERSION = 1;
    /** Non-{@code data} kinds use definition schema 2 (ADR-049 §3). */
    public static final int KIND_DEFINITION_SCHEMA_VERSION = 2;
    public static final int COMPONENT_SCHEMA_VERSION = 1;
    public static final int LEGACY_SATELLITE_SCHEMA_VERSION = 1;
    public static final int SATELLITE_SCHEMA_VERSION = 2;
    public static final int LEGACY_MISSION_SCHEMA_VERSION = 1;
    public static final int MISSION_SCHEMA_VERSION = 2;
    public static final int INSTANCE_SCHEMA_VERSION = 1;
    public static final int RESEARCH_ACCOUNT_SCHEMA_VERSION = 1;
    /** Registry root 3 (ADR-050 §10); roots 1 and 2 are migrated before use. */
    public static final int REGISTRY_SCHEMA_VERSION = 3;
    public static final String REGISTRY_FORMAT_EPOCH = "v1.6.0-satellite-missions";

    public static final int MAX_DEFINITIONS = 16;
    public static final int MAX_KIND_DEFINITIONS = 16;
    public static final int MAX_TARGETS_PER_DEFINITION = 16;
    public static final int MAX_JSON_CHARS_PER_DEFINITION = 32_768;
    public static final int MIN_MISSION_DURATION_TICKS = 20;
    public static final int MAX_MISSION_DURATION_TICKS = 72_000;
    public static final int MAX_RESEARCH_PER_MISSION = 10_000;
    public static final int MAX_REQUIRED_LIFETIME_RESEARCH = 1_000_000;

    public static final int MAX_COMPONENT_DEFINITIONS = 64;
    public static final int MAX_JSON_CHARS_PER_COMPONENT = 8_192;
    public static final int MAX_MODULES = 6;
    public static final int MAX_BLUEPRINT_COMPONENTS = 8;
    public static final int BASE_BATTERY = 720;
    public static final int MAX_POWER = 1_000;
    public static final int MAX_BATTERY = 1_000_000;
    public static final int MAX_DATA = 100_000;
    public static final int MAX_CARGO = 27;
    public static final int MAX_RATING = 100;
    public static final int MIN_SCAN_RADIUS = 16;
    public static final int MAX_SCAN_RADIUS = 48;
    public static final int MAX_SCAN_ENERGY = 100_000;
    public static final int MAX_SOLAR_MULTIPLIER_PERCENT = 400;
    public static final int MAX_INSTANCES_PER_SURVEY = 4;

    public static final int MAX_SATELLITES = 4_096;
    /** Load bound; v1.6 admission limits are separate (ADR-050 §6, §10). */
    public static final int MAX_MISSIONS = 8_192;
    public static final int MAX_RESEARCH_ACCOUNTS = 4_096;
    public static final int MAX_ACTIVE_MISSIONS = 1_024;
    public static final int MAX_INSTANCES = 2_048;
    public static final int MAX_COMPLETIONS_PER_PASS = 32;
    public static final int MAX_QUEUE_INSPECTIONS_PER_PASS = 64;
    public static final int SCHEDULER_INTERVAL_TICKS = 20;
    /** Root bound raised with ADR-050 §7 (13 MiB of section budgets plus framing). */
    public static final int MAX_REGISTRY_NBT_BYTES = 16 * 1024 * 1024;
    /** Legacy per-record bound, still used for research accounts. */
    public static final int MAX_RECORD_NBT_BYTES = 4 * 1024;
    public static final int MAX_SATELLITE_RECORD_NBT_BYTES = 2 * 1024;
    public static final int MAX_MISSION_RECORD_NBT_BYTES = 4 * 1024;
    public static final int MAX_INSTANCE_RECORD_NBT_BYTES = 4 * 1024;
    public static final int MAX_REWARD_ENTRIES = 17;
    public static final int MAX_REWARD_ITEMS = 1_728;
    public static final int MAX_YIELD_ITEMS = 4_096;
    public static final int MAX_REWARD_VERSION_CHARS = 200;
    public static final int MAX_RESEARCH_BALANCE = 1_000_000;
    public static final long MAX_LIFETIME_RESEARCH = 1_000_000_000L;

    private SatelliteLimits() {
    }
}
