package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

public enum PatternRotation {
    ZERO(0),
    CLOCKWISE_90(90),
    CLOCKWISE_180(180),
    CLOCKWISE_270(270);

    private final int degrees;

    PatternRotation(int degrees) {
        this.degrees = degrees;
    }

    public int degrees() {
        return degrees;
    }
}
