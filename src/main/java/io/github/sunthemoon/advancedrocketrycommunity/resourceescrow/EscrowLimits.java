package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** W11a2 bounds. These are admission/proof limits, never truncation targets. */
public final class EscrowLimits {
    public static final int ROOT_BYTES = 1_048_576;
    public static final int OPERATIONS = 64;
    public static final int PER_ROCKET = 8;
    public static final int FLOORS = 256;
    public static final int TOMBSTONES = 64;
    public static final int OP_BYTES = 12_288;
    public static final int REFERENCE_BYTES = 512;
    public static final int FLUID_BYTES = 8_192;
    public static final int BE_MARKER_BYTES = 256;
    public static final int ENTITY_MARKER_BYTES = 1_024;
    public static final int CAPTURE_BYTES = 16 * 1_048_576;
    public static final String EPOCH = "v1.8.0-resource-escrow";
    private EscrowLimits() { }
}
