package io.github.sunthemoon.advancedrocketrycommunity.rocket;

/** Fixed v0.5 safety limits. Changes require benchmark evidence and an ADR. */
public final class RocketLimits {
    public static final int SNAPSHOT_SCHEMA_VERSION = 1;
    public static final int MAX_BLOCKS = 2_048;
    public static final long MAX_BOUNDING_VOLUME = 32_768L;
    public static final int MAX_BLOCK_ENTITIES = 128;
    public static final int MAX_BLOCK_ENTITY_NBT_BYTES = 262_144;
    public static final int MAX_TOTAL_NBT_BYTES = 1_048_576;
    public static final int MAX_PALETTE_ENTRIES = 512;
    public static final int MAX_BLOCK_PROPERTIES = 32;
    public static final int MAX_IDENTIFIER_LENGTH = 255;
    public static final int MAX_PROPERTY_NAME_LENGTH = 64;
    public static final int MAX_PROPERTY_VALUE_LENGTH = 64;
    public static final int MAX_VISUAL_SNAPSHOT_BYTES = 524_288;
    public static final int MAX_VISUAL_CHUNK_BYTES = 32_768;
    public static final int MAX_VISUAL_REASSEMBLIES = 8;

    private RocketLimits() {
    }
}
