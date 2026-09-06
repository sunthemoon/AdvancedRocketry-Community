package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

/** Bounded pattern dimensions. */
public record PatternSize(int x, int y, int z) {
    public static final int MAX_AXIS = 16;
    public static final int MAX_CELLS = 4_096;

    public PatternSize {
        if (x < 1 || y < 1 || z < 1 || x > MAX_AXIS || y > MAX_AXIS || z > MAX_AXIS) {
            throw new IllegalArgumentException("pattern axes must be in the range 1..16");
        }
        if (Math.multiplyExact(Math.multiplyExact(x, y), z) > MAX_CELLS) {
            throw new IllegalArgumentException("pattern exceeds the cell limit");
        }
    }

    public int volume() {
        return x * y * z;
    }

    public boolean contains(PatternPosition position) {
        return position.x() >= 0 && position.x() < x
                && position.y() >= 0 && position.y() < y
                && position.z() >= 0 && position.z() < z;
    }
}
