package io.github.sunthemoon.advancedrocketrycommunity.celestial.content;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.AtmosphereDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.OrbitDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-043 v1.5 example star system, as data-generation input only. Values are placeholders inside
 * the celestial schema bounds, not literal astronomy (a real star exceeds the 2,000 K and 4 g bounds).
 */
public final class StarSystemContent {
    public static final ResourceLocation TAU_CETI = ModIdentity.id("tau_ceti");
    public static final ResourceLocation TAU_CETI_E = ModIdentity.id("tau_ceti_e");
    private static final ResourceLocation VACUUM = ModIdentity.id("vacuum");

    private StarSystemContent() {
    }

    public static List<CelestialBodyDefinition> definitions() {
        // Root star: public (no discovery), not a destination and not a satellite target.
        CelestialBodyDefinition star = new CelestialBodyDefinition(TAU_CETI, Optional.empty(), Optional.empty(), 0.0D,
                new AtmosphereDefinition(0.0D, false, 2_000.0D, VACUUM), new OrbitDefinition(0L, 0L, 0.0D),
                CelestialIds.SPACE_ID, new CelestialCapabilities(false, false, false), 0.0D, 0.0D, false, false);
        // Orbit-only planet: stations may orbit it after a warp; no Level, so nothing lands there.
        CelestialBodyDefinition planet = new CelestialBodyDefinition(TAU_CETI_E, Optional.of(TAU_CETI), Optional.empty(),
                1.1D, new AtmosphereDefinition(0.0D, false, 250.0D, VACUUM), new OrbitDefinition(83_000_000L, 5_433_600L, 0.0D),
                PlanetaryContent.MARS, new CelestialCapabilities(false, true, false), 0.5D, 0.1D, false, true);
        return List.of(star, planet);
    }

    /** v1.5 data satellite: the v1.4 targets plus the example planet (supersedes the v1.4 copy). */
    public static SatelliteDefinition surveySatellite() {
        SatelliteDefinition previous = PlanetaryContent.surveySatellite();
        java.util.ArrayList<ResourceLocation> targets = new java.util.ArrayList<>(previous.allowedTargets());
        targets.add(TAU_CETI_E);
        return new SatelliteDefinition(previous.schemaVersion(), SatelliteIds.DATA_SATELLITE, previous.missionDurationTicks(),
                previous.researchYield(), previous.discoveryCost(), List.copyOf(targets));
    }
}
