package io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment;

/** Stable logical armor layout, independent of Minecraft slot ordinals. */
public enum SuitPart {
    HEAD(2), CHEST(3), LEGS(2), FEET(1);

    private final int positions;
    SuitPart(int positions) { this.positions = positions; }
    public int positions() { return positions; }
}
