package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Long-running v0.6 integration scenarios kept separate from the v0.5 transaction suite. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFlightGameTests {
    private static final String TEMPLATE = "rocket_test";
    private RocketFlightGameTests() {
    }

    @GameTest(template = TEMPLATE, batch = "station_flight", timeoutTicks = 800)
    public static void earthStationEarthUsesApprovedPadAndPreservesNeighborSpace(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        ServerLevel space = earth.getServer().getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space Level is unavailable");
        clearTransferJournal(earth);
        helper.runAfterDelay(40, () -> {
            UUID owner = UUID.randomUUID();
            StationPlatformGenerator platforms = new StationPlatformGenerator();
            StationState station = new StationCreationService(platforms, bodyId -> true).create(
                    earth.getServer(),
                    owner,
                    "Flight Test Station",
                    CelestialIds.EARTH_ID,
                    false
            ).station().orElseThrow();
            helper.assertTrue(platforms.intact(space, station.cell()),
                    "Station platform was not generated exactly");

            RocketEntity outbound = assembleFueledRocket(helper, new BlockPos(3, 2, 3), owner);
            UUID logical = outbound.assemblyTransactionId().orElseThrow();
            long initialFuel = outbound.flightData().orElseThrow().fuel().amount();
            RocketFlightRequestResult outward = RocketRuntime.requestAdminStationFlight(
                    outbound,
                    station.stationId(),
                    UUID.randomUUID()
            );
            helper.assertTrue(outward.success(), "Earth-to-station launch failed: " + outward.code());

            helper.runAfterDelay(270, () -> {
                RocketEntity landedSpace = findLogicalRocket(space, logical);
                helper.assertTrue(landedSpace != null, "Station transfer did not create a Space rocket");
                helper.assertTrue(
                        landedSpace.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                        "Station rocket did not land"
                );
                helper.assertTrue(
                        station.region().contains(
                                landedSpace.blockPosition().getX(),
                                landedSpace.blockPosition().getZ()
                        ),
                        "Station rocket landed outside its committed region"
                );
                helper.assertTrue(
                        landedSpace.snapshot().orElseThrow().sourceOrigin().y()
                                + landedSpace.snapshot().orElseThrow().bounds().minimum().y()
                                == station.landingPad().y(),
                        "Station rocket did not use the approved landing elevation"
                );
                helper.assertTrue(platforms.intact(space, station.cell()),
                        "Rocket landing changed the station platform");
                helper.assertTrue(
                        landedSpace.flightData().orElseThrow().fuel().amount()
                                == initialFuel - outward.requiredFuel(),
                        "Station outbound fuel debit changed"
                );

                var remaining = landedSpace.flightData().orElseThrow().fuel();
                landedSpace.updateFlightData(landedSpace.flightData().orElseThrow().withFuel(
                        remaining,
                        space.getGameTime()
                ));
                RocketFlightRequestResult returning = RocketRuntime.requestAdminFlight(
                        landedSpace,
                        RocketDestination.EARTH,
                        UUID.randomUUID()
                );
                helper.assertTrue(returning.success(),
                        "Station-to-Earth launch failed: " + returning.code());

                helper.runAfterDelay(270, () -> {
                    RocketEntity landedEarth = findLogicalRocket(earth, logical);
                    helper.assertTrue(landedEarth != null, "Station return did not create an Earth rocket");
                    helper.assertTrue(
                            landedEarth.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                            "Station return rocket did not land"
                    );
                    helper.assertTrue(findLogicalRocket(space, logical) == null,
                            "Space source survived committed return");
                    RocketStructureSnapshot returned = landedEarth.snapshot().orElseThrow();
                    RocketTransactionResult cleanup = new RocketDisassemblyTransaction(
                            new ServerLevelRocketTransactionWorld(
                                    earth,
                                    RocketBlockEntityAdapters.defaults(),
                                    owner
                            ),
                            new RocketRegionLockManager(),
                            new RocketOperationLedger(),
                            RocketTransactionJournal.NO_OP
                    ).execute(UUID.randomUUID(), landedEarth.getUUID(), returned);
                    helper.assertTrue(cleanup.success(), "Station-return rocket cleanup failed");
                    clearSnapshotBlocks(earth, returned);

                    StationRegistrySavedData data = StationRegistrySavedData.get(earth.getServer());
                    helper.assertTrue(data.delete(station.stationId()).isPresent(),
                            "Station cleanup did not release its committed cell");
                    platforms.removeTemplate(space, station.cell());
                    data.flush(earth.getServer());
                    clearTransferJournal(earth);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = "flight", timeoutTicks = 1_000)
    public static void earthMoonRoundTripConservesFuelAndBlockedPadReturnsSource(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        ServerLevel moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        clearTransferJournal(earth);
        primePadChunks(earth);
        primePadChunks(moon);
        clearPadRockets(earth);
        clearPadRockets(moon);
        helper.runAfterDelay(20, () -> {
            clearPadRockets(earth);
            clearPadRockets(moon);
        });
        helper.runAfterDelay(50, () -> {
            clearPadRockets(earth);
            clearPadRockets(moon);
        });
        helper.runAfterDelay(80, () -> {
            clearPadRockets(earth);
            clearPadRockets(moon);
            startRoundTrip(helper, earth, moon);
        });
    }

    @GameTest(template = TEMPLATE, batch = "flight_concurrency", timeoutTicks = 500)
    public static void simultaneousRocketsUseDisjointAuthorityAndPads(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        ServerLevel moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        clearTransferJournal(earth);
        primePadChunks(earth);
        primePadChunks(moon);
        helper.runAfterDelay(40, () -> {
            clearPadRockets(earth);
            clearPadRockets(moon);
            RocketEntity first = assembleFueledRocket(helper, new BlockPos(3, 2, 3), UUID.randomUUID());
            RocketEntity second = assembleFueledRocket(helper, new BlockPos(12, 2, 3), UUID.randomUUID());
            UUID firstLogical = first.assemblyTransactionId().orElseThrow();
            UUID secondLogical = second.assemblyTransactionId().orElseThrow();
            long firstFuel = first.flightData().orElseThrow().fuel().amount();
            long secondFuel = second.flightData().orElseThrow().fuel().amount();
            UUID firstTransfer = UUID.randomUUID();
            UUID secondTransfer = UUID.randomUUID();
            RocketFlightRequestResult firstLaunch = RocketRuntime.requestAdminFlight(
                    first,
                    RocketDestination.MOON,
                    firstTransfer
            );
            RocketFlightRequestResult secondLaunch = RocketRuntime.requestAdminFlight(
                    second,
                    RocketDestination.MOON,
                    secondTransfer
            );
            helper.assertTrue(firstLaunch.success(), "First concurrent launch failed: " + firstLaunch.code());
            helper.assertTrue(secondLaunch.success(), "Second concurrent launch failed: " + secondLaunch.code());
            RocketTransferSavedData journal = RocketTransferSavedData.get(earth.getServer());
            var firstRecord = journal.find(firstTransfer).orElseThrow();
            var secondRecord = journal.find(secondTransfer).orElseThrow();
            helper.assertTrue(
                    !RocketRegion.fromSnapshot(firstRecord.destinationSnapshot())
                            .overlaps(RocketRegion.fromSnapshot(secondRecord.destinationSnapshot())),
                    "Concurrent transfers reserved an overlapping Moon pad"
            );

            helper.runAfterDelay(270, () -> {
                RocketEntity firstLanded = findLogicalRocket(moon, firstLogical);
                RocketEntity secondLanded = findLogicalRocket(moon, secondLogical);
                helper.assertTrue(firstLanded != null && secondLanded != null,
                        "Concurrent transfer lost a destination rocket");
                helper.assertTrue(!firstLanded.getUUID().equals(secondLanded.getUUID()),
                        "Concurrent transfer aliased destination identities");
                helper.assertTrue(
                        firstLanded.flightData().orElseThrow().state() == RocketFlightState.LANDED
                                && secondLanded.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                        "Concurrent destination did not reach LANDED"
                );
                helper.assertTrue(
                        firstLanded.flightData().orElseThrow().fuel().amount()
                                == firstFuel - firstLaunch.requiredFuel(),
                        "First concurrent transfer fuel changed"
                );
                helper.assertTrue(
                        secondLanded.flightData().orElseThrow().fuel().amount()
                                == secondFuel - secondLaunch.requiredFuel(),
                        "Second concurrent transfer fuel changed"
                );
                helper.assertTrue(findLogicalRocket(earth, firstLogical) == null
                                && findLogicalRocket(earth, secondLogical) == null,
                        "Concurrent source authority survived commit");
                helper.assertTrue(journal.entries().size() == 2,
                        "Concurrent landed reservations were not independently retained");
                firstLanded.discard();
                secondLanded.discard();
                clearTransferJournal(earth);
                helper.succeed();
            });
        });
    }

    private static void startRoundTrip(
            GameTestHelper helper,
            ServerLevel earth,
            ServerLevel moon
    ) {
        UUID owner = UUID.randomUUID();
        BlockPos origin = new BlockPos(3, 2, 3);
        RocketEntity outbound = assembleFueledRocket(helper, origin, owner);
        UUID logical = outbound.assemblyTransactionId().orElseThrow();
        long initialFuel = outbound.flightData().orElseThrow().fuel().amount();
        UUID outwardId = UUID.randomUUID();
        RocketFlightRequestResult outward = RocketRuntime.requestAdminFlight(
                outbound,
                RocketDestination.MOON,
                outwardId
        );
        helper.assertTrue(outward.success(), "Earth-to-Moon launch failed: " + outward.code());

        helper.runAfterDelay(270, () -> {
            RocketEntity landedMoon = findLogicalRocket(moon, logical);
            helper.assertTrue(landedMoon != null, "Earth-to-Moon transfer did not create a Moon rocket");
            helper.assertTrue(
                    landedMoon.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                    "Moon rocket did not land"
            );
            helper.assertTrue(
                    landedMoon.flightData().orElseThrow().fuel().amount()
                            == initialFuel - outward.requiredFuel(),
                    "Earth-to-Moon transfer did not debit fuel exactly once"
            );
            helper.assertTrue(findLogicalRocket(earth, logical) == null,
                    "Earth source survived committed transfer");

            var landedFuel = landedMoon.flightData().orElseThrow().fuel();
            landedMoon.updateFlightData(landedMoon.flightData().orElseThrow().withFuel(
                    landedFuel,
                    moon.getGameTime()
            ));
            helper.assertTrue(
                    landedMoon.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                    "Moon rocket did not accept post-landing fuel state"
            );

            UUID returnId = UUID.randomUUID();
            RocketFlightRequestResult returning = RocketRuntime.requestAdminFlight(
                    landedMoon,
                    RocketDestination.EARTH,
                    returnId
            );
            helper.assertTrue(returning.success(), "Moon-to-Earth launch failed: " + returning.code());

            helper.runAfterDelay(270, () -> {
                RocketEntity landedEarth = findLogicalRocket(earth, logical);
                helper.assertTrue(landedEarth != null, "Moon-to-Earth transfer did not create an Earth rocket");
                helper.assertTrue(
                        landedEarth.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                        "Returned Earth rocket did not land"
                );
                helper.assertTrue(
                        landedEarth.flightData().orElseThrow().fuel().amount()
                                == initialFuel - outward.requiredFuel() - returning.requiredFuel(),
                        "Round trip fuel accounting changed"
                );
                helper.assertTrue(findLogicalRocket(moon, logical) == null,
                        "Moon source survived committed return transfer");
                RocketStructureSnapshot returnedSnapshot = landedEarth.snapshot().orElseThrow();
                ServerLevelRocketTransactionWorld earthWorld = new ServerLevelRocketTransactionWorld(
                        earth,
                        RocketBlockEntityAdapters.defaults(),
                        owner
                );
                RocketTransactionResult disassembled = new RocketDisassemblyTransaction(
                        earthWorld,
                        new RocketRegionLockManager(),
                        new RocketOperationLedger(),
                        RocketTransactionJournal.NO_OP
                ).execute(UUID.randomUUID(), landedEarth.getUUID(), returnedSnapshot);
                helper.assertTrue(disassembled.success(), "Returned rocket did not disassemble exactly");
                assertSnapshotBlocks(helper, earthWorld, returnedSnapshot);
                clearSnapshotBlocks(earth, returnedSnapshot);

                startBlockedPadCase(helper, earth, moon);
            });
        });
    }

    private static void startBlockedPadCase(
            GameTestHelper helper,
            ServerLevel earth,
            ServerLevel moon
    ) {
        UUID owner = UUID.randomUUID();
        BlockPos origin = new BlockPos(12, 2, 3);
        RocketEntity source = assembleFueledRocket(helper, origin, owner);
        UUID logical = source.assemblyTransactionId().orElseThrow();
        long fuelBefore = source.flightData().orElseThrow().fuel().amount();
        UUID transferId = UUID.randomUUID();
        RocketFlightRequestResult launch = RocketRuntime.requestAdminFlight(
                source,
                RocketDestination.MOON,
                transferId
        );
        helper.assertTrue(launch.success(), "Blocked-pad setup launch failed: " + launch.code());
        var record = RocketTransferSavedData.get(earth.getServer()).find(transferId).orElseThrow();
        var firstBlock = record.destinationSnapshot().blocks().get(0);
        RocketPosition blocked = record.destinationSnapshot().sourceOrigin().add(firstBlock.position());
        BlockPos blockedPosition = new BlockPos(blocked.x(), blocked.y(), blocked.z());
        moon.setBlock(blockedPosition, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);

        helper.runAfterDelay(190, () -> {
            RocketEntity recovered = findLogicalRocket(earth, logical);
            helper.assertTrue(recovered != null, "Blocked destination lost the source rocket");
            helper.assertTrue(
                    recovered.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                    "Blocked destination did not return the source to FUELED"
            );
            helper.assertTrue(
                    recovered.flightData().orElseThrow().fuel().amount() == fuelBefore,
                    "Blocked destination consumed source fuel"
            );
            helper.assertTrue(findLogicalRocket(moon, logical) == null,
                    "Blocked destination created a Moon authority");
            helper.assertTrue(
                    RocketTransferSavedData.get(earth.getServer()).find(transferId).isEmpty(),
                    "Blocked destination left an active transfer journal"
            );
            moon.setBlock(blockedPosition, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            RocketStructureSnapshot snapshot = recovered.snapshot().orElseThrow();
            RocketTransactionResult cleanup = new RocketDisassemblyTransaction(
                    new ServerLevelRocketTransactionWorld(
                            earth,
                            RocketBlockEntityAdapters.defaults(),
                            owner
                    ),
                    new RocketRegionLockManager(),
                    new RocketOperationLedger(),
                    RocketTransactionJournal.NO_OP
            ).execute(UUID.randomUUID(), recovered.getUUID(), snapshot);
            helper.assertTrue(cleanup.success(), "Blocked-pad source cleanup failed");
            clearSnapshotBlocks(earth, snapshot);
            clearTransferJournal(earth);
            helper.succeed();
        });
    }

}
