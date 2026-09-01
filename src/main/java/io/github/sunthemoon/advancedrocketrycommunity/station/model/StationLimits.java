package io.github.sunthemoon.advancedrocketrycommunity.station.model;

/** Fixed v0.7 station budgets. Relaxing these bounds requires new evidence. */
public final class StationLimits {
    public static final int STATE_SCHEMA_VERSION = 1;
    public static final int REGISTRY_SCHEMA_VERSION = 1;
    public static final int MAX_STATIONS = 4_096;
    public static final int MAX_RESERVATIONS = 64;
    public static final int MAX_MEMBERS = 32;
    public static final int MAX_INVITATIONS = 32;
    public static final int MAX_ACCESSIBLE_DESTINATIONS = 32;
    public static final int MAX_OWNED_STATIONS = 1;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int REGION_SIZE = 512;
    public static final int GRID_SPACING = 1_024;
    public static final int PLATFORM_RADIUS = 8;
    public static final int PLATFORM_BLOCKS = 289;
    public static final int PLATFORM_Y = 127;
    public static final int LANDING_Y = PLATFORM_Y + 1;
    public static final int MAX_CELL_COORDINATE = 1_000_000;
    public static final int MAX_STATION_RECORD_NBT_BYTES = 8_192;
    public static final int MAX_REGISTRY_NBT_BYTES = 4 * 1_024 * 1_024;

    private StationLimits() {
    }
}
