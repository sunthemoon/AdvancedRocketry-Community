package io.github.sunthemoon.advancedrocketrycommunity.rocket.validation;

import java.util.Locale;

/** Stable machine-readable diagnostics used by tests, commands, and player feedback. */
public enum RocketValidationCode {
    SUCCESS,
    EMPTY_STRUCTURE,
    TOO_MANY_BLOCKS,
    BOUNDING_VOLUME_EXCEEDED,
    TOO_MANY_PALETTE_ENTRIES,
    TOO_MANY_BLOCK_ENTITIES,
    BLOCK_ENTITY_DATA_TOO_LARGE,
    SNAPSHOT_DATA_TOO_LARGE,
    DUPLICATE_BLOCK_POSITION,
    POSITION_OVERFLOW,
    INVALID_BLOCK_STATE,
    INVALID_BLOCK_ENTITY_DATA,
    STATS_MISMATCH,
    HASH_MISMATCH,
    UNSUPPORTED_SCHEMA,
    MALFORMED_SNAPSHOT,
    MISSING_ENGINE,
    MISSING_SEAT,
    MISSING_GUIDANCE,
    INSUFFICIENT_THRUST,
    SCAN_BUDGET_EXCEEDED,
    UNLOADED_CHUNK,
    FORBIDDEN_BLOCK,
    BLOCK_NOT_MOVABLE,
    UNSUPPORTED_BLOCK_ENTITY,
    REGION_BUSY,
    OPERATION_LEDGER_FULL,
    WORLD_CHANGED,
    EXTRACTION_FAILED,
    SPAWN_FAILED,
    ROLLBACK_FAILED,
    TARGET_OCCUPIED,
    ENTITY_STATE_INVALID,
    REQUEST_REPLAYED,
    OUT_OF_RANGE,
    UNAUTHORIZED;

    public String translationKey() {
        return "validation.advancedrocketrycommunity.rocket."
                + name().toLowerCase(Locale.ROOT);
    }
}
