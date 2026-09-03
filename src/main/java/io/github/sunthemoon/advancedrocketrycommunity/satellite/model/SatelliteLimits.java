package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

/** Fixed safety budgets for definitions, runtime state, persistence, and scheduling. */
public final class SatelliteLimits {
    public static final int DEFINITION_SCHEMA_VERSION = 1;
    public static final int SATELLITE_SCHEMA_VERSION = 1;
    public static final int MISSION_SCHEMA_VERSION = 1;
    public static final int RESEARCH_ACCOUNT_SCHEMA_VERSION = 1;
    public static final int REGISTRY_SCHEMA_VERSION = 2;

    public static final int MAX_DEFINITIONS = 16;
    public static final int MAX_TARGETS_PER_DEFINITION = 16;
    public static final int MAX_JSON_CHARS_PER_DEFINITION = 32_768;
    public static final int MIN_MISSION_DURATION_TICKS = 20;
    public static final int MAX_MISSION_DURATION_TICKS = 72_000;
    public static final int MAX_RESEARCH_PER_MISSION = 10_000;

    public static final int MAX_SATELLITES = 4_096;
    public static final int MAX_MISSIONS = 8_192;
    public static final int MAX_RESEARCH_ACCOUNTS = 4_096;
    public static final int MAX_ACTIVE_MISSIONS = 1_024;
    public static final int MAX_COMPLETIONS_PER_PASS = 32;
    public static final int MAX_QUEUE_INSPECTIONS_PER_PASS = 64;
    public static final int SCHEDULER_INTERVAL_TICKS = 20;
    public static final int MAX_REGISTRY_NBT_BYTES = 4 * 1024 * 1024;
    public static final int MAX_RECORD_NBT_BYTES = 4 * 1024;
    public static final int MAX_RESEARCH_BALANCE = 1_000_000;
    public static final long MAX_LIFETIME_RESEARCH = 1_000_000_000L;

    private SatelliteLimits() {
    }
}
