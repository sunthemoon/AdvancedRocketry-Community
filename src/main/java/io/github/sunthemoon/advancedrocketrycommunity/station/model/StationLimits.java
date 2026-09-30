package io.github.sunthemoon.advancedrocketrycommunity.station.model;

/** Fixed station budgets; ADR-040 adds one bounded expansion size, ADR-044 the warp energy bounds. */
public final class StationLimits {
    public static final int STATE_SCHEMA_VERSION = 2;
    public static final int RESERVATION_SCHEMA_VERSION = 1;
    public static final int REGISTRY_SCHEMA_VERSION = 4;
    /** Root schema 3 (ADR-040): record 2 without warp energy; upgraded to 4 before start. */
    public static final int ORBITAL_REGISTRY_SCHEMA_VERSION = 3;
    public static final int MAX_STATIONS = 4_096;
    public static final int MAX_RESERVATIONS = 64;
    public static final int MAX_MEMBERS = 32;
    public static final int MAX_INVITATIONS = 32;
    public static final int MAX_ACCESSIBLE_DESTINATIONS = 32;
    public static final int MAX_OWNED_STATIONS = 1;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int REGION_SIZE = 512;
    public static final int EXPANDED_REGION_SIZE = 768;
    public static final int GRID_SPACING = 1_024;
    public static final int PLATFORM_RADIUS = 8;
    public static final int PLATFORM_BLOCKS = 289;
    public static final int PLATFORM_Y = 127;
    public static final int LANDING_Y = PLATFORM_Y + 1;
    public static final int MAX_CELL_COORDINATE = 1_000_000;
    public static final int MAX_STATION_RECORD_NBT_BYTES = 8_192;
    public static final int MAX_REGISTRY_NBT_BYTES = 4 * 1_024 * 1_024;
    public static final int MAX_PENDING_EXPANSIONS = 128;
    public static final long EXPANSION_CONFIRMATION_TICKS = 200L;
    /** ADR-044: one station's stored warp energy, in FE. */
    public static final int MAX_WARP_ENERGY = 10_000_000;
    public static final int MAX_WARP_ENERGY_ENTRIES = MAX_STATIONS;
    public static final int MAX_WARP_ENERGY_ENTRY_NBT_BYTES = 64;
    /** A credit that adds a balance entry is refused while the registry is this close to its bound. */
    public static final int WARP_ENERGY_HEADROOM_NBT_BYTES = 256 * 1_024;

    private StationLimits() {
    }
}
