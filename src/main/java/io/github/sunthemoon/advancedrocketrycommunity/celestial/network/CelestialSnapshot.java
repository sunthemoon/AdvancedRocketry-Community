package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Immutable display-only client view; it contains no authoritative world state. */
public record CelestialSnapshot(
        int schemaVersion,
        List<Entry> entries
) {
    public CelestialSnapshot {
        entries = List.copyOf(entries);
    }

    public record Entry(
            ResourceLocation bodyId,
            Optional<ResourceLocation> parentId,
            Optional<ResourceLocation> levelId,
            double gravityMultiplier,
            double pressure,
            boolean breathable,
            double temperatureKelvin,
            ResourceLocation atmosphereProfile,
            ResourceLocation visualProfile,
            CelestialCapabilities capabilities,
            double solarIntensity,
            double radiation
    ) {
        public Entry {
            BoundedCelestialCodecs.requireId(bodyId, "body id");
            Objects.requireNonNull(parentId, "parentId");
            parentId.ifPresent(id -> BoundedCelestialCodecs.requireId(id, "parent"));
            Objects.requireNonNull(levelId, "levelId");
            levelId.ifPresent(id -> BoundedCelestialCodecs.requireId(id, "level"));
            BoundedCelestialCodecs.requireId(visualProfile, "visual profile");
            // Reuse the configured atmosphere invariants, without authority or world access.
            new AtmosphereDefinition(pressure, breathable, temperatureKelvin, atmosphereProfile);
            BoundedCelestialCodecs.requireRange(gravityMultiplier, 0,
                    CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER, "gravity");
            BoundedCelestialCodecs.requireRange(solarIntensity, 0,
                    CelestialBodyDefinition.MAX_SOLAR_INTENSITY, "solar intensity");
            BoundedCelestialCodecs.requireRange(radiation, 0, CelestialBodyDefinition.MAX_RADIATION, "radiation");
            Objects.requireNonNull(capabilities, "capabilities");
            if ((capabilities.landable() && levelId.isEmpty()) || (capabilities.gasGiant() && levelId.isPresent())) {
                throw new IllegalArgumentException("Snapshot capability and Level mapping disagree");
            }
        }

        public boolean vacuum() {
            return pressure == 0.0D;
        }
    }
}
