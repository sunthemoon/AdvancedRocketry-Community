package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Transfer-journal presence matrix and explicit recovery scenarios. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketTransferRecoveryGameTests {
    private static final String TEMPLATE = "rocket_test";

    private RocketTransferRecoveryGameTests() {
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
            // Drain asynchronous entity-chunk reads so the recovery matrix observes a persisted,
            // restart-equivalent boundary instead of racing the accelerated GameTest clock.
            earth.getServer().saveEverything(false, true, true);
            RocketTransferRecoveryReport sourceOnlyReport = recoveryManager.recoverTransfer(
                    earth.getServer(),
                    sourceOnly.transferId()
            );
            if (sourceOnlyReport.status() == RocketTransferRecoveryReport.Status.RETRY_LATER) {
                retrySourceRecovery(
                        helper,
                        earth,
                        moon,
                        recoveryManager,
                        sourceOnly,
                        1
                );
                return;
            }
            continueRecoveryMatrix(
                    helper,
                    earth,
                    moon,
                    recoveryManager,
                    sourceOnly,
                    sourceOnlyReport
            );
        });
    }

    private static void retrySourceRecovery(
            GameTestHelper helper,
            ServerLevel earth,
            ServerLevel moon,
            RocketManager recoveryManager,
            RocketTransferRecord sourceOnly,
            int attempt
    ) {
        helper.runAfterDelay(1, () -> {
            RocketTransferRecoveryReport report = recoveryManager.recoverTransfer(
                    earth.getServer(),
                    sourceOnly.transferId()
            );
            if (report.status() == RocketTransferRecoveryReport.Status.RETRY_LATER
                    && attempt < 20) {
                retrySourceRecovery(
                        helper,
                        earth,
                        moon,
                        recoveryManager,
                        sourceOnly,
                        attempt + 1
                );
                return;
            }
            continueRecoveryMatrix(
                    helper,
                    earth,
                    moon,
                    recoveryManager,
                    sourceOnly,
                    report
            );
        });
    }

    private static void continueRecoveryMatrix(
            GameTestHelper helper,
            ServerLevel earth,
            ServerLevel moon,
            RocketManager recoveryManager,
            RocketTransferRecord sourceOnly,
            RocketTransferRecoveryReport sourceOnlyReport
    ) {
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

}
