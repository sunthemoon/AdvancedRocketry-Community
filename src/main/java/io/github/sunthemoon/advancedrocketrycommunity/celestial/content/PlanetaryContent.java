package io.github.sunthemoon.advancedrocketrycommunity.celestial.content;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Additive data-generation inputs, never a runtime destination allowlist. */
public final class PlanetaryContent {
    public static final ResourceLocation MARS = ModIdentity.id("mars");
    public static final ResourceLocation VENUS = ModIdentity.id("venus");
    public static final ResourceLocation GAS_GIANT = ModIdentity.id("gas_giant");

    private PlanetaryContent() {
    }

    public static ResourceKey<Level> level(ResourceLocation id) {
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    public static List<CelestialBodyDefinition> definitions() {
        return List.of(
                body(MARS, true, 0.38, 0.006, 210, 0.43, 0.05, 54_600_000, 16_488_000),
                body(VENUS, true, 0.90, 9.2, 737, 1.91, 0.02, 41_400_000, 5_400_000),
                body(GAS_GIANT, false, 2.5, 10, 165, 0.04, 0.5, 588_000_000, 103_992_000)
        );
    }

    private static CelestialBodyDefinition body(ResourceLocation id, boolean surface, double gravity,
            double pressure, double temperature, double solar, double radiation, long distance, long period) {
        return new CelestialBodyDefinition(id, Optional.of(CelestialIds.EARTH_ID),
                surface ? Optional.of(level(id)) : Optional.empty(), gravity,
                new AtmosphereDefinition(pressure, false, temperature, id),
                new OrbitDefinition(distance, period, 0), id,
                new CelestialCapabilities(surface, true, !surface), solar, radiation);
    }

    public static List<RouteDefinition> routes() {
        var earth = RouteAnchor.bodySurface(CelestialIds.EARTH_ID);
        return List.of(
                route("earth_mars", earth, RouteAnchor.bodySurface(MARS), 80),
                route("earth_venus", earth, RouteAnchor.bodySurface(VENUS), 95),
                route("mars_surface_orbit", RouteAnchor.bodySurface(MARS), RouteAnchor.orbit(MARS), 25),
                route("venus_surface_orbit", RouteAnchor.bodySurface(VENUS), RouteAnchor.orbit(VENUS), 35),
                route("earth_gas_orbit", earth, RouteAnchor.orbit(GAS_GIANT), 150)
        );
    }

    private static RouteDefinition route(String id, RouteAnchor from, RouteAnchor to, int distance) {
        return new RouteDefinition(1, ModIdentity.id(id), from, to, distance, true);
    }
}
