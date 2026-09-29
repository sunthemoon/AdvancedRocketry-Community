package io.github.sunthemoon.advancedrocketrycommunity.station.model;

/** Horizontal ownership region; the fixed Space Level is implied by the registry. */
public record StationRegion(int minimumX, int minimumZ, int maximumX, int maximumZ) {
    public StationRegion {
        if (minimumX > maximumX || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Station region bounds are inverted");
        }
        long width = (long) maximumX - minimumX + 1L;
        long depth = (long) maximumZ - minimumZ + 1L;
        if (width != depth || (width != StationLimits.REGION_SIZE
                && width != StationLimits.EXPANDED_REGION_SIZE)) {
            throw new IllegalArgumentException("Station region must use an allowed square size");
        }
    }

    public int width() {
        return maximumX - minimumX + 1;
    }

    public boolean contains(int x, int z) {
        return x >= minimumX && x <= maximumX && z >= minimumZ && z <= maximumZ;
    }

    public boolean overlaps(StationRegion other) {
        return minimumX <= other.maximumX
                && maximumX >= other.minimumX
                && minimumZ <= other.maximumZ
                && maximumZ >= other.minimumZ;
    }
}
