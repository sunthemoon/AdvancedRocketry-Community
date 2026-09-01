package io.github.sunthemoon.advancedrocketrycommunity.station.model;

/** Horizontal ownership region; the fixed Space Level is implied by the registry. */
public record StationRegion(int minimumX, int minimumZ, int maximumX, int maximumZ) {
    public StationRegion {
        if (minimumX > maximumX || minimumZ > maximumZ) {
            throw new IllegalArgumentException("Station region bounds are inverted");
        }
        long width = (long) maximumX - minimumX + 1L;
        long depth = (long) maximumZ - minimumZ + 1L;
        if (width != StationLimits.REGION_SIZE || depth != StationLimits.REGION_SIZE) {
            throw new IllegalArgumentException("Station region must use the fixed v0.7 size");
        }
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

