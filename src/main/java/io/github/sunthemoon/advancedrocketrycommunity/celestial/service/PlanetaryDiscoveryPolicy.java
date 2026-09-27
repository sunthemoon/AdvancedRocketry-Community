package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Destination permission only; does not create routes, visits, surfaces or station access. */
public final class PlanetaryDiscoveryPolicy {
    private PlanetaryDiscoveryPolicy() { }

    public static boolean allows(CelestialBodyDefinition body, Predicate<ResourceLocation> discovered) {
        Objects.requireNonNull(discovered, "discovered");
        return body != null && (!body.discoveryRequired() || discovered.test(body.id()));
    }
}
