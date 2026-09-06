package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

/** Integer local offset or world position used by the bounded pattern core. */
public record PatternPosition(int x, int y, int z) implements Comparable<PatternPosition> {
    public PatternPosition add(PatternPosition offset) {
        return new PatternPosition(
                Math.addExact(x, offset.x),
                Math.addExact(y, offset.y),
                Math.addExact(z, offset.z)
        );
    }

    public PatternPosition subtract(PatternPosition offset) {
        return new PatternPosition(
                Math.subtractExact(x, offset.x),
                Math.subtractExact(y, offset.y),
                Math.subtractExact(z, offset.z)
        );
    }

    @Override
    public int compareTo(PatternPosition other) {
        int yOrder = Integer.compare(y, other.y);
        if (yOrder != 0) {
            return yOrder;
        }
        int zOrder = Integer.compare(z, other.z);
        return zOrder != 0 ? zOrder : Integer.compare(x, other.x);
    }
}
