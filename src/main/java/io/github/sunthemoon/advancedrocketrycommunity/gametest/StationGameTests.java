package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContext;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.WorldLocation;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationGameTests {
    private StationGameTests() {
    }

    @GameTest(template = "empty", batch = "station", timeoutTicks = 300)
    public static void tenStationsPersistWithoutOverlapAndDeletionPreservesNeighbor(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        ServerLevel space = earth.getServer().getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space Level is unavailable");
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        StationCreationService creation = new StationCreationService(platforms, bodyId -> true);
        StationRegistrySavedData data = StationRegistrySavedData.get(earth.getServer());
        helper.assertTrue(data.operational(), "Station registry is blocked");

        ArrayList<StationState> created = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            StationCreationResult result = creation.create(
                    earth.getServer(),
                    UUID.nameUUIDFromBytes(("station-owner-" + index).getBytes()),
                    "Allocation " + index,
                    index % 2 == 0 ? CelestialIds.EARTH_ID : CelestialIds.MOON_ID,
                    false
            );
            helper.assertTrue(result.success(), "Station allocation failed: " + result.code());
            StationState station = result.station().orElseThrow();
            helper.assertTrue(platforms.intact(space, station.cell()),
                    "Station platform is incomplete");
            created.add(station);
        }
        for (int first = 0; first < created.size(); first++) {
            for (int second = first + 1; second < created.size(); second++) {
                helper.assertTrue(
                        !created.get(first).region().overlaps(created.get(second).region()),
                        "Committed station regions overlap"
                );
            }
        }

        StationRegistrySavedData restored = StationRegistrySavedData.load(data.save(new CompoundTag()));
        for (StationState station : created) {
            helper.assertTrue(
                    restored.find(station.stationId()).filter(station::equals).isPresent(),
                    "Station state changed across NBT round trip"
            );
        }

        StationState removed = created.get(0);
        StationState neighbor = created.get(1);
        RocketEntity occupyingRocket = ModEntities.ROCKET.get().create(space);
        helper.assertTrue(occupyingRocket != null, "Deletion guard rocket could not be created");
        occupyingRocket.setPos(
                removed.landingPad().x() + 0.5D,
                removed.landingPad().y(),
                removed.landingPad().z() + 0.5D
        );
        helper.assertTrue(space.addFreshEntity(occupyingRocket),
                "Deletion guard rocket could not be added");
        StationManager manager = new StationManager(defaultCatalogs());
        boolean rocketGuarded = false;
        try {
            manager.delete(
                    earth.getServer(),
                    removed.ownerId(),
                    false,
                    removed.stationId(),
                    "confirm"
            );
        } catch (IllegalStateException expected) {
            rocketGuarded = true;
        }
        helper.assertTrue(rocketGuarded, "Station deletion ignored a rocket authority in its region");
        occupyingRocket.discard();
        manager.delete(
                earth.getServer(),
                removed.ownerId(),
                false,
                removed.stationId(),
                "confirm"
        );
        helper.assertTrue(data.find(neighbor.stationId()).isPresent(),
                "Deleting one station removed its neighbor state");
        helper.assertTrue(platforms.intact(space, neighbor.cell()),
                "Deleting one station changed its neighbor platform");

        for (StationState station : created.subList(1, created.size())) {
            data.delete(station.stationId());
            platforms.removeTemplate(space, station.cell());
        }
        data.flush(earth.getServer());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "station", timeoutTicks = 120)
    public static void blockedGenerationReleasesReservationAndInvitationRemovalIsImmediate(
            GameTestHelper helper
    ) {
        ServerLevel earth = helper.getLevel();
        ServerLevel space = earth.getServer().getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space Level is unavailable");
        StationRegistrySavedData data = StationRegistrySavedData.get(earth.getServer());
        UUID probeId = UUID.randomUUID();
        StationReservation probe = data.reserve(
                probeId,
                UUID.randomUUID(),
                "Probe",
                CelestialIds.EARTH_ID,
                space.getGameTime()
        );
        StationGridCell expectedCell = probe.cell();
        data.release(probeId);
        BlockPos blocker = new BlockPos(
                expectedCell.centerX(),
                io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits.PLATFORM_Y,
                expectedCell.centerZ()
        );
        space.setBlock(blocker, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_ALL);
        int before = data.stations().size();
        StationCreationResult blocked = new StationCreationService(
                new StationPlatformGenerator(),
                bodyId -> true
        ).create(
                earth.getServer(),
                UUID.randomUUID(),
                "Blocked",
                CelestialIds.EARTH_ID,
                false
        );
        helper.assertTrue(blocked.code() == StationCreationCode.PLATFORM_BLOCKED,
                "Occupied platform did not fail closed");
        helper.assertTrue(data.stations().size() == before,
                "Failed generation committed station state");
        helper.assertTrue(data.reservations().stream().noneMatch(
                reservation -> reservation.cell().equals(expectedCell)
        ), "Failed generation leaked its reservation");
        space.setBlock(blocker, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        UUID stationId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        StationReservation permissionReservation = data.reserve(
                stationId, owner, "Permissions", CelestialIds.EARTH_ID, space.getGameTime()
        );
        StationState station = data.commit(stationId);
        data.invite(stationId, member);
        StationAccessService access = new StationAccessService();
        helper.assertTrue(!access.allowed(
                data.find(stationId).orElseThrow(), member, false, StationAccessAction.VISIT
        ), "Invitation granted authority before acceptance");
        data.acceptInvitation(stationId, member);
        helper.assertTrue(access.allowed(
                data.find(stationId).orElseThrow(), member, false, StationAccessAction.BUILD
        ), "Accepted member did not gain build access");
        data.removeMember(stationId, member);
        helper.assertTrue(!access.allowed(
                data.find(stationId).orElseThrow(), member, false, StationAccessAction.BUILD
        ), "Removed member retained cached build authority");
        data.delete(station.stationId());
        // No platform was generated for this direct registry-only permission state.
        data.flush(earth.getServer());
        helper.assertTrue(permissionReservation.cell().equals(station.cell()),
                "Permission station geometry changed at commit");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "station_context", timeoutTicks = 120)
    public static void sharedSpaceLocationsResolveTheirAuthoritativeOrbitBodies(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        StationRegistrySavedData data = StationRegistrySavedData.get(earth.getServer());
        helper.assertTrue(data.operational(), "Station registry is blocked");
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        helper.assertTrue(
                catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions())),
                "Celestial catalog could not be prepared"
        );
        BodyContextResolver resolver = new BodyContextResolver(
                catalogs,
                List.of(new StationRegionBodyContextResolver(data::findAt))
        );

        int stationCountBeforeRejectedCreation = data.stations().size();
        StationCreationResult unknownBody = new StationCreationService(
                new StationPlatformGenerator(),
                bodyId -> catalogs.current().flatMap(catalog -> catalog.get(bodyId)).isPresent()
        ).create(
                earth.getServer(),
                UUID.randomUUID(),
                "Unknown Context",
                ModIdentity.id("missing_body"),
                false
        );
        helper.assertTrue(
                unknownBody.code() == StationCreationCode.UNKNOWN_ORBIT_BODY,
                "Undefined orbit body was accepted"
        );
        helper.assertTrue(
                data.stations().size() == stationCountBeforeRejectedCreation,
                "Rejected orbit body changed station state"
        );

        UUID earthStationId = UUID.randomUUID();
        UUID moonStationId = UUID.randomUUID();
        data.reserve(earthStationId, UUID.randomUUID(), "Earth Context", CelestialIds.EARTH_ID, 0L);
        data.reserve(moonStationId, UUID.randomUUID(), "Moon Context", CelestialIds.MOON_ID, 0L);
        StationState earthStation = data.commit(earthStationId);
        StationState moonStation = data.commit(moonStationId);

        helper.assertTrue(
                resolver.resolve(worldLocation(earthStation)).filter(BodyContext.stationOrbit(
                        CelestialIds.EARTH_ID, earthStationId
                )::equals).isPresent(),
                "Earth station did not resolve its orbit context"
        );
        helper.assertTrue(
                resolver.resolve(worldLocation(moonStation)).filter(BodyContext.stationOrbit(
                        CelestialIds.MOON_ID, moonStationId
                )::equals).isPresent(),
                "Moon station did not resolve its orbit context"
        );
        helper.assertTrue(
                resolver.resolve(new WorldLocation(
                        CelestialIds.SPACE_LEVEL,
                        new BlockPos(Integer.MAX_VALUE, 0, Integer.MAX_VALUE)
                )).isEmpty(),
                "Unassigned Space position incorrectly inherited a body context"
        );

        RocketFlightData legacyStationFlight = legacySpaceFlight(earthStation);
        helper.assertTrue(
                RocketRuntime.migrateLegacyFlightData(
                        earth.getServer().getLevel(CelestialIds.SPACE_LEVEL),
                        legacyStationFlight
                ).flatMap(RocketFlightData::currentTarget)
                        .filter(new TravelTarget.Station(earthStationId)::equals)
                        .isPresent(),
                "Runtime migration did not bind the committed station identity"
        );
        RocketFlightData legacyGap = RocketFlightData.restore(
                1,
                UUID.randomUUID(),
                RocketFlightState.ASSEMBLED,
                RocketFuelState.empty(1_000L),
                null,
                RocketPassengerManifest.empty(1),
                CelestialIds.SPACE_ID,
                CelestialIds.SPACE_LEVEL.location(),
                new RocketPosition(Integer.MAX_VALUE, 0, Integer.MAX_VALUE),
                0L,
                null
        );
        helper.assertTrue(
                RocketRuntime.migrateLegacyFlightData(
                        earth.getServer().getLevel(CelestialIds.SPACE_LEVEL),
                        legacyGap
                ).isEmpty(),
                "Unassigned Space migration guessed a station or body"
        );

        data.delete(earthStationId);
        data.delete(moonStationId);
        data.flush(earth.getServer());
        helper.succeed();
    }

    private static WorldLocation worldLocation(StationState station) {
        return new WorldLocation(CelestialIds.SPACE_LEVEL, new BlockPos(
                station.landingPad().x(),
                station.landingPad().y(),
                station.landingPad().z()
        ));
    }

    private static RocketFlightData legacySpaceFlight(StationState station) {
        return RocketFlightData.restore(
                1,
                UUID.randomUUID(),
                RocketFlightState.ASSEMBLED,
                RocketFuelState.empty(1_000L),
                null,
                RocketPassengerManifest.empty(1),
                CelestialIds.SPACE_ID,
                CelestialIds.SPACE_LEVEL.location(),
                new RocketPosition(
                        station.landingPad().x(),
                        station.landingPad().y(),
                        station.landingPad().z()
                ),
                0L,
                null
        );
    }

    private static CelestialCatalogManager defaultCatalogs() {
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        if (!catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions()))) {
            throw new IllegalStateException("Default celestial catalog could not be prepared");
        }
        return catalogs;
    }
}
