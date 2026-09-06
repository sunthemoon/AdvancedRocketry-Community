package io.github.sunthemoon.advancedrocketrycommunity.travel.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LegacyFlightTargetMigratorTest {
    private static final UUID EARTH_STATION = UUID.fromString("123e4567-e89b-42d3-a456-426614174010");
    private static final UUID MOON_STATION = UUID.fromString("123e4567-e89b-42d3-a456-426614174011");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void directEarthAndMoonLocationsBecomeSurfaceTargets() {
        Fixture fixture = fixture();

        assertEquals(
                new TravelTarget.BodySurface(CelestialIds.EARTH_ID),
                fixture.migrator.currentLocation(
                        CelestialIds.EARTH_ID,
                        Level.OVERWORLD.location(),
                        new WorldLocation(Level.OVERWORLD, BlockPos.ZERO)
                ).target().orElseThrow()
        );
        assertEquals(
                new TravelTarget.BodySurface(CelestialIds.MOON_ID),
                fixture.migrator.currentLocation(
                        CelestialIds.MOON_ID,
                        CelestialIds.MOON_LEVEL.location(),
                        new WorldLocation(CelestialIds.MOON_LEVEL, BlockPos.ZERO)
                ).target().orElseThrow()
        );
    }

    @Test
    void sharedSpacePositionsResolveToTheirCommittedStationTargets() {
        Fixture fixture = fixture();

        assertEquals(
                new TravelTarget.Station(EARTH_STATION),
                fixture.migrator.currentLocation(
                        CelestialIds.SPACE_ID,
                        CelestialIds.SPACE_LEVEL.location(),
                        atStation(fixture.earthStation)
                ).target().orElseThrow()
        );
        assertEquals(
                new TravelTarget.Station(MOON_STATION),
                fixture.migrator.currentLocation(
                        CelestialIds.SPACE_ID,
                        CelestialIds.SPACE_LEVEL.location(),
                        atStation(fixture.moonStation)
                ).target().orElseThrow()
        );
    }

    @Test
    void sharedSpaceGapPreservesLegacyIdentityAndReportsStableReason() {
        Fixture fixture = fixture();
        TravelTargetMigrationResult result = fixture.migrator.currentLocation(
                CelestialIds.SPACE_ID,
                CelestialIds.SPACE_LEVEL.location(),
                new WorldLocation(CelestialIds.SPACE_LEVEL, new BlockPos(Integer.MAX_VALUE, 64, 0))
        );

        assertEquals(TravelTargetMigrationCode.UNRESOLVED_SHARED_LEVEL, result.code());
        assertTrue(result.target().isEmpty());
    }

    @Test
    void unknownOrMismatchedLegacyLocationFailsClosed() {
        Fixture fixture = fixture();
        ResourceLocation deleted = ModIdentity.id("deleted");

        assertEquals(
                TravelTargetMigrationCode.UNKNOWN_LEGACY_LOCATION,
                fixture.migrator.currentLocation(
                        deleted,
                        Level.OVERWORLD.location(),
                        new WorldLocation(Level.OVERWORLD, BlockPos.ZERO)
                ).code()
        );
        assertEquals(
                TravelTargetMigrationCode.UNKNOWN_LEGACY_LOCATION,
                fixture.migrator.currentLocation(
                        CelestialIds.EARTH_ID,
                        Level.OVERWORLD.location(),
                        new WorldLocation(CelestialIds.MOON_LEVEL, BlockPos.ZERO)
                ).code()
        );
    }

    @Test
    void legacyPlanDestinationsMapOnlyWhenTheirShapeIsAuthoritative() {
        Fixture fixture = fixture();

        assertEquals(
                new TravelTarget.BodySurface(CelestialIds.MOON_ID),
                fixture.migrator.destination(
                        CelestialIds.MOON_ID,
                        CelestialIds.MOON_LEVEL.location(),
                        null
                ).target().orElseThrow()
        );
        assertEquals(
                new TravelTarget.Station(EARTH_STATION),
                fixture.migrator.destination(
                        CelestialIds.SPACE_ID,
                        CelestialIds.SPACE_LEVEL.location(),
                        EARTH_STATION
                ).target().orElseThrow()
        );
        assertEquals(
                TravelTargetMigrationCode.INVALID_LEGACY_STATION_TARGET,
                fixture.migrator.destination(
                        CelestialIds.SPACE_ID,
                        CelestialIds.SPACE_LEVEL.location(),
                        null
                ).code()
        );
        assertEquals(
                TravelTargetMigrationCode.UNKNOWN_DESTINATION_BODY,
                fixture.migrator.destination(
                        ModIdentity.id("deleted"),
                        ModIdentity.id("deleted_level"),
                        null
                ).code()
        );
    }

    private static Fixture fixture() {
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        if (!catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions()))) {
            throw new AssertionError("Default catalog rejected");
        }
        StationRegistryModel stations = new StationRegistryModel();
        stations.reserve(EARTH_STATION, UUID.randomUUID(), "Earth", CelestialIds.EARTH_ID, 0L);
        stations.reserve(MOON_STATION, UUID.randomUUID(), "Moon", CelestialIds.MOON_ID, 0L);
        StationState earth = stations.commit(EARTH_STATION);
        StationState moon = stations.commit(MOON_STATION);
        BodyContextResolver contexts = new BodyContextResolver(
                catalogs,
                List.of(new StationRegionBodyContextResolver(stations::findAt))
        );
        return new Fixture(
                new LegacyFlightTargetMigrator(catalogs, contexts),
                earth,
                moon
        );
    }

    private static WorldLocation atStation(StationState station) {
        return new WorldLocation(CelestialIds.SPACE_LEVEL, new BlockPos(
                station.landingPad().x(),
                station.landingPad().y(),
                station.landingPad().z()
        ));
    }

    private record Fixture(
            LegacyFlightTargetMigrator migrator,
            StationState earthStation,
            StationState moonStation
    ) {
    }
}
