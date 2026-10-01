package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

/** ADR-055 sections 2 and 3: the drill samples its orbit body, or digs at a linked laser target (opt-in). */
public enum LaserDrillMode {
    LOGICAL,
    PHYSICAL;

    /** Stable names in device roots; an unknown name is a defect of the root. */
    public static LaserDrillMode byName(String name) {
        for (LaserDrillMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown drill mode " + name);
    }
}
