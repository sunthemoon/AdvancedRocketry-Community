package io.github.sunthemoon.advancedrocketrycommunity.celestial.context;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Server-resolved logical environment identity for one world location or instance. */
public record BodyContext(
        ResourceLocation bodyId,
        Locus locus,
        Optional<UUID> instanceId
) {
    public BodyContext {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(locus, "locus");
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        if (bodyId.toString().length() > 128) {
            throw new IllegalArgumentException("Body context identifier exceeds 128 characters");
        }
        if (locus == Locus.SURFACE && instanceId.isPresent()) {
            throw new IllegalArgumentException("Surface context cannot carry an instance UUID");
        }
        if (locus == Locus.MISSION && instanceId.isEmpty()) {
            throw new IllegalArgumentException("Mission context requires an instance UUID");
        }
    }

    public static BodyContext surface(ResourceLocation bodyId) {
        return new BodyContext(bodyId, Locus.SURFACE, Optional.empty());
    }

    public static BodyContext orbit(ResourceLocation bodyId) {
        return new BodyContext(bodyId, Locus.ORBIT, Optional.empty());
    }

    public static BodyContext stationOrbit(ResourceLocation bodyId, UUID stationId) {
        return new BodyContext(bodyId, Locus.ORBIT, Optional.of(stationId));
    }

    public static BodyContext mission(ResourceLocation bodyId, UUID missionId) {
        return new BodyContext(bodyId, Locus.MISSION, Optional.of(missionId));
    }

    public enum Locus {
        SURFACE,
        ORBIT,
        MISSION
    }
}
