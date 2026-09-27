package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Immutable logical body; a Level mapping is neither mandatory nor arrival permission. */
public record CelestialBodyDefinition(
        ResourceLocation id,
        Optional<ResourceLocation> parentId,
        Optional<ResourceKey<Level>> levelKey,
        double gravityMultiplier,
        AtmosphereDefinition atmosphere,
        OrbitDefinition orbit,
        ResourceLocation visualProfile,
        CelestialCapabilities capabilities,
        double solarIntensity,
        double radiation
) {
    public static final int SCHEMA_VERSION = 2;
    public static final double MAX_GRAVITY_MULTIPLIER = 4.0D;
    public static final double MAX_SOLAR_INTENSITY = 16.0D;
    public static final double MAX_RADIATION = 1.0D;
    public static final Codec<CelestialBodyDefinition> CODEC = CelestialDefinitionCodec.CODEC;

    public CelestialBodyDefinition {
        BoundedCelestialCodecs.requireId(id, "id");
        Objects.requireNonNull(parentId, "parentId");
        parentId.ifPresent(parent -> BoundedCelestialCodecs.requireId(parent, "parent"));
        Objects.requireNonNull(levelKey, "levelKey");
        levelKey.ifPresent(level -> BoundedCelestialCodecs.requireId(level.location(), "level"));
        Objects.requireNonNull(atmosphere, "atmosphere");
        Objects.requireNonNull(orbit, "orbit");
        BoundedCelestialCodecs.requireId(visualProfile, "visualProfile");
        Objects.requireNonNull(capabilities, "capabilities");
        BoundedCelestialCodecs.requireRange(gravityMultiplier, 0, MAX_GRAVITY_MULTIPLIER, "gravity");
        BoundedCelestialCodecs.requireRange(solarIntensity, 0, MAX_SOLAR_INTENSITY, "solar intensity");
        BoundedCelestialCodecs.requireRange(radiation, 0, MAX_RADIATION, "radiation");
        if (parentId.filter(id::equals).isPresent()) {
            throw new IllegalArgumentException("Celestial body cannot be its own parent");
        }
        if (parentId.isEmpty() != (orbit.distance() == 0L)) {
            throw new IllegalArgumentException("Only root bodies may use a zero-distance orbit");
        }
        if (capabilities.landable() && levelKey.isEmpty()) {
            throw new IllegalArgumentException("A landable body requires a Level mapping");
        }
        if (capabilities.gasGiant() && levelKey.isPresent()) {
            throw new IllegalArgumentException("A gas giant cannot have a surface Level mapping");
        }
    }

    /** Explicit legacy defaults for the pre-schema built-in authoring data and importer. */
    public CelestialBodyDefinition(
            ResourceLocation id, Optional<ResourceLocation> parentId, ResourceKey<Level> levelKey,
            double gravityMultiplier, AtmosphereDefinition atmosphere, OrbitDefinition orbit,
            ResourceLocation visualProfile
    ) {
        this(id, parentId, Optional.of(levelKey), gravityMultiplier, atmosphere, orbit, visualProfile,
                CelestialCapabilities.legacy(levelKey), 1.0D, 0.0D);
    }

    public boolean isRoot() {
        return parentId.isEmpty();
    }

    /** Definition eligibility only; this does not check a live Level or grant player access. */
    public boolean supportsSurfaceArrival() {
        return capabilities.landable() && levelKey.filter(level -> !level.equals(CelestialIds.SPACE_LEVEL)).isPresent();
    }

    /** Only the historical authoring provider opts into this checked, lossless legacy format. */
    public <T> DataResult<T> encodeLegacy(DynamicOps<T> ops) {
        return CelestialDefinitionCodec.encodeLegacy(this, ops);
    }
}
