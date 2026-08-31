package io.github.sunthemoon.advancedrocketrycommunity.atmosphere;

/** Hard safety limits for the v0.4 atmosphere and life-support slice. */
public final class AtmosphereLimits {
    public static final int MAX_VOLUME_CELLS = 4_096;
    public static final int MAX_TASK_INSPECTIONS_PER_TICK = 256;
    public static final int MAX_LEVEL_INSPECTIONS_PER_TICK = 1_024;
    public static final int MAX_ACTIVE_SCAN_TASKS = 64;
    public static final int MAX_INDEXED_VOLUMES = 128;
    public static final int MAX_INDEXED_CELLS = 65_536;
    public static final int MAX_DIRTY_POSITIONS = 8_192;

    public static final int OXYGEN_UNITS_PER_CANISTER = 1_000;
    public static final int SUIT_OXYGEN_CAPACITY = 2_000;
    public static final int VENT_OXYGEN_CAPACITY = 4_000;
    public static final int VENT_ENERGY_CAPACITY = 40_000;
    public static final int VENT_ENERGY_PER_TICK = 20;

    private AtmosphereLimits() {
    }
}
