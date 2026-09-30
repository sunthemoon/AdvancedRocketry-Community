package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-043 knowledge rule with no new persistence: a body is known when it needs no discovery or has
 * a recorded discovery (ADR-037); a star system is known when any of its bodies is known.
 */
public final class StarSystemKnowledge {
    private StarSystemKnowledge() {
    }

    public static boolean bodyKnown(CelestialCatalog catalog, ResourceLocation bodyId,
                                    Predicate<ResourceLocation> discovered) {
        Objects.requireNonNull(catalog, "catalog");
        return catalog.get(bodyId).filter(body -> PlanetaryDiscoveryPolicy.allows(body, discovered)).isPresent();
    }

    public static boolean systemKnown(CelestialCatalog catalog, ResourceLocation systemId,
                                      Predicate<ResourceLocation> discovered) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(discovered, "discovered");
        return catalog.systemBodies(systemId).stream().anyMatch(body -> PlanetaryDiscoveryPolicy.allows(body, discovered));
    }
}
