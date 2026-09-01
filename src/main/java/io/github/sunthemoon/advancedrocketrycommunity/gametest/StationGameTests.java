package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
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
        StationCreationService creation = new StationCreationService(platforms);
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
        StationManager manager = new StationManager();
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
        StationCreationResult blocked = new StationCreationService(new StationPlatformGenerator()).create(
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
}
