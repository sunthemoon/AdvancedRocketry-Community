package io.github.sunthemoon.advancedrocketrycommunity.station.model;

/** Persistent fixed-grid identity used by the shared Space Level allocator. */
public record StationGridCell(int x, int z) implements Comparable<StationGridCell> {
    public StationGridCell {
        if (Math.abs((long) x) > StationLimits.MAX_CELL_COORDINATE
                || Math.abs((long) z) > StationLimits.MAX_CELL_COORDINATE) {
            throw new IllegalArgumentException("Station cell is outside the fixed coordinate bound");
        }
    }

    public int centerX() {
        return Math.multiplyExact(x, StationLimits.GRID_SPACING);
    }

    public int centerZ() {
        return Math.multiplyExact(z, StationLimits.GRID_SPACING);
    }

    public StationRegion region() {
        int half = StationLimits.REGION_SIZE / 2;
        return new StationRegion(
                Math.subtractExact(centerX(), half),
                Math.subtractExact(centerZ(), half),
                Math.addExact(centerX(), half - 1),
                Math.addExact(centerZ(), half - 1)
        );
    }

    public StationPosition landingPad() {
        return new StationPosition(centerX(), StationLimits.LANDING_Y, centerZ());
    }

    @Override
    public int compareTo(StationGridCell other) {
        int xOrder = Integer.compare(x, other.x);
        return xOrder != 0 ? xOrder : Integer.compare(z, other.z);
    }
}

