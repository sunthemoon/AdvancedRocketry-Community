package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W27d1 A1 with the session's real coordinator, receipt store, registry and transfer journal: an escrow-bound source is
 * refused with BAY_SOURCE_PROTECTED and its menu reason; RELEASED_TO_ORDINARY gives the ordinary stationary recovery;
 * an INTENT receipt locks a rocket at rest until its exact removal (section 8, Q8); the INTENT restart rows.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayDeploymentGameTests {
    private BayDeploymentGameTests() { }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 400)
    public static void anEscrowBoundSourceIsRefusedWithBaySourceProtectedAndKeepsItsPackages(GameTestHelper helper) {
        new Checkpoint(helper, true).start();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 600)
    public static void releasedToOrdinaryGivesTheOrdinaryStationaryRecovery(GameTestHelper helper) {
        new Checkpoint(helper, false).start();
    }

    /** D0 entered directly at the ascent-complete hook with a PREPARED record and its source in TRANSIT. */
    private static final class Checkpoint {
        final GameTestHelper helper;
        final boolean bound;
        final UUID owner = UUID.randomUUID();
        final net.minecraft.world.item.ItemStack data, solar;
        Flight flight;
        BayClaim claim;
        List<String> before;
        int stage, waits;
        boolean closed;

        Checkpoint(GameTestHelper helper, boolean bound) {
            this.helper = helper; this.bound = bound; data = dataPackage(owner); solar = solarPackage(owner);
        }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++waits < 560, "Bay checkpoint fixture exceeded its bounded wait");
                var server = helper.getLevel().getServer();
                var journal = RocketTransferSavedData.get(server);
                switch (stage) {
                    case 0 -> {
                        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3)), target = helper.absolutePos(new BlockPos(14, 2, 3));
                        if (!helper.getLevel().isPositionEntityTicking(origin) || !helper.getLevel().isPositionEntityTicking(target)) { return; }
                        flight = transit(helper, origin, target, owner, List.of(data, solar),
                                bound ? RocketEscrowSectionPatch.BOUND : RocketEscrowSectionPatch.NEUTRAL);
                        before = signatures(flight.record());
                        placeBay(helper, new BlockPos(8, 2, 8), owner, data);
                        journal.put(flight.record());
                        bays(helper).ascentComplete(server, journal, flight.record(), flight.source());
                        claim = claim(helper, flight.record().logicalRocketId()).orElseThrow();
                        helper.assertTrue(claim.stage() == BayClaim.Stage.GROUP && claim.members().size() == 2,
                                "D0 did not hold L at once with both packaged bays");
                        if (!bound) { flight.source().setPos(flight.source().getX(), flight.source().getY() + 1, flight.source().getZ()); }
                        stage = 1;
                    }
                    case 1 -> {
                        if (claim(helper, claim.logical()).isPresent()) {
                            helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts.holdsRocket(server, claim.logical()),
                                    "A claim must hold the gate lock");
                            return;
                        }
                        helper.assertTrue(claim.stage() == BayClaim.Stage.ENDED, "The claim was forgotten before it ended");
                        String reason = bound ? "BAY_SOURCE_PROTECTED" : "SOURCE_UNAVAILABLE";
                        helper.assertTrue(claim.members().stream().allMatch(member -> member.state() == BayMember.State.REFUSED
                                && member.reason().equals(reason)), "Every bay must be refused with " + reason);
                        helper.assertTrue(claim.handoff().orElseThrow() == (bound ? BayClaim.Handoff.READY_SOURCE : BayClaim.Handoff.ORDINARY),
                                "Wrong handoff " + claim.handoff());
                        var session = bays(helper).sessionForTests();
                        for (var pkg : List.of(data, solar)) {
                            helper.assertTrue(session.find(satellite(pkg)).isEmpty(), "A runtime refusal wrote a receipt");
                        }
                        helper.assertTrue(before.equals(signatures(journal.find(flight.record().transferId()).orElseThrow())),
                                "The packages in T changed");
                        if (bound) {
                            helper.assertTrue(session.rejections().get(satellite(data)).orElseThrow().reason().equals(reason),
                                    "The runtime rejection was not remembered");
                            var bay = (io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity)
                                    helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(8, 2, 8)));
                            helper.assertTrue(bay.phase() == SatelliteBayStatus.Phase.NONE
                                    && bay.reason() == SatelliteBayStatus.Reason.BAY_SOURCE_PROTECTED, "The bay menu reason is not shown");
                            finish(journal);
                        } else {
                            stage = 2;
                        }
                    }
                    default -> {
                        // RELEASED_TO_ORDINARY: T is unclassified and ordinary W11b2 recovery settles a stationary source.
                        if (journal.find(flight.record().transferId()).isPresent()) { return; }
                        var data = flight.source().flightData().orElseThrow();
                        helper.assertTrue(data.state() == RocketFlightState.FUELED
                                && flight.source().getY() == flight.record().sourceSnapshot().sourceOrigin().y(),
                                "Ordinary recovery did not restore the stationary source");
                        helper.assertTrue(before.equals(signatures(flight.source().snapshot().orElseThrow())), "The packages changed");
                        finish(journal);
                    }
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        void finish(RocketTransferSavedData journal) { close(); helper.succeed(); }


        void close() {
            if (closed) { return; }
            closed = true;
            var server = helper.getLevel().getServer();
            if (flight != null) {
                var journal = RocketTransferSavedData.get(server);
                if (journal.find(flight.record().transferId()).isPresent()) { journal.remove(flight.record().transferId()); }
                RocketTransferEntities.releaseFlightTickets(server, flight.record());
                flight.source().discard();
            }
            var session = bays(helper).sessionForTests();
            session.rejections().remove(satellite(data)); session.rejections().remove(satellite(solar));
            clearBay(helper.getLevel(), helper.absolutePos(new BlockPos(8, 2, 8)));
        }

    }

    static List<String> signatures(RocketTransferRecord record) { return signatures(record.sourceSnapshot()); }
    static List<String> signatures(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot snapshot) {
        return bayPayloads(snapshot).stream().map(RocketNativeResources::signature).toList();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void anIntentReceiptLocksTheRocketAtRestUntilItsExactRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        UUID owner = UUID.randomUUID();
        var flight = transit(helper, helper.absolutePos(new BlockPos(5, 2, 5)), helper.absolutePos(new BlockPos(12, 2, 5)), owner,
                List.of(), RocketEscrowSectionPatch.NEUTRAL);
        RocketEntity rocket = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(rocket != null, "No rocket");
        flight.source().discard();
        UUID logical = UUID.randomUUID();
        rocket.initialize(flight.record().sourceSnapshot(), logical, owner);
        RocketTransferEntities.positionAtOrigin(rocket, flight.record().sourceSnapshot().sourceOrigin());
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ARCEBayHold"));
        var assigned = rocket.flightData().orElseThrow();
        rocket.updateFlightData(assigned.withPassengers(assigned.passengers().assign(player.getUUID()).orElseThrow()));
        helper.assertTrue(level.addFreshEntity(rocket), "Rocket spawn failed");
        long tick = server.getTickCount();
        String hash = RocketNativeResources.hash("w27d1-hold");
        var intent = new BayReceipt(UUID.randomUUID(), BayReceiptTestHooks.nextSequence(server), logical, new RocketPosition(0, 1, 0),
                UUID.randomUUID(), owner, ModIdentity.id("data_satellite").toString(), SatelliteKind.DATA, ModIdentity.id("earth").toString(),
                hash, UUID.randomUUID(), hash, null, hash, null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, tick, tick, 0);
        var operator = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        int[] step = {0};
        helper.onEachTick(() -> {
            try {
                if (step[0] == 0) {
                    helper.assertTrue(RocketEscrowGate.idle(rocket) && player.startRiding(rocket, true), "Fixture rocket was not idle");
                    player.stopRiding();
                    helper.assertTrue(BayReceiptTestHooks.put(server, intent, outcome -> { }).queued(), "INTENT fixture refused");
                    // The bay part holds L at once, from the queued receipt; the escrow-only forms stay unchanged.
                    helper.assertTrue(RocketEscrowGate.bayHeld(rocket) && !RocketEscrowGate.idle(rocket)
                            && !RocketEscrowGate.resourcesIdle(rocket) && RocketEscrowGate.logicalLocked(server, logical)
                            && RocketEscrowGate.escrowIdle(rocket) && !RocketEscrowGate.escrowLogicalLocked(server, logical),
                            "The bay part did not join idle, resourcesIdle and logicalLocked only");
                    var flightBefore = rocket.flightData().orElseThrow(); var position = rocket.position();
                    helper.assertTrue(!player.startRiding(rocket, true), "Mount was not refused");
                    server.getCommands().performPrefixedCommand(operator, "tp " + rocket.getStringUUID() + " ~ ~5 ~");
                    server.getCommands().performPrefixedCommand(operator, "data merge entity " + rocket.getStringUUID()
                            + " {RocketEntityData:{}}");
                    rocket.kill();
                    var refill = flightBefore.withFuel(flightBefore.fuel().fill(1).state(), level.getGameTime());
                    boolean refused = false;
                    try { rocket.updateFlightData(refill); } catch (IllegalStateException protectedResources) { refused = true; }
                    helper.assertTrue(refused, "A held rocket's resources changed");
                    UUID launch = UUID.randomUUID();
                    var launched = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, launch);
                    helper.assertTrue(rocket.isAlive() && rocket.position().equals(position) && !rocket.isPassenger(),
                            "kill or teleport moved a held rocket");
                    helper.assertTrue(rocket.flightData().orElseThrow().equals(flightBefore), "A held rocket's flight data changed");
                    helper.assertTrue(!RocketRuntime.resourceMutationAllowed(rocket), "Loaders and disassembly were admitted");
                    helper.assertTrue(launched.code() != io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.SUCCESS
                            && RocketTransferSavedData.get(server).find(launch).isEmpty(), "Launch was admitted: " + launched.code());
                    helper.assertTrue(BayReceiptTestHooks.remove(server, intent.op(), outcome -> { }).status()
                            == io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptStore.Status.BUSY,
                            "One change of an operation at a time");
                    step[0] = 1;
                } else if (step[0] == 1) {
                    if (BayReceiptTestHooks.store(server).map(store -> store.holdsWork()).orElse(true)) { return; }
                    helper.assertTrue(BayReceiptTestHooks.remove(server, intent.op(), outcome -> { }).queued(), "Removal refused");
                    helper.assertTrue(RocketEscrowGate.bayHeld(rocket), "The lock ended before the removal's acknowledgement");
                    step[0] = 2;
                } else if (step[0] == 2) {
                    if (BayReceiptTestHooks.store(server).map(store -> store.holdsWork()).orElse(true)) { return; }
                    helper.assertTrue(!RocketEscrowGate.bayHeld(rocket) && RocketEscrowGate.idle(rocket)
                            && player.startRiding(rocket, true), "The exact removal did not free the rocket");
                    player.stopRiding(); rocket.discard(); step[0] = 3; helper.succeed();
                }
            } catch (RuntimeException | Error failure) { player.stopRiding(); rocket.discard(); throw failure; }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void restartRowsFollowTheLoadedRegistryAndTransferJournal(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        var flight = transit(helper, helper.absolutePos(new BlockPos(5, 2, 10)), helper.absolutePos(new BlockPos(12, 2, 10)), owner,
                List.of(dataPackage(owner)), RocketEscrowSectionPatch.NEUTRAL);
        flight.source().discard();
        var record = flight.record();
        var registry = SatelliteMissionSavedData.get(server);
        var journal = RocketTransferSavedData.get(server);
        UUID absent = UUID.randomUUID(), equal = UUID.randomUUID(), differs = UUID.randomUUID();
        try {
            for (UUID id : List.of(equal, differs)) {
                helper.assertTrue(registry.launchIdle(t -> SatelliteState.launchIdle(id, ModIdentity.id("solar_satellite"), owner, t,
                        ModIdentity.id("earth"), new SatelliteBlueprint(List.of(ModIdentity.id("primary")), false,
                                new SatelliteStats(4, 720, 0, 0, 10)), new SatelliteKindState.Plain(SatelliteKind.SURVEY)), 0)
                        .code() == io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode.SUCCESS,
                        "Registry fixture refused");
            }
            // Same tick as the put and removal: no transfer classification or recovery sees the fixture record.
            journal.put(record);
            helper.assertTrue(BayRestartRows.row(server, intent(record, absent, owner, "solar_satellite"))
                    == BayRestartRows.Row.REJECT_INTERRUPTED, "X absent with matching T must become REJECTED (INTERRUPTED)");
            helper.assertTrue(BayRestartRows.row(server, intent(record, equal, owner, "solar_satellite"))
                    == BayRestartRows.Row.HOLD_EQUAL, "An equal X waits unchanged for W27d2");
            helper.assertTrue(BayRestartRows.row(server, intent(record, differs, owner, "data_satellite"))
                    == BayRestartRows.Row.REPAIR_SIGNATURE, "A different tuple becomes REPAIR");
            var otherOwner = intent(record, absent, UUID.randomUUID(), "solar_satellite");
            helper.assertTrue(BayRestartRows.row(server, otherOwner) == BayRestartRows.Row.HOLD_AUTHORITY, "Owner mismatch");
            var missing = new BayReceipt(UUID.randomUUID(), 1, record.logicalRocketId(), new RocketPosition(0, 1, 0), absent, owner,
                    ModIdentity.id("solar_satellite").toString(), SatelliteKind.SURVEY, ModIdentity.id("earth").toString(),
                    record.sourceSnapshot().contentHash(), UUID.randomUUID(), record.sourceSnapshot().contentHash(), null,
                    record.destinationSnapshot().contentHash(), null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, 0, 0, 0);
            helper.assertTrue(BayRestartRows.row(server, missing) == BayRestartRows.Row.HOLD_AUTHORITY, "T missing must hold, no write");
            journal.remove(record.transferId());
            helper.assertTrue(BayRestartRows.row(server, intent(record, absent, owner, "solar_satellite"))
                    == BayRestartRows.Row.HOLD_AUTHORITY, "A removed T must hold");
            helper.succeed();
        } finally {
            if (journal.find(record.transferId()).isPresent()) { journal.remove(record.transferId()); }
            for (UUID id : List.of(equal, differs)) { registry.decommission(id, owner, true, true); }
        }
    }

    private static BayReceipt intent(RocketTransferRecord record, UUID satellite, UUID owner, String definition) {
        return new BayReceipt(UUID.randomUUID(), 1, record.logicalRocketId(), new RocketPosition(0, 1, 0), satellite, owner,
                ModIdentity.id(definition).toString(), SatelliteKind.SURVEY, ModIdentity.id("earth").toString(),
                record.sourceSnapshot().contentHash(), record.transferId(), record.sourceSnapshot().contentHash(), null,
                record.destinationSnapshot().contentHash(), null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, 0, 0, 0);
    }
}
