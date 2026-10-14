package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
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
            var name = io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW.dataName();
            server.overworld().getDataStorage().set(name, null);
            helper.assertTrue(ResourceEscrowSavedData.existing(helper.getLevel().getServer()) == null, "Fixture requires no ledger");
            var record = anotherTransferWriting(helper);
            var player = RocketLegacyFuelGameTests.owner(helper, rocket); var manager = new RocketManager();
            try {
                helper.assertTrue(RocketEscrowGate.idle(rocket), "Journal alone changed ledger protection");
                RocketLegacyFuelGameTests.intent(manager, player, rocket, RocketFlightAction.BOARD);
                helper.assertTrue(player.getVehicle() == rocket, "Unrelated write blocked landed boarding");
                RocketLegacyFuelGameTests.refuel(helper, rocket);
                RocketLegacyFuelGameTests.intent(manager, player, rocket, RocketFlightAction.LEAVE);
            } finally { player.stopRiding(); finishUnrelated(helper, record); server.overworld().getDataStorage().set(name, ledger); }
        });
    }

    @GameTest(template = "rocket_test", batch = "journal_blocked_interlocks", timeoutTicks = 40)
    public static void blockedJournalRefusesLaunchAndFuelButNotMountOrDismount(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
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
