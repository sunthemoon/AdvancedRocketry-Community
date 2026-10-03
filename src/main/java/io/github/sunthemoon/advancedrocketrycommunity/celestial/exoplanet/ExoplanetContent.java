package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfile;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The classic exoplanet worlds of ADR-063 section 6 with revision 6 (C15c): Tau Ceti f and g, both landable with a
 * startup Level, their three routes inside the Tau Ceti system, the v1.8 data satellite that can discover them, and
 * their sky profiles.
 */
public final class ExoplanetContent {
    public static final ResourceLocation TAU_CETI_F = ModIdentity.id("tau_ceti_f");
    public static final ResourceLocation TAU_CETI_G = ModIdentity.id("tau_ceti_g");
    public static final ResourceKey<Level> TAU_CETI_F_LEVEL = ResourceKey.create(Registries.DIMENSION, TAU_CETI_F);
    public static final ResourceKey<Level> TAU_CETI_G_LEVEL = ResourceKey.create(Registries.DIMENSION, TAU_CETI_G);

    private ExoplanetContent() {
    }

    public static List<CelestialBodyDefinition> definitions() {
        return List.of(
                // Breathable forest world: 1.0 atm, 295 K; 1.33 AU on Tau Ceti e's scale, its days per real day.
                new CelestialBodyDefinition(TAU_CETI_F, Optional.of(StarSystemContent.TAU_CETI),
                        Optional.of(TAU_CETI_F_LEVEL), 1.0D, new AtmosphereDefinition(1.0D, true, 295.0D, TAU_CETI_F),
                        new OrbitDefinition(199_000_000L, 20_764_800L, 0.0D), TAU_CETI_F,
                        new CelestialCapabilities(true, true, false), 0.8D, 0.0D, true, true),
                // Storm and crystal world: 1.4 atm, 255 K, not breathable; 0.13 AU.
                new CelestialBodyDefinition(TAU_CETI_G, Optional.of(StarSystemContent.TAU_CETI),
                        Optional.of(TAU_CETI_G_LEVEL), 1.2D, new AtmosphereDefinition(1.4D, false, 255.0D, TAU_CETI_G),
                        new OrbitDefinition(19_400_000L, 646_400L, 0.0D), TAU_CETI_G,
                        new CelestialCapabilities(true, true, false), 1.2D, 0.1D, true, true));
    }

    /** Between each surface and its orbit, and between the two surfaces; all inside the Tau Ceti system. */
    public static List<RouteDefinition> routes() {
        return List.of(
                route("tau_ceti_f_surface_orbit", RouteAnchor.bodySurface(TAU_CETI_F), RouteAnchor.orbit(TAU_CETI_F), 25),
                route("tau_ceti_g_surface_orbit", RouteAnchor.bodySurface(TAU_CETI_G), RouteAnchor.orbit(TAU_CETI_G), 35),
                route("tau_ceti_f_g", RouteAnchor.bodySurface(TAU_CETI_F), RouteAnchor.bodySurface(TAU_CETI_G), 60));
    }

    /** The v1.8 data satellite: the v1.5 targets plus both worlds; it supersedes the v1.5 copy (ADR-063 section 8). */
    public static SatelliteDefinition dataSatellite() {
        SatelliteDefinition previous = StarSystemContent.surveySatellite();
        List<ResourceLocation> targets = new ArrayList<>(previous.allowedTargets());
        targets.add(TAU_CETI_F);
        targets.add(TAU_CETI_G);
        return new SatelliteDefinition(previous.schemaVersion(), SatelliteIds.DATA_SATELLITE,
                previous.missionDurationTicks(), previous.researchYield(), previous.discoveryCost(), List.copyOf(targets));
    }

    /** A blue-green day sky for f; the dark legacy stormland sky (0x202020) for g, its sun dimmed by the storm. */
    public static Map<ResourceLocation, SkyProfile> skyProfiles() {
        return Map.of(
                TAU_CETI_F, new SkyProfile(0x5FA8C8, 0x07131C, 0x7FB8C0, 0xFFE2B0, 3, 1.0, 0.3, 0.6, 256,
                        Optional.empty(), 0),
                TAU_CETI_G, new SkyProfile(0x202020, 0x050505, 0x2A2A2E, 0xFFD8A0, 4, 0.5, 0.1, 0.3, 160,
                        Optional.empty(), 0));
    }

    private static RouteDefinition route(String id, RouteAnchor from, RouteAnchor to, int distance) {
        return new RouteDefinition(1, ModIdentity.id(id), from, to, distance, true);
    }
}
