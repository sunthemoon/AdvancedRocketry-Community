package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R168-R2: restart claims observe outside copies before released authority exists, including cold ends. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayReleasedConflictGameTests {
    private BayReleasedConflictGameTests() { }

    @GameTest(template = TEMPLATE, batch = "bay_released_conflict_join_present", timeoutTicks = 1_200)
    public static void releasedJoinWithSource(GameTestHelper h) { new Check(h, true, true, false, 0).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_released_conflict_join_absent", timeoutTicks = 1_200)
    public static void releasedJoinWithoutSource(GameTestHelper h) { new Check(h, true, false, false, 1).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_released_conflict_move_present", timeoutTicks = 1_200)
    public static void releasedMoveWithSource(GameTestHelper h) { new Check(h, true, true, true, 2).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_released_conflict_move_absent", timeoutTicks = 1_200)
    public static void releasedMoveWithoutSource(GameTestHelper h) { new Check(h, true, false, true, 3).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_repair_conflict_join_present", timeoutTicks = 1_200)
    public static void repairJoinWithSource(GameTestHelper h) { new Check(h, false, true, false, 4).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_repair_conflict_join_absent", timeoutTicks = 1_200)
    public static void repairJoinWithoutSource(GameTestHelper h) { new Check(h, false, false, false, 5).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_repair_conflict_move_present", timeoutTicks = 1_200)
    public static void repairMoveWithSource(GameTestHelper h) { new Check(h, false, true, true, 6).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_repair_conflict_move_absent", timeoutTicks = 1_200)
    public static void repairMoveWithoutSource(GameTestHelper h) { new Check(h, false, false, true, 7).start(); }

    private static final class Check {
        final GameTestHelper helper;
        final boolean released, present, move;
        final int variant;
        final GameTestTickPacer pacer;
        final UUID owner = UUID.randomUUID(), rider = UUID.randomUUID();
        Flight flight;
        BayReceipt receipt;
        RocketEntity copy;
        ServerPlayer player;
        BayClaim claim;
        int stage, readyTicks, pending;
        boolean closed;

        Check(GameTestHelper helper, boolean released, boolean present, boolean move, int variant) {
            this.helper = helper; this.released = released; this.present = present; this.move = move; this.variant = variant;
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            var server = helper.getLevel().getServer(); var coordinator = bays(helper);
            var journal = RocketTransferSavedData.get(server);
            try {
                if (stage == 0) {
                    var origin = helper.absolutePos(new BlockPos(3, 2, 3));
                    if (!helper.getLevel().isPositionEntityTicking(origin)) { return; }
                    var pkg = dataPackage(owner);
                    flight = transit(helper, origin, helper.getLevel().dimension().location(),
                            position(new BlockPos(810_008 + 1_024 * variant, 100, 810_008)), owner, List.of(pkg),
                            RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), rider);
                    receipt = intent(server, flight.record(), pkg, 0)
                            .withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", server.getTickCount());
                    if (released) { receipt = receipt.withReleased(); }
                    helper.assertTrue(!BaySourceProof.endsLoaded(server, flight.record()), "The delayed end was already loaded");
                    if (!present) { flight.source().discard(); }
                    copy = ModEntities.ROCKET.get().create(helper.getLevel());
                    copy.initializeTransferred(flight.record().sourceSnapshot(), receipt.rocket(), owner, flight.record().sourceFlightData());
                    RocketTransferEntities.positionAtAltitude(copy, flight.record().sourceSnapshot().sourceOrigin());
                    player = FirstEventGameTestPlayers.join(server, rider, "W196Conflict", helper.getLevel(), origin);
                    BayReleasedPassengerGameTests.seat(player, copy, coordinator);
                    if (move) { helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Initial copy refused"); }
                    helper.assertTrue(BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued(), "Receipt refused");
                    journal.put(flight.record()); stage = 1;
                } else if (stage == 1) {
                    if (!storeIdle(server) || !journal.actionable(receipt.rocket())) { return; }
                    pending = BayReceiptTestHooks.authority(server).pendingCount();
                    claim = coordinator.restartForTests(List.of(receipt)).get(0);
                    // Exercise both the no-release-state join window and a state waiting for its recorded end.
                    if (move) {
                        var field = BayTransferRecovery.class.getDeclaredField("released"); field.setAccessible(true);
                        ((BayReleasedFlight) field.get(coordinator)).step(claim);
                        helper.assertTrue(!BaySourceProof.endsLoaded(server, flight.record()), "The first resume did not wait on its cold end");
                    }
                    var outside = helper.absolutePos(new BlockPos(3, 2, 12));
                    copy.setPos(outside.getX() + .5, copy.getY(), outside.getZ() + .5);
                    if (!move) { helper.assertTrue(helper.getLevel().addFreshEntity(copy), "Outside join refused"); }
                    helper.assertTrue(claim.hold() == BayClaim.Hold.REPAIR, "Outside notification did not hold the claim");
                    helper.assertTrue(!BayCopyCensus.within(server, flight.record(), copy), "Copy is not outside the recorded ends");
                    stage = 2;
                } else if (stage == 2) {
                    // The fixture loads only T's bounded ends, allowing the original faulty resume to run.
                    BayCensusColumns.renewRestart(server, flight.record());
                    var current = journal.find(receipt.transfer()).orElseThrow();
                    helper.assertTrue(current.phase() == RocketTransferPhase.PREPARED
                            && coordinator.claim(server, journal, current).kind() == RocketTransferBayRecoveryGate.Kind.HELD,
                            "Observed outside conflict delegated released flight");
                    helper.assertTrue(!copy.isRemoved() && player.getVehicle() == copy,
                            "Held recovery removed a copy or moved its passenger");
                    helper.assertTrue(present ? helper.getLevel().getEntity(current.sourceEntityId()) == flight.source()
                                    : helper.getLevel().getEntity(current.sourceEntityId()) == null,
                            "Held recovery reconstructed or removed the recorded source");
                    helper.assertTrue(RocketTransferEntities.findDestination(server, current) == null,
                            "Held recovery published a destination");
                    helper.assertTrue(coordinator.sessionForTests().current(receipt.op()).orElseThrow().equals(receipt)
                            && BayReceiptTestHooks.authority(server).pendingCount() == pending
                            && BayReceipts.holdsRocket(server, receipt.rocket()), "Conflict changed a receipt, flag, charge or lock");
                    if (!BaySourceProof.endsLoaded(server, current) || ++readyTicks < 10) { return; }
                    close(); helper.succeed();
                }
            } catch (ReflectiveOperationException failure) { close(); throw new IllegalStateException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
            finally { if (!closed) { pacer.pace(); } }
        }

        void close() {
            if (closed) { return; } closed = true;
            var server = helper.getLevel().getServer();
            if (player != null) { player.stopRiding(); server.getPlayerList().remove(player); }
            if (flight == null) { return; }
            bays(helper).forgetForTests(flight.record().logicalRocketId());
            var journal = RocketTransferSavedData.get(server);
            journal.find(flight.record().transferId()).ifPresent(record -> {
                var source = RocketTransferEntities.findSource(server, record); if (source != null) { source.discard(); }
                var destination = RocketTransferEntities.findDestination(server, record); if (destination != null) { destination.discard(); }
                journal.remove(record.transferId());
            });
            flight.source().discard(); if (copy != null) { copy.discard(); }
            if (receipt != null) { BayReceiptTestHooks.removeReleasedFixture(server, receipt.op()); }
            RocketTransferEntities.releaseFlightTickets(server, flight.record());
        }
    }
}
