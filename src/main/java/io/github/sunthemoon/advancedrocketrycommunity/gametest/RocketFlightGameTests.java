package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.util.FakePlayer;

/** Long-running v0.6 integration scenarios kept separate from the v0.5 transaction suite. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFlightGameTests {
    private static final String TEMPLATE = "rocket_test";
    private static final int PAD_SPACING = 64;
    private static final int[][] PAD_OFFSETS = {
            {0, 0},
            {PAD_SPACING, 0},
            {-PAD_SPACING, 0},
            {0, PAD_SPACING},
            {0, -PAD_SPACING},
            {PAD_SPACING, PAD_SPACING},
            {-PAD_SPACING, PAD_SPACING},
            {PAD_SPACING, -PAD_SPACING}
    };
    private static final TicketType<ChunkPos> PAD_CLEANUP_TICKET = TicketType.create(
            "arce_gametest_pad_cleanup",
            Comparator.comparingLong(chunk -> chunk.toLong()),
            200
    );

    private RocketFlightGameTests() {
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

    @GameTest(template = TEMPLATE, batch = "flight_security", timeoutTicks = 200)
    public static void hostileFlightIntentsCannotChangeAuthority(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        ServerLevel moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        clearTransferJournal(earth);
        primePadChunks(moon);
        helper.runAfterDelay(40, () -> {
            clearPadRockets(earth);
            clearPadRockets(moon);
            ServerPlayer owner = new FlightFakePlayer(earth);
            RocketEntity unauthorizedRocket = assembleFueledRocket(
                    helper,
                    new BlockPos(3, 2, 3),
                    UUID.randomUUID()
            );
            long unauthorizedFuel = unauthorizedRocket.flightData().orElseThrow().fuel().amount();
            owner.setPos(unauthorizedRocket.getX(), unauthorizedRocket.getY(), unauthorizedRocket.getZ());
            RocketRuntime.requestFlightIntent(
                    owner,
                    unauthorizedRocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.MOON,
                    UUID.randomUUID()
            );
            assertUnchangedSecurityState(
                    helper,
                    earth,
                    unauthorizedRocket,
                    unauthorizedFuel,
                    "unauthorized request"
            );
            unauthorizedRocket.discard();

            RocketEntity rocket = assembleFueledRocket(helper, new BlockPos(12, 2, 3), owner.getUUID());
            long fuelBefore = rocket.flightData().orElseThrow().fuel().amount();

            owner.setPos(rocket.getX() + 100.0D, rocket.getY(), rocket.getZ());
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.MOON,
                    UUID.randomUUID()
            );
            assertUnchangedSecurityState(helper, earth, rocket, fuelBefore, "far request");

            owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.EARTH,
                    UUID.randomUUID()
            );
            assertUnchangedSecurityState(helper, earth, rocket, fuelBefore, "invalid destination");

            UUID launchId = UUID.randomUUID();
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.MOON,
                    launchId
            );
            helper.assertTrue(
                    rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN,
                    "Valid owner launch did not start countdown"
            );
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.LAUNCH,
                    RocketDestination.MOON,
                    launchId
            );
            helper.assertTrue(
                    RocketTransferSavedData.get(earth.getServer()).entries().size() == 1,
                    "Replayed launch created another transfer"
            );
            RocketRuntime.requestFlightIntent(
                    owner,
                    rocket.getId(),
                    RocketFlightAction.CANCEL,
                    RocketDestination.MOON,
                    UUID.randomUUID()
            );
            helper.assertTrue(
                    rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                    "Countdown cancellation did not restore FUELED"
            );
            helper.assertTrue(
                    rocket.flightData().orElseThrow().fuel().amount() == fuelBefore,
                    "Hostile or cancelled request consumed fuel"
            );
            helper.assertTrue(
                    RocketTransferSavedData.get(earth.getServer()).entries().isEmpty(),
                    "Countdown cancellation retained a transfer journal"
            );
            rocket.discard();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = "flight_recovery", timeoutTicks = 400)
    public static void transferRecoveryReconcilesAllEntityPresenceCases(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        ServerLevel moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        clearTransferJournal(earth);
        primePadChunks(earth);
        primePadChunks(moon);
        helper.runAfterDelay(40, () -> {
            RocketManager recoveryManager = new RocketManager();

            RocketTransferRecord sourceOnly = prepareRecoveryScenario(
                    helper,
                    new BlockPos(3, 2, 3)
            );
            RocketTransferRecoveryReport sourceOnlyReport = recoveryManager.recoverTransfer(
                    earth.getServer(),
                    sourceOnly.transferId()
            );
            assertRecovery(
                    helper,
                    sourceOnlyReport,
                    RocketTransferRecoveryAction.KEEP_SOURCE,
                    1,
                    0,
                    "source-only"
            );
            assertSingleAuthority(helper, earth, moon, sourceOnly.logicalRocketId(), earth, "source-only");

            clearRecoveryScenario(earth, moon);
            RocketTransferRecord destinationOnly = prepareRecoveryScenario(
                    helper,
                    new BlockPos(6, 2, 3)
            );
            RocketEntity destinationOnlySource = findLogicalRocket(earth, destinationOnly.logicalRocketId());
            helper.assertTrue(destinationOnlySource != null, "Destination-only setup source is missing");
            destinationOnlySource.discard();
            spawnRecoveryDestination(moon, destinationOnly);
            RocketTransferRecoveryReport destinationOnlyReport = recoveryManager.recoverTransfer(
                    earth.getServer(),
                    destinationOnly.transferId()
            );
            assertRecovery(
                    helper,
                    destinationOnlyReport,
                    RocketTransferRecoveryAction.KEEP_DESTINATION,
                    0,
                    1,
                    "destination-only"
            );
            assertSingleAuthority(
                    helper,
                    earth,
                    moon,
                    destinationOnly.logicalRocketId(),
                    moon,
                    "destination-only"
            );

            clearRecoveryScenario(earth, moon);
            RocketTransferRecord both = prepareRecoveryScenario(helper, new BlockPos(9, 2, 3));
            spawnRecoveryDestination(moon, both);
            RocketTransferRecoveryReport bothReport = recoveryManager.recoverTransfer(
                    earth.getServer(),
                    both.transferId()
            );
            assertRecovery(
                    helper,
                    bothReport,
                    RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE,
                    1,
                    1,
                    "both"
            );
            assertSingleAuthority(helper, earth, moon, both.logicalRocketId(), earth, "both");

            clearRecoveryScenario(earth, moon);
            RocketTransferRecord neither = prepareRecoveryScenario(helper, new BlockPos(12, 2, 3));
            RocketEntity neitherSource = findLogicalRocket(earth, neither.logicalRocketId());
            helper.assertTrue(neitherSource != null, "Neither setup source is missing");
            neitherSource.discard();
            RocketTransferSavedData journal = RocketTransferSavedData.get(earth.getServer());
            journal.put(neither.destinationSpawned(UUID.randomUUID()));
            journal.flush(earth.getServer());
            RocketTransferRecoveryReport neitherReport = recoveryManager.recoverTransfer(
                    earth.getServer(),
                    neither.transferId()
            );
            assertRecovery(
                    helper,
                    neitherReport,
                    RocketTransferRecoveryAction.REBUILD_DESTINATION,
                    0,
                    0,
                    "neither"
            );
            assertSingleAuthority(helper, earth, moon, neither.logicalRocketId(), moon, "neither");

            clearRecoveryScenario(earth, moon);
            helper.succeed();
        });
    }

    private static RocketTransferRecord prepareRecoveryScenario(
            GameTestHelper helper,
            BlockPos origin
    ) {
        ServerLevel earth = helper.getLevel();
        RocketEntity source = assembleFueledRocket(helper, origin, UUID.randomUUID());
        UUID transferId = UUID.randomUUID();
        RocketFlightRequestResult launch = RocketRuntime.requestAdminFlight(
                source,
                RocketDestination.MOON,
                transferId
        );
        helper.assertTrue(launch.success(), "Recovery setup launch failed: " + launch.code());
        return RocketTransferSavedData.get(earth.getServer()).find(transferId).orElseThrow();
    }

    private static RocketEntity spawnRecoveryDestination(
            ServerLevel destinationLevel,
            RocketTransferRecord record
    ) {
        RocketEntity destination = ModEntities.ROCKET.get().create(destinationLevel);
        if (destination == null) {
            throw new IllegalStateException("Recovery destination entity type is unavailable");
        }
        destination.initializeTransferred(
                record.destinationSnapshot(),
                record.logicalRocketId(),
                record.ownerId(),
                record.destinationFlightData()
        );
        RocketPosition origin = record.destinationSnapshot().sourceOrigin();
        destination.setPos(
                origin.x() + 0.5D,
                origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                origin.z() + 0.5D
        );
        if (!destinationLevel.addFreshEntity(destination)) {
            throw new IllegalStateException("Recovery destination entity could not be spawned");
        }
        return destination;
    }

    private static void assertRecovery(
            GameTestHelper helper,
            RocketTransferRecoveryReport report,
            RocketTransferRecoveryAction expectedAction,
            int expectedSources,
            int expectedDestinations,
            String scenario
    ) {
        helper.assertTrue(
                report.status() == RocketTransferRecoveryReport.Status.RECOVERED,
                scenario + " recovery did not complete: " + report.status()
        );
        helper.assertTrue(
                report.action().filter(expectedAction::equals).isPresent(),
                scenario + " recovery selected " + report.action().orElse(null)
        );
        helper.assertTrue(
                report.sourceMatches() == expectedSources
                        && report.destinationMatches() == expectedDestinations,
                scenario + " recovery observed an unexpected presence matrix"
        );
    }

    private static void assertSingleAuthority(
            GameTestHelper helper,
            ServerLevel earth,
            ServerLevel moon,
            UUID logicalRocketId,
            ServerLevel expectedLevel,
            String scenario
    ) {
        int earthCount = countLogicalRockets(earth, logicalRocketId);
        int moonCount = countLogicalRockets(moon, logicalRocketId);
        helper.assertTrue(earthCount + moonCount == 1,
                scenario + " recovery did not leave exactly one rocket authority");
        helper.assertTrue(countLogicalRockets(expectedLevel, logicalRocketId) == 1,
                scenario + " recovery kept authority in the wrong dimension");
    }

    private static int countLogicalRockets(ServerLevel level, UUID logicalRocketId) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RocketEntity rocket
                    && rocket.operational()
                    && rocket.assemblyTransactionId().filter(logicalRocketId::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    private static void clearRecoveryScenario(ServerLevel earth, ServerLevel moon) {
        clearPadRockets(earth);
        clearPadRockets(moon);
        clearTransferJournal(earth);
    }

    /** Network-free server actor for authority and hostile-intent validation. */
    private static final class FlightFakePlayer extends FakePlayer {
        private FlightFakePlayer(ServerLevel level) {
            super(level, new GameProfile(
                    UUID.fromString("637c42c9-f7f6-4d42-b44c-40ded65e760f"),
                    "ARCEFlightTest"
            ));
        }
    }

    private static void assertUnchangedSecurityState(
            GameTestHelper helper,
            ServerLevel earth,
            RocketEntity rocket,
            long expectedFuel,
            String request
    ) {
        helper.assertTrue(
                rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED,
                request + " changed rocket state"
        );
        helper.assertTrue(
                rocket.flightData().orElseThrow().fuel().amount() == expectedFuel,
                request + " changed rocket fuel"
        );
        helper.assertTrue(
                RocketTransferSavedData.get(earth.getServer()).entries().isEmpty(),
                request + " created a transfer journal"
        );
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

    private static RocketEntity assembleFueledRocket(
            GameTestHelper helper,
            BlockPos origin,
            UUID owner
    ) {
        placeLegalRocket(helper, origin);
        helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        RocketStructureSnapshot snapshot = successfulSnapshot(helper, origin);
        ServerLevel level = helper.getLevel();
        RocketTransactionResult assembled = new RocketAssemblyTransaction(
                new ServerLevelRocketTransactionWorld(
                        level,
                        RocketBlockEntityAdapters.defaults(),
                        owner
                ),
                new RocketRegionLockManager(),
                new RocketOperationLedger(),
                RocketTransactionJournal.NO_OP
        ).execute(UUID.randomUUID(), snapshot);
        helper.assertTrue(assembled.success(), "Flight test rocket assembly failed: " + assembled.code());
        RocketEntity rocket = (RocketEntity) level.getEntity(assembled.rocketEntityId().orElseThrow());
        helper.assertTrue(rocket != null, "Flight test assembly did not spawn a rocket");
        var full = rocket.flightData().orElseThrow().fuel()
                .fill(rocket.flightData().orElseThrow().fuel().capacity()).state();
        rocket.updateFlightData(rocket.flightData().orElseThrow().withFuel(full, level.getGameTime()));
        return rocket;
    }

    private static RocketEntity findLogicalRocket(ServerLevel level, UUID logical) {
        primePadChunks(level);
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RocketEntity rocket
                    && rocket.operational()
                    && rocket.assemblyTransactionId().filter(logical::equals).isPresent()) {
                return rocket;
            }
        }
        return null;
    }

    private static void assertSnapshotBlocks(
            GameTestHelper helper,
            ServerLevelRocketTransactionWorld world,
            RocketStructureSnapshot snapshot
    ) {
        for (var block : snapshot.blocks()) {
            RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
            helper.assertTrue(
                    world.readBlock(absolute).filter(RocketWorldBlock.fromSnapshotBlock(block)::equals).isPresent(),
                    "Disassembly changed relocated block at " + absolute
            );
        }
    }

    private static void clearSnapshotBlocks(ServerLevel level, RocketStructureSnapshot snapshot) {
        for (var block : snapshot.blocks()) {
            RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
            level.setBlock(
                    new BlockPos(absolute.x(), absolute.y(), absolute.z()),
                    Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL
            );
        }
    }

    private static void clearTransferJournal(ServerLevel level) {
        RocketTransferSavedData data = RocketTransferSavedData.get(level.getServer());
        if (!data.operational()) {
            throw new IllegalStateException("Transfer journal is blocked during GameTest");
        }
        data.entries().forEach(record -> data.remove(record.transferId()));
        data.flush(level.getServer());
    }

    private static void primePadChunks(ServerLevel level) {
        BlockPos base = level.dimension().equals(CelestialIds.MOON_LEVEL)
                ? SafeCelestialTravel.FIXED_FEET_POSITION
                : level.getSharedSpawnPos();
        for (int[] offset : PAD_OFFSETS) {
            BlockPos pad = base.offset(offset[0], 0, offset[1]);
            ChunkPos chunk = new ChunkPos(pad);
            level.getChunkSource().addRegionTicket(PAD_CLEANUP_TICKET, chunk, 2, chunk);
            level.getChunkAt(pad);
        }
    }

    private static void clearPadRockets(ServerLevel level) {
        ArrayList<RocketEntity> stale = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof RocketEntity rocket) {
                stale.add(rocket);
            }
        }
        stale.forEach(Entity::discard);
    }

    private static void placeLegalRocket(GameTestHelper helper, BlockPos origin) {
        helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
        helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
    }

    private static RocketStructureSnapshot successfulSnapshot(GameTestHelper helper, BlockPos origin) {
        RocketScanResult result = scan(helper, origin);
        helper.assertTrue(
                result.status() == RocketScanResult.Status.SUCCESS,
                "Legal rocket scan failed: "
                        + (result.issues().isEmpty() ? "unknown" : result.issues().get(0).code())
        );
        return result.snapshot().orElseThrow();
    }

    private static RocketScanResult scan(GameTestHelper helper, BlockPos relativeOrigin) {
        ServerLevel level = helper.getLevel();
        BlockPos absoluteOrigin = helper.absolutePos(relativeOrigin);
        RocketStructureScanTask task = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()),
                level.dimension().location(),
                new RocketPosition(absoluteOrigin.getX(), absoluteOrigin.getY(), absoluteOrigin.getZ()),
                UUID.randomUUID(),
                level.getGameTime()
        );
        RocketScanResult result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        int steps = 1;
        while (result.status() == RocketScanResult.Status.RUNNING && steps++ < 64) {
            result = task.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        }
        return result;
    }
}
