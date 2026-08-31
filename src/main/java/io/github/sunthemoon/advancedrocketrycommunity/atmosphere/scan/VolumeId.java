package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

/** Full SHA-256 identity of one connected traversable cell set. */
public record VolumeId(String value) implements Comparable<VolumeId> {
    public VolumeId {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Volume ID must be a lowercase SHA-256 value");
        }
    }

    @Override
    public int compareTo(VolumeId other) {
        return value.compareTo(other.value);
    }
}
