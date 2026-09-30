package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

/** Stable server result codes shared by domain, terminal, commands, and UI. */
public enum SatelliteOperationCode {
    SUCCESS,
    IDEMPOTENT,
    UNSUPPORTED_DATA,
    CATALOG_UNAVAILABLE,
    DEFINITION_NOT_FOUND,
    TARGET_NOT_ALLOWED,
    CAPACITY_REACHED,
    IDENTITY_CONFLICT,
    SATELLITE_NOT_FOUND,
    MISSION_NOT_FOUND,
    MISSION_BUSY,
    NOT_READY,
    ALREADY_CLAIMED,
    CANCELLED,
    PENDING_DISCOVERY,
    UNAUTHORIZED,
    RECEIVER_REQUIRED,
    NO_POWER,
    INVALID_COMPONENTS,
    OUTPUT_BLOCKED,
    UNLOADED_CHUNK,
    OUT_OF_RANGE,
    RECOVERY_REQUIRED,
    SERVER_ERROR,
    // v1.6 (ADR-049..051); persisted by ordinal, so new codes are only appended.
    STAT_LIMIT,
    REQUIREMENT_UNMET,
    RESEARCH_LOCKED,
    COMPONENT_UNAVAILABLE,
    RATE_LIMITED,
    OWNER_LIMIT,
    STORAGE_BUDGET,
    AWAITING_WORLD_SAVE,
    DELIVERY_BUFFER_FULL,
    TERMINAL_RECEIPTS_FULL,
    TERMINAL_MISSING,
    NO_ASTEROID_TYPES,
    BODY_UNAVAILABLE,
    // C8b (ADR-051): a claim or cancel at a terminal the mission is not bound to, a terminal still reconciling,
    // and an asteroid instance that does not exist.
    WRONG_TERMINAL,
    RECONCILING,
    INSTANCE_NOT_FOUND;

    public String translationKey() {
        return "status.advancedrocketrycommunity.satellite."
                + name().toLowerCase(java.util.Locale.ROOT);
    }
}
