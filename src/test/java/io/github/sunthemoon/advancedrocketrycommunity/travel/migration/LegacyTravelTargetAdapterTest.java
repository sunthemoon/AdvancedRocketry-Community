package io.github.sunthemoon.advancedrocketrycommunity.travel.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class LegacyTravelTargetAdapterTest {
    private static final UUID STATION = UUID.fromString("123e4567-e89b-42d3-a456-426614174000");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void mapsAllThreeV1DestinationsExactly() {
        assertEquals(new TravelTarget.BodySurface(CelestialIds.EARTH_ID),
                LegacyTravelTargetAdapter.fromLegacy(RocketDestination.EARTH, null));
        assertEquals(new TravelTarget.BodySurface(CelestialIds.MOON_ID),
                LegacyTravelTargetAdapter.fromLegacy(RocketDestination.MOON, null));
        assertEquals(new TravelTarget.Station(STATION),
                LegacyTravelTargetAdapter.fromLegacy(RocketDestination.SPACE_STATION, STATION));
    }

    @Test
    void rejectsMalformedLegacyShapes() {
        assertThrows(NullPointerException.class,
                () -> LegacyTravelTargetAdapter.fromLegacy(RocketDestination.SPACE_STATION, null));
        assertThrows(IllegalArgumentException.class,
                () -> LegacyTravelTargetAdapter.fromLegacy(RocketDestination.EARTH, STATION));
    }

    @Test
    void mapsOnlyTargetsSupportedByTheTemporaryV1Adapter() {
        assertEquals(RocketDestination.EARTH,
                LegacyTravelTargetAdapter.toLegacy(new TravelTarget.BodySurface(CelestialIds.EARTH_ID))
                        .orElseThrow().destination());
        assertEquals(STATION,
                LegacyTravelTargetAdapter.toLegacy(new TravelTarget.Station(STATION))
                        .orElseThrow().stationId());
        assertTrue(LegacyTravelTargetAdapter.toLegacy(
                new TravelTarget.BodySurface(ModIdentity.id("test_mars"))).isEmpty());
        assertTrue(LegacyTravelTargetAdapter.toLegacy(new TravelTarget.Orbit(CelestialIds.EARTH_ID)).isEmpty());
        assertTrue(LegacyTravelTargetAdapter.toLegacy(new TravelTarget.Mission(STATION)).isEmpty());
    }
}
