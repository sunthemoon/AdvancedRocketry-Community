package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalSchedulingGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketJournalInterlockGameTests {
    private RocketJournalInterlockGameTests() { }

    @GameTest(template = "rocket_test", batch = "journal_dismount", timeoutTicks = 500)
    public static void landedDismountSurvivesAnotherTransfersUnacknowledgedWrite(GameTestHelper helper) {
        RocketLegacyFuelGameTests.afterLanding(helper, rocket -> {
            var player = RocketLegacyFuelGameTests.owner(helper, rocket); var manager = new RocketManager();
            RocketLegacyFuelGameTests.intent(manager, player, rocket, RocketFlightAction.BOARD);
            helper.assertTrue(player.getVehicle() == rocket, "Landed boarding setup failed");
            var record = anotherTransferWriting(helper);
            try {
                player.stopRiding();
                helper.assertTrue(player.getVehicle() == null, "Unrelated write cancelled dismount");
                RocketLegacyFuelGameTests.intent(manager, player, rocket, RocketFlightAction.LEAVE);
                helper.assertTrue(rocket.flightData().orElseThrow().passengers().assignment(player.getUUID()).isEmpty(),
                        "Unrelated write blocked explicit leave");
            } finally { player.stopRiding(); finishUnrelated(helper, record); }
        });
    }

    @GameTest(template = "rocket_test", batch = "journal_stationary_mutation", timeoutTicks = 500)
    public static void noLedgerLandedBoardAndRefuelSurviveAnotherTransfersWrite(GameTestHelper helper) {
        RocketLegacyFuelGameTests.afterLanding(helper, rocket -> {
            var server = helper.getLevel().getServer(); var ledger = ResourceEscrowSavedData.existing(server);
            // The runtime's one retarget path; it refuses rather than drop a ledger that still holds escrow work.
            var runtime = EscrowRuntime.of(server);
            helper.assertTrue(runtime.replaceLedgerForTests(server, null), "Ledger removal refused while escrow work is held");
            helper.assertTrue(ResourceEscrowSavedData.existing(helper.getLevel().getServer()) == null, "Fixture requires no ledger");
            var record = anotherTransferWriting(helper);
            var player = RocketLegacyFuelGameTests.owner(helper, rocket); var manager = new RocketManager();
            try {
                helper.assertTrue(RocketEscrowGate.idle(rocket), "Journal alone changed ledger protection");
                RocketLegacyFuelGameTests.intent(manager, player, rocket, RocketFlightAction.BOARD);
                helper.assertTrue(player.getVehicle() == rocket, "Unrelated write blocked landed boarding");
                RocketLegacyFuelGameTests.refuel(helper, rocket);
                RocketLegacyFuelGameTests.intent(manager, player, rocket, RocketFlightAction.LEAVE);
            } finally {
                player.stopRiding(); finishUnrelated(helper, record);
                helper.assertTrue(runtime.replaceLedgerForTests(server, ledger), "Original ledger was not restored");
            }
        });
    }

    @GameTest(template = "rocket_test", batch = "journal_blocked_interlocks", timeoutTicks = 40)
    public static void blockedJournalRefusesLaunchAndFuelButNotMountOrDismount(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ResourceEscrowSavedData.forTests(server); // An unrelated escrow root must not disable scalar gameplay.
        var original = RocketTransferSavedData.get(server);
        var future = original.save(new CompoundTag()); future.putInt("schema_version", Integer.MAX_VALUE);
        var blocked = RocketTransferSavedData.load(future);
        var rocket = RocketLegacyFuelGameTests.decodedRocket(helper);
        var player = RocketLegacyFuelGameTests.owner(helper, rocket); var manager = new RocketManager();
        var fuel = rocket.flightData().orElseThrow().fuel();
        rocket.updateFlightData(rocket.flightData().orElseThrow().withFuel(fuel.fill(1_000).state(), helper.getLevel().getGameTime()));
        server.overworld().getDataStorage().set(RocketTransferSavedData.DATA_NAME, blocked);
        try {
            helper.assertTrue(!blocked.operational() && RocketEscrowGate.idle(rocket), "Blocked journal became an escrow lock");
            RocketLegacyFuelGameTests.intent(manager, player, rocket, RocketFlightAction.BOARD);
            helper.assertTrue(player.getVehicle() == rocket, "Blocked journal froze mounting");
            player.stopRiding(); helper.assertTrue(player.getVehicle() == null, "Blocked journal froze dismounting");
            RocketLegacyFuelGameTests.intent(manager, player, rocket, RocketFlightAction.LEAVE);
            helper.assertTrue(rocket.flightData().orElseThrow().passengers().assignment(player.getUUID()).isEmpty(), "Blocked journal froze explicit leave");
            helper.assertTrue(!RocketRuntime.resourceMutationAllowed(rocket), "Blocked journal allowed resource mutation");
            helper.assertTrue(manager.requestAdminFlight(rocket, RocketDestination.MOON, UUID.randomUUID()).code()
                    == RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED, "Blocked journal allowed launch");
            rocket.kill(); helper.assertTrue(!rocket.isAlive(), "Blocked journal suppressed base kill");
        } finally { player.stopRiding(); rocket.discard(); server.overworld().getDataStorage().set(RocketTransferSavedData.DATA_NAME, original); }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "journal_pre_baseline_launch", timeoutTicks = 600)
    public static void legacyLaunchBeforeTheSessionsBaselineCommitIsAdmittedAndCountsDownAfterIt(GameTestHelper helper) {
        // Root decision R30 item 2 (W91-R2): prepareLaunch keeps only its interlock and reserve(L). Before a session's first
        // acknowledgement an untouched absent rocket counts as proved (W86 section 2.1), so a legacy launch is admitted,
        // and its countdown runs only after that commit. A busy L is still refused before any change.
        var driver = new GameTestTickDriver(helper); var level = helper.getLevel(); var server = level.getServer();
        var moon = server.getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon missing");
        RocketFlightGameTestFixtures.clearTransferJournal(level); RocketFlightGameTestFixtures.primePadChunks(moon);
        driver.after(40, () -> {
            RocketJournalGameTestSupport.acknowledgeFixture(helper);
            // A reloaded copy of the settled root, with the same payload and 128 slots, that has no acknowledgement yet.
            var journal = RocketJournalSchedulingGameTestSupport.boundedFixture(helper, 128);
            helper.assertTrue(journal.checkedWriteResult().acknowledgedSequence() == null, "The fixture root has a baseline");
            var rocket = RocketLegacyFuelGameTests.decodedRocket(helper); var fuel = rocket.flightData().orElseThrow().fuel();
            rocket.updateFlightData(rocket.flightData().orElseThrow().withFuel(fuel.fill(fuel.capacity()).state(), level.getGameTime()));
            UUID logical = rocket.assemblyTransactionId().orElseThrow();
            long before = rocket.flightData().orElseThrow().fuel().amount();
            helper.assertTrue(!journal.actionable(logical) && RocketRuntime.resourceMutationAllowed(rocket),
                    "The fixture is not the pre-baseline state that W91's precheck refused");
            UUID refused = UUID.randomUUID();
            try (var busy = journal.reserve(logical).orElseThrow()) {
                long sequence = journal.mutationSequence(); int outstanding = journal.outstandingEffectCount();
                var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, refused);
                helper.assertTrue(result.code() == RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED,
                        "A busy rocket was not refused: " + result.code());
                helper.assertTrue(journal.mutationSequence() == sequence && journal.outstandingEffectCount() == outstanding
                        && journal.find(refused).isEmpty() && journal.findByLogicalRocket(logical).isEmpty(),
                        "The refusal of a busy rocket changed the journal");
                helper.assertTrue(rocket.flightData().orElseThrow().state() == RocketFlightState.FUELED
                        && rocket.flightData().orElseThrow().fuel().amount() == before, "The refusal changed the rocket");
            }
            UUID transfer = UUID.randomUUID();
            RocketFlightGameTestFixtures.assertPendingLaunch(helper,
                    RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, transfer), rocket);
            helper.assertTrue(journal.find(transfer).isPresent() && journal.checkedWriteResult().acknowledgedSequence() == null,
                    "The admitted launch did not wait for the session's baseline commit");
            awaitCountdownAfterBaseline(helper, driver, journal, rocket, helper.getTick() + 200,
                    new GameTestTickPacer(server), () -> RocketFlightGameTestFixtures.awaitFlightState(helper, driver, moon,
                            logical, RocketFlightState.LANDED, 350, () -> {
                                var landed = RocketFlightGameTestFixtures.findLogicalRocket(moon, logical);
                                helper.assertTrue(landed != null, "The admitted pre-baseline launch did not land");
                                landed.discard(); RocketFlightGameTestFixtures.clearTransferJournal(level); helper.succeed();
                            }));
        });
    }

    /** Each tick: the rocket stays FUELED until the root's first acknowledgement, then counts down. */
    private static void awaitCountdownAfterBaseline(GameTestHelper helper, GameTestTickDriver driver,
                                                    RocketTransferSavedData journal, RocketEntity rocket, long deadline,
                                                    GameTestTickPacer pacer, Runnable next) {
        boolean committed = journal.checkedWriteResult().acknowledgedSequence() != null;
        var state = rocket.flightData().orElseThrow().state();
        helper.assertTrue(committed || state == RocketFlightState.FUELED, "The countdown ran before the baseline commit");
        if (state != RocketFlightState.FUELED) {
            helper.assertTrue(state == RocketFlightState.COUNTDOWN, "The countdown was skipped: " + state);
            next.run();
            return;
        }
        helper.assertTrue(helper.getTick() < deadline, "The admitted launch never counted down after the baseline commit");
        pacer.pace();
        driver.after(1, () -> awaitCountdownAfterBaseline(helper, driver, journal, rocket, deadline, pacer, next));
    }

    private static RocketTransferRecord anotherTransferWriting(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); var journal = RocketTransferSavedData.get(server);
        RocketJournalGameTestSupport.acknowledgeFixture(helper);
        var source = RocketLegacyFuelGameTests.decodedRocket(helper); var snapshot = source.snapshot().orElseThrow();
        var fuel = source.flightData().orElseThrow().fuel().fill(10_000).state();
        UUID transfer = UUID.randomUUID();
        var plan = RocketFlightPlanner.plan(snapshot.stats(), fuel, RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON, transfer, helper.getLevel().getGameTime()).plan();
        var transit = source.flightData().orElseThrow().withFuel(fuel, helper.getLevel().getGameTime()).withPlan(plan)
                .startCountdown(helper.getLevel().getGameTime()).completeCountdown(helper.getLevel().getGameTime())
                .beginTransit(transfer, helper.getLevel().getGameTime());
        var destination = snapshot.relocated(UUID.randomUUID(), CelestialIds.MOON_LEVEL.location(),
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(1_000, 90, 1_000), helper.getLevel().getGameTime());
        var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(),
                destination.sourceOrigin(), helper.getLevel().getGameTime());
        var record = RocketTransferRecord.create(transfer, transit.logicalRocketId(), source.ownerId().orElseThrow(), source.getUUID(),
                snapshot, destination, transit, arrival, plan.requiredFuel(), helper.getLevel().getGameTime());
        source.discard(); journal.put(record); journal.flush(server);
        helper.assertTrue(!journal.acknowledgedCurrent(), "Unrelated write was already acknowledged");
        return record;
    }

    private static void finishUnrelated(GameTestHelper helper, RocketTransferRecord record) {
        RocketJournalGameTestSupport.acknowledgeFixture(helper);
        var journal = RocketTransferSavedData.get(helper.getLevel().getServer()); journal.remove(record.transferId());
        RocketJournalGameTestSupport.acknowledgeFixture(helper);
    }
}
