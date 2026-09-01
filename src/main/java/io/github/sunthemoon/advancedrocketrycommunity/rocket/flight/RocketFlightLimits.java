package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;

/** Fixed v0.6 safety and timing limits. Relaxing a limit requires evidence and an ADR. */
public final class RocketFlightLimits {
    public static final int FLIGHT_DATA_SCHEMA_VERSION = 1;
    public static final int TRANSFER_JOURNAL_SCHEMA_VERSION = 1;
    public static final int MAX_PASSENGERS = 16;
    public static final int MAX_COMMITTED_FUEL_DEBITS = 64;
    public static final int MAX_ACTIVE_TRANSFERS = 64;
    public static final int MAX_TRANSFER_ENTITY_MATCHES = 64;
    public static final int MAX_REPLAY_REQUESTS = 4_096;
    public static final int MAX_TRACKED_INTENT_PLAYERS = 128;
    public static final int MAX_INTENTS_PER_WINDOW = 8;
    public static final int INTENT_WINDOW_TICKS = 20;
    public static final int MAX_LANDING_PAD_CANDIDATES = 8;
    public static final int MAX_LANDING_CHUNKS = 16;
    public static final int MAX_LANDING_BLOCK_INSPECTIONS = RocketLimits.MAX_BLOCKS;
    public static final int MAX_FLIGHT_DATA_NBT_BYTES = 65_536;
    public static final int MAX_TRANSFER_RECORD_NBT_BYTES =
            RocketLimits.MAX_TOTAL_NBT_BYTES * 2 + MAX_FLIGHT_DATA_NBT_BYTES * 2 + 65_536;
    public static final int MAX_TRANSFER_JOURNAL_NBT_BYTES =
            MAX_TRANSFER_RECORD_NBT_BYTES * MAX_ACTIVE_TRANSFERS + 65_536;
    public static final long MAX_FUEL_CAPACITY = (long) RocketLimits.MAX_BLOCKS * 1_000L;
    public static final long FUEL_CELL_UNITS = 500L;
    public static final long FUEL_TRANSFER_PER_TICK = 25L;
    public static final long BASE_TRAVEL_FUEL = 100L;
    public static final long MAX_TRAVEL_FUEL = MAX_FUEL_CAPACITY;
    public static final int COUNTDOWN_TICKS = 60;
    public static final int ASCENT_TICKS = 80;
    public static final int TRANSIT_TICKS = 20;
    public static final int DESCENT_TICKS = 80;
    public static final int FLIGHT_ALTITUDE_BLOCKS = 48;

    private RocketFlightLimits() {
    }
}
