package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

/** Redstone control of an endgame device (ADR-055 section 1): the automation path for starting and pausing. */
public enum EndgameRedstoneMode {
    IGNORED,
    ON,
    INVERTED;

    public boolean satisfied(boolean powered) {
        return switch (this) {
            case IGNORED -> true;
            case ON -> powered;
            case INVERTED -> !powered;
        };
    }

    public EndgameRedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Stable names in device roots; an unknown name is a defect of the root. */
    public static EndgameRedstoneMode byName(String name) {
        for (EndgameRedstoneMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown redstone mode " + name);
    }
}
