package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

/** Immutable inclusive bounds of traversable scan cells. */
public record VolumeBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public VolumeBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Volume bounds must be ordered");
        }
    }

    public static VolumeBounds single(VolumePosition position) {
        return new VolumeBounds(
                position.x(),
                position.y(),
                position.z(),
                position.x(),
                position.y(),
                position.z()
        );
    }

    public VolumeBounds include(VolumePosition position) {
        return new VolumeBounds(
                Math.min(minX, position.x()),
                Math.min(minY, position.y()),
                Math.min(minZ, position.z()),
                Math.max(maxX, position.x()),
                Math.max(maxY, position.y()),
                Math.max(maxZ, position.z())
        );
    }

    public boolean contains(VolumePosition position) {
        return position.x() >= minX && position.x() <= maxX
                && position.y() >= minY && position.y() <= maxY
                && position.z() >= minZ && position.z() <= maxZ;
    }
}
