package io.github.sunthemoon.advancedrocketrycommunity.celestial.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Data-driven logical body definition with a stable optional fixed-Level mapping. */
public record CelestialBodyDefinition(
        ResourceLocation id,
        Optional<ResourceLocation> parentId,
        ResourceKey<Level> levelKey,
        double gravityMultiplier,
        AtmosphereDefinition atmosphere,
        OrbitDefinition orbit,
        ResourceLocation visualProfile
) {
    public static final double MAX_GRAVITY_MULTIPLIER = 4.0D;

    private static final Codec<CelestialBodyDefinition> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BoundedCelestialCodecs.RESOURCE_LOCATION
                    .fieldOf("id")
                    .forGetter(CelestialBodyDefinition::id),
            BoundedCelestialCodecs.RESOURCE_LOCATION
                    .optionalFieldOf("parent")
                    .forGetter(CelestialBodyDefinition::parentId),
            BoundedCelestialCodecs.LEVEL_KEY
                    .fieldOf("level")
                    .forGetter(CelestialBodyDefinition::levelKey),
            Codec.doubleRange(0.0D, MAX_GRAVITY_MULTIPLIER)
                    .fieldOf("gravity_multiplier")
                    .forGetter(CelestialBodyDefinition::gravityMultiplier),
            AtmosphereDefinition.CODEC
                    .fieldOf("atmosphere")
                    .forGetter(CelestialBodyDefinition::atmosphere),
            OrbitDefinition.CODEC.fieldOf("orbit").forGetter(CelestialBodyDefinition::orbit),
            BoundedCelestialCodecs.RESOURCE_LOCATION
                    .fieldOf("visual_profile")
                    .forGetter(CelestialBodyDefinition::visualProfile)
    ).apply(instance, CelestialBodyDefinition::new));

    public static final Codec<CelestialBodyDefinition> CODEC = BoundedCelestialCodecs.validated(
            RAW_CODEC,
            CelestialBodyDefinition::validate
    );

    public CelestialBodyDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(levelKey, "levelKey");
        Objects.requireNonNull(atmosphere, "atmosphere");
        Objects.requireNonNull(orbit, "orbit");
        Objects.requireNonNull(visualProfile, "visualProfile");
    }

    public boolean isRoot() {
        return parentId.isEmpty();
    }

    private static DataResult<CelestialBodyDefinition> validate(CelestialBodyDefinition value) {
        if (!Double.isFinite(value.gravityMultiplier)) {
            return DataResult.error(() -> "Gravity multiplier must be finite");
        }
        if (value.parentId.filter(value.id::equals).isPresent()) {
            return DataResult.error(() -> "Celestial body cannot be its own parent: " + value.id);
        }
        if (value.isRoot() != (value.orbit.distance() == 0L)) {
            return DataResult.error(() -> "Only root bodies may use a zero-distance orbit: " + value.id);
        }
        return DataResult.success(value);
    }
}
