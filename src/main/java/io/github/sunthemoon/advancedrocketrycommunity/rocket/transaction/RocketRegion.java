package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record RocketRegion(
        ResourceLocation dimension,
        RocketPosition minimum,
        RocketPosition maximum
) {
    public RocketRegion {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (minimum.x() > maximum.x()
                || minimum.y() > maximum.y()
                || minimum.z() > maximum.z()) {
            throw new IllegalArgumentException("Region minimum must not exceed maximum");
        }
    }

    public static RocketRegion fromSnapshot(RocketStructureSnapshot snapshot) {
        return new RocketRegion(
                snapshot.sourceDimension(),
                snapshot.sourceOrigin().add(snapshot.bounds().minimum()),
                snapshot.sourceOrigin().add(snapshot.bounds().maximum())
        );
    }

    public boolean overlaps(RocketRegion other) {
        return dimension.equals(other.dimension)
                && minimum.x() <= other.maximum.x() && maximum.x() >= other.minimum.x()
                && minimum.y() <= other.maximum.y() && maximum.y() >= other.minimum.y()
                && minimum.z() <= other.maximum.z() && maximum.z() >= other.minimum.z();
    }

    public boolean contains(RocketPosition position) {
        return position.x() >= minimum.x() && position.x() <= maximum.x()
                && position.y() >= minimum.y() && position.y() <= maximum.y()
                && position.z() >= minimum.z() && position.z() <= maximum.z();
    }
}
