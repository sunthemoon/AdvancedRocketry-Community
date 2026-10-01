package io.github.sunthemoon.advancedrocketrycommunity.endgame.model;

/**
 * Fixed endgame maxima from ADR-054. A COMMON config value can only lower them (the {@code CommonConfig.limit}
 * pattern); the root's growth accounting (section 10) assumes every one of them can be reached at once.
 */
public final class EndgameLimits {
    // Section 4: intents and the device view.
    public static final int MIN_INTENT_TICKS = 10;
    public static final int MIN_SELECTION_TICKS = 2;
    public static final int MAX_INTERVAL_TICKS = 200;
    public static final double INTENT_DISTANCE_BLOCKS = 8.0D;
    public static final int MAX_DEVICE_VIEW_BYTES = 8 * 1024;
    public static final int DEVICE_VIEW_INTERVAL_TICKS = 5;
    public static final int MAX_UPDATE_TAG_BYTES = 1024;

    // Section 2.1: structure validation.
    public static final int STRUCTURE_VALIDATIONS_PER_TICK = 8;
    public static final int STRUCTURE_REVALIDATION_TICKS = 200;

    // Section 6: protected zones.
    public static final int MAX_ZONES = 256;
    public static final int MAX_ZONE_SPAN = 4096;
    public static final int MAX_ZONE_ALLOW_LIST = 16;
    public static final int MAX_ZONE_NAME_LENGTH = 32;

    // Section 9: endpoints.
    public static final int MAX_ENDPOINTS = 2048;
    public static final int MAX_ENDPOINTS_PER_OWNER = 64;

    // Section 11 tombstones (R3-M2).
    public static final int TOMBSTONE_HOUSEKEEPING_THRESHOLD = 4096;
    public static final int TOMBSTONE_HOUSEKEEPING_AGE_TICKS = 6000;
    /** Housekeeping runs at most once per this many ticks (review C11R-M5). */
    public static final int TOMBSTONE_HOUSEKEEPING_INTERVAL_TICKS = 200;
    public static final int MAX_SETTLED_TOMBSTONES_PER_OWNER = 256;
    public static final int MAX_SETTLED_TOMBSTONES = 8192;
    public static final int PERSISTENCE_AGE_TICKS = 40;
    /** Section 7 ledger pass: endpoint registrations per tick. */
    public static final int REGISTRATIONS_PER_TICK = 32;

    // Section 10: the endgame root.
    public static final int ROOT_SCHEMA_VERSION = 1;
    public static final String ROOT_FORMAT_EPOCH = "v1.7.0-endgame";
    public static final long MAX_ROOT_BYTES = 4L * 1024L * 1024L;
    public static final long GROWTH_ADMISSION_BYTES = 3L * 1024L * 1024L;
    public static final int ENDPOINT_RECORD_BYTES = 384;
    public static final int DISPATCHED_THROUGH_ENTRY_BYTES = 32;
    public static final int TRANSIT_RECORD_BYTES = 2560;
    public static final int TOMBSTONE_RECORD_BYTES = 64;
    public static final int PAIR_RECORD_BYTES = 512;
    public static final int ZONE_RECORD_BYTES = 768;
    public static final int MAX_TRANSIT_RECORDS = 256;
    public static final int MAX_PAIRS = 1024;
    public static final int MAX_PINNED_TOMBSTONES = MAX_TRANSIT_RECORDS * 3 + MAX_PAIRS * 2;
    public static final int COALESCED_FLUSH_INTERVAL_TICKS = 100;

    // Section 7 barrier spacing.
    public static final int BARRIER_SPACING_TICKS = 20;
    public static final int BARRIER_STATION_COOLDOWN_TICKS = 100;

    // Section 13: audit.
    public static final int AUDIT_RING_LINES = 512;
    public static final int AUDIT_LINES_PER_TICK = 64;
    public static final int AUDIT_LINE_BYTES = 512;
    public static final int AUDIT_SUMMARY_INTERVAL_TICKS = 1200;
    public static final int AUDIT_PAGE_LINES = 16;
    public static final int PROTECTION_AUDIT_INTERVAL_TICKS = 1200;

    /** Accounted maximum of a root with every section full (section 10): 2,932,736 bytes. */
    public static final long ACCOUNTED_MAXIMUM_BYTES = (long) MAX_ENDPOINTS * ENDPOINT_RECORD_BYTES
            + (long) MAX_ENDPOINTS * DISPATCHED_THROUGH_ENTRY_BYTES
            + (long) MAX_TRANSIT_RECORDS * TRANSIT_RECORD_BYTES
            + (long) (MAX_SETTLED_TOMBSTONES + MAX_PINNED_TOMBSTONES) * TOMBSTONE_RECORD_BYTES
            + (long) MAX_PAIRS * PAIR_RECORD_BYTES
            + (long) MAX_ZONES * ZONE_RECORD_BYTES;

    private EndgameLimits() {
    }
}
