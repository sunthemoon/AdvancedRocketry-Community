package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

/**
 * ADR-054 section 2: every endgame block entity keeps its root under one compound, so a chunk tag can be read for
 * presence and persistence without the block entity class.
 */
public final class EndgameDeviceTags {
    /** The block entity's endgame root. */
    public static final String ROOT = "endgame";
    public static final String SCHEMA_VERSION = "schema_version";
    public static final String DEVICE_ID = "device_id";
    public static final String OWNER_ID = "owner_id";
    /** Recorded once an ID was frozen, so it never registers again (review R4-L3). */
    public static final String FROZEN = "frozen";

    private EndgameDeviceTags() {
    }
}
