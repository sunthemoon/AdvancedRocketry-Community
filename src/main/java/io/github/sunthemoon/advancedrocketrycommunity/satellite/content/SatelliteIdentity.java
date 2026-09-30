package io.github.sunthemoon.advancedrocketrycommunity.satellite.content;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable identity shared by one assembled package and its control chip. Non-{@code data} kinds also carry
 * the blueprint's component item IDs in slot order; stats are re-derived from them at launch (ADR-049 §6).
 */
public record SatelliteIdentity(
        UUID satelliteId,
        UUID ownerId,
        ResourceLocation definitionId,
        SatelliteKind kind,
        List<ResourceLocation> components
) {
    public SatelliteIdentity {
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(components, "components");
        components = List.copyOf(components);
        if (kind == SatelliteKind.DATA ? !components.isEmpty()
                : components.isEmpty() || components.size() > SatelliteLimits.MAX_BLUEPRINT_COMPONENTS) {
            throw new IllegalArgumentException("Satellite identity components do not match its kind");
        }
    }

    /** A {@code data} identity from the terminal's fixed recipe or an ADR-029 payload. */
    public SatelliteIdentity(UUID satelliteId, UUID ownerId, ResourceLocation definitionId) {
        this(satelliteId, ownerId, definitionId, SatelliteKind.DATA, List.of());
    }
}
