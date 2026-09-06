package io.github.sunthemoon.advancedrocketrycommunity.celestial.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BodyContextResolverTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void unambiguousLevelResolvesToSurfaceWithoutReadingWorldState() {
        BodyContextResolver resolver = new BodyContextResolver(loadedCatalogs(), List.of());

        assertEquals(
                BodyContext.surface(CelestialIds.MOON_ID),
                resolver.resolve(new WorldLocation(CelestialIds.MOON_LEVEL, new BlockPos(1, 2, 3)))
                        .orElseThrow()
        );
    }

    @Test
    void committedStationsResolveDifferentOrbitBodiesInsideSharedSpace() {
        StationRegistryModel stations = new StationRegistryModel();
        StationState earthStation = createStation(stations, "Earth", CelestialIds.EARTH_ID);
        StationState moonStation = createStation(stations, "Moon", CelestialIds.MOON_ID);
        BodyContextResolver resolver = resolver(stations, loadedCatalogs());

        assertEquals(
                BodyContext.stationOrbit(CelestialIds.EARTH_ID, earthStation.stationId()),
                resolver.resolve(atLandingPad(earthStation)).orElseThrow()
        );
        assertEquals(
                BodyContext.stationOrbit(CelestialIds.MOON_ID, moonStation.stationId()),
                resolver.resolve(atLandingPad(moonStation)).orElseThrow()
        );
    }

    @Test
    void spaceGapAndReservationFailClosedInsteadOfFallingBackToSpaceBody() {
        StationRegistryModel stations = new StationRegistryModel();
        StationReservation reservation = stations.reserve(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Reserved",
                CelestialIds.EARTH_ID,
                0L
        );
        BodyContextResolver resolver = resolver(stations, loadedCatalogs());

        assertTrue(resolver.resolve(new WorldLocation(
                CelestialIds.SPACE_LEVEL,
                new BlockPos(reservation.landingPad().x(), reservation.landingPad().y(), reservation.landingPad().z())
        )).isEmpty());
        assertTrue(resolver.resolve(new WorldLocation(
                CelestialIds.SPACE_LEVEL,
                new BlockPos(Integer.MAX_VALUE, 0, Integer.MAX_VALUE)
        )).isEmpty());
    }

    @Test
    void regionBoundaryIsInclusiveAndOutsidePositionIsUnresolved() {
        StationRegistryModel stations = new StationRegistryModel();
        StationState station = createStation(stations, "Boundary", CelestialIds.EARTH_ID);
        BodyContextResolver resolver = resolver(stations, loadedCatalogs());

        assertTrue(resolver.resolve(new WorldLocation(
                CelestialIds.SPACE_LEVEL,
                new BlockPos(station.region().maximumX(), 0, station.region().maximumZ())
        )).isPresent());
        assertTrue(resolver.resolve(new WorldLocation(
                CelestialIds.SPACE_LEVEL,
                new BlockPos(station.region().maximumX() + 1, 0, station.region().maximumZ())
        )).isEmpty());
    }

    @Test
    void deletedOrUnknownStationBodyDoesNotFallBackToLevelBody() {
        StationRegistryModel stations = new StationRegistryModel();
        StationState unknownBody = createStation(stations, "Unknown", ModIdentity.id("missing_body"));
        BodyContextResolver resolver = resolver(stations, loadedCatalogs());

        assertTrue(resolver.resolve(atLandingPad(unknownBody)).isEmpty());
        stations.delete(unknownBody.stationId());
        assertTrue(resolver.resolve(atLandingPad(unknownBody)).isEmpty());
    }

    @Test
    void resolverRequiresLoadedCatalogAndBoundsResolverCount() {
        assertTrue(new BodyContextResolver(new CelestialCatalogManager(), List.of())
                .resolve(new WorldLocation(Level.OVERWORLD, BlockPos.ZERO))
                .isEmpty());

        ArrayList<InstanceBodyContextResolver> tooMany = new ArrayList<>();
        for (int index = 0; index <= BodyContextResolver.MAX_INSTANCE_RESOLVERS; index++) {
            tooMany.add(location -> BodyContextResolution.unhandled());
        }
        assertThrows(IllegalArgumentException.class,
                () -> new BodyContextResolver(loadedCatalogs(), tooMany));
    }

    @Test
    void authoritativeUnresolvedResultStopsLaterResolversAndLevelFallback() {
        InstanceBodyContextResolver later = location -> BodyContextResolution.resolved(
                BodyContext.surface(CelestialIds.EARTH_ID)
        );
        BodyContextResolver resolver = new BodyContextResolver(
                loadedCatalogs(),
                List.of(location -> BodyContextResolution.unresolved(), later)
        );

        assertTrue(resolver.resolve(new WorldLocation(Level.OVERWORLD, BlockPos.ZERO)).isEmpty());
    }

    @Test
    void worldLocationCopiesMutablePosition() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        WorldLocation location = new WorldLocation(Level.OVERWORLD, mutable);
        mutable.set(4, 5, 6);

        assertEquals(new BlockPos(1, 2, 3), location.position());
    }

    private static BodyContextResolver resolver(
            StationRegistryModel stations,
            CelestialCatalogManager catalogs
    ) {
        return new BodyContextResolver(
                catalogs,
                List.of(new StationRegionBodyContextResolver(stations::findAt))
        );
    }

    private static CelestialCatalogManager loadedCatalogs() {
        CelestialCatalogManager manager = new CelestialCatalogManager();
        assertTrue(manager.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions())));
        return manager;
    }

    private static StationState createStation(
            StationRegistryModel stations,
            String name,
            net.minecraft.resources.ResourceLocation orbitBody
    ) {
        UUID stationId = UUID.randomUUID();
        stations.reserve(stationId, UUID.randomUUID(), name, orbitBody, 0L);
        return stations.commit(stationId);
    }

    private static WorldLocation atLandingPad(StationState station) {
        return new WorldLocation(CelestialIds.SPACE_LEVEL, new BlockPos(
                station.landingPad().x(),
                station.landingPad().y(),
                station.landingPad().z()
        ));
    }
}
