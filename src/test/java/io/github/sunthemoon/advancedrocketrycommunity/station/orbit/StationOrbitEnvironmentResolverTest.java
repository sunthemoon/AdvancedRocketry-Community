package io.github.sunthemoon.advancedrocketrycommunity.station.orbit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class StationOrbitEnvironmentResolverTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void stationOrbitUsesStoredGravityAndTheOrbitedBodysSolarIntensity() {
        CelestialCatalog catalog = catalog(2.5D);
        StationState station = station(CelestialIds.MOON_ID).withGravityMilli(350);
        StationOrbitEnvironment environment = StationOrbitEnvironmentResolver.resolve(station, Optional.of(catalog));
        assertEquals(station.stationId(), environment.stationId());
        assertEquals(CelestialIds.MOON_ID, environment.orbitBody());
        assertTrue(environment.orbitBodyAvailable());
        assertEquals(0.35D, environment.configuredGravity());
        assertEquals(0.35D, environment.effectiveGravity());
        assertFalse(environment.gravityClamped());
        assertTrue(environment.vacuum());
        assertEquals(2.5D, environment.solarIntensity());
        assertEquals(270.0D, environment.sunAngleDegrees());
    }

    @Test
    void configuredGravityAboveTheCelestialBoundIsClampedOnlyForPhysics() {
        StationState station = station(CelestialIds.EARTH_ID).withGravityMilli(10_000);
        StationOrbitEnvironment environment = StationOrbitEnvironmentResolver.resolve(station, Optional.of(catalog(1.0D)));
        assertEquals(10.0D, environment.configuredGravity());
        assertEquals(CelestialBodyDefinition.MAX_GRAVITY_MULTIPLIER, environment.effectiveGravity());
        assertTrue(environment.gravityClamped());
        StationState atBound = station(CelestialIds.EARTH_ID).withGravityMilli(4_000);
        assertFalse(StationOrbitEnvironmentResolver.resolve(atBound, Optional.of(catalog(1.0D))).gravityClamped());
    }

    @Test
    void missingOrbitBodyFallsBackToSharedSpaceSolarWithoutRewritingTheStation() {
        CelestialCatalog catalog = catalog(2.5D);
        double spaceSolar = catalog.get(CelestialIds.SPACE_ID).orElseThrow().solarIntensity();
        StationState station = station(ModIdentity.id("removed_body")).withGravityMilli(200);
        StationOrbitEnvironment environment = StationOrbitEnvironmentResolver.resolve(station, Optional.of(catalog));
        assertFalse(environment.orbitBodyAvailable());
        assertEquals(ModIdentity.id("removed_body"), environment.orbitBody());
        assertEquals(spaceSolar, environment.solarIntensity());
        assertEquals(0.2D, environment.effectiveGravity());
        StationOrbitEnvironment noCatalog = StationOrbitEnvironmentResolver.resolve(station, Optional.empty());
        assertFalse(noCatalog.orbitBodyAvailable());
        assertEquals(0.0D, noCatalog.solarIntensity());
    }

    @Test
    void basicSpaceStationsKeepTodaysZeroGravity() {
        StationOrbitEnvironment environment = StationOrbitEnvironmentResolver.resolve(
                station(CelestialIds.EARTH_ID), Optional.of(catalog(1.0D)));
        assertEquals(0.0D, environment.effectiveGravity());
        assertEquals(0.0D, environment.configuredGravity());
    }

    @Test
    void effectiveGravityMustNotExceedConfiguredGravity() {
        assertThrows(IllegalArgumentException.class, () -> new StationOrbitEnvironment(UUID.randomUUID(), "x",
                CelestialIds.EARTH_ID, true, 0.1D, 0.2D, true, 1.0D, 0.0D));
    }

    private static StationState station(ResourceLocation orbit) {
        return StationState.fromReservation(new StationReservation(UUID.randomUUID(), UUID.randomUUID(),
                "Orbit", new StationGridCell(2, -1), orbit, 0));
    }

    /** Default bodies with the Moon's solar intensity replaced. */
    private static CelestialCatalog catalog(double moonSolar) {
        List<CelestialBodyDefinition> bodies = new ArrayList<>();
        for (CelestialBodyDefinition body : CelestialDefaults.definitions()) {
            bodies.add(!body.id().equals(CelestialIds.MOON_ID) ? body : new CelestialBodyDefinition(body.id(),
                    body.parentId(), body.levelKey(), body.gravityMultiplier(), body.atmosphere(), body.orbit(),
                    body.visualProfile(), body.capabilities(), moonSolar, body.radiation(), body.environmentEffects()));
        }
        return CelestialCatalog.create(bodies).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
    }
}
