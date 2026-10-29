package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R168-R1: real acknowledged passenger effects invalidated at either physical endpoint. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayReleasedPassengerGameTests {
    private BayReleasedPassengerGameTests() { }

    @GameTest(template = TEMPLATE, batch = "bay_released_rider_source_save", timeoutTicks = 1_200)
    public static void sourceSaveBeforePassengerAcknowledgement(GameTestHelper helper) { new Check(helper, false, true).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_released_rider_source_position", timeoutTicks = 1_200)
    public static void sourcePositionBeforePassengerAcknowledgement(GameTestHelper helper) { new Check(helper, false, false).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_released_rider_destination_save", timeoutTicks = 1_200)
    public static void destinationSaveBeforePassengerAcknowledgement(GameTestHelper helper) { new Check(helper, true, true).start(); }
    @GameTest(template = TEMPLATE, batch = "bay_released_rider_destination_position", timeoutTicks = 1_200)
    public static void destinationPositionBeforePassengerAcknowledgement(GameTestHelper helper) { new Check(helper, true, false).start(); }

    private static final class Check implements RocketTransferBayRecoveryGate {
        final GameTestHelper helper;
        final boolean destinationEnd, save;
        final UUID passenger = UUID.randomUUID(), owner = UUID.randomUUID();
        Flight flight;
        BayReceipt receipt;
        ServerPlayer player;
        RocketTransferBayRecoveryGate previous;
        int stage;
        boolean closed, injected;

        Check(GameTestHelper helper, boolean destinationEnd, boolean save) {
            this.helper = helper; this.destinationEnd = destinationEnd; this.save = save;
        }
        void start() { helper.onEachTick(this::tick); }

        void tick() {
            if (closed) { return; }
            var server = helper.getLevel().getServer(); var journal = RocketTransferSavedData.get(server);
            try {
                if (stage == 0) {
                    var origin = helper.absolutePos(new BlockPos(3, 2, 3));
                    if (!helper.getLevel().isPositionEntityTicking(origin)) { return; }
                    var pkg = dataPackage(owner);
                    flight = transit(helper, origin, helper.getLevel().dimension().location(),
                            position(helper.absolutePos(new BlockPos(14, 2, 3))), owner, List.of(pkg),
                            RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), passenger);
                    player = FirstEventGameTestPlayers.join(server, passenger, "W196Rider", helper.getLevel(), origin);
                    seat(player, flight.source(), bays(helper));
                    receipt = intent(server, flight.record(), pkg, 0).withReleased();
                    helper.assertTrue(BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued(), "Receipt refused");
                    journal.put(flight.record());
                    var field = RocketTransferBayDispatch.class.getDeclaredField("gate"); field.setAccessible(true);
                    previous = (RocketTransferBayRecoveryGate) field.get(dispatch()); field.set(dispatch(), this);
                    stage = 1;
                } else if (stage == 1) {
                    if (!storeIdle(server)) { return; }
                    bays(helper).restartForTests(List.of(receipt)); stage = 2;
                } else if (stage == 2) {
                    var record = journal.find(receipt.transfer()).orElseThrow();
                    if (record.phase() != RocketTransferPhase.DESTINATION_SPAWNED || !journal.actionable(receipt.rocket())
                            || previous.claim(server, journal, record).kind() != Kind.RELEASED_FLIGHT) { return; }
                    var destination = RocketTransferEntities.findDestination(server, record);
                    if (destination == null) { return; }
                    helper.assertTrue(player.getVehicle() == flight.source(), "Assigned rider left source before publication");
                    dispatch().classify(server, journal, record);
                    // Pause only the ordinary live tick at SPAWNED, then call its unchanged passenger method once.
                    // The real continuation publishes T and queues its checked-writer acknowledgement before returning.
                    var method = RocketTransferService.class.getDeclaredMethod("transferPassengers", MinecraftServer.class,
                            RocketTransferSavedData.class, RocketTransferRecord.class);
                    method.setAccessible(true); method.invoke(RocketRuntime.installedTransfers(), server, journal, record);
                    var pending = journal.find(receipt.transfer()).orElseThrow();
                    helper.assertTrue(pending.phase() == RocketTransferPhase.PASSENGERS_TRANSFERRED
                            && !journal.actionable(receipt.rocket()), "Passenger publication was not pending");
                    var endpoint = destinationEnd ? destination : flight.source();
                    if (save) { endpoint.saveWithoutId(new CompoundTag()); }
                    else { endpoint.setPos(endpoint.getX(), endpoint.getY(), endpoint.getZ()); }
                    helper.assertTrue(player.getVehicle() == flight.source(), "Mutation executed passenger effect early");
                    injected = true; stage = 3;
                } else if (stage == 3) {
                    var record = journal.find(receipt.transfer()).orElseThrow();
                    var destination = RocketTransferEntities.findDestination(server, record);
                    if (flight.source().isRemoved()) {
                        helper.assertTrue(destination != null && player.getVehicle() == destination,
                                "Source was removed before the assigned passenger reached the destination");
                    }
                    if (destination == null || destination.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return; }
                    helper.assertTrue(injected && flight.source().isRemoved() && player.getVehicle() == destination,
                            "Invalidated passenger effect did not reconcile through flight completion");
                    helper.assertTrue(bayPayloads(destination.snapshot().orElseThrow()).equals(bayPayloads(flight.record().sourceSnapshot()))
                            && BayReceipts.holdsRocket(server, receipt.rocket()), "Release changed package or lock");
                    close(); helper.succeed();
                }
            } catch (ReflectiveOperationException failure) { close(); throw new IllegalStateException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        @Override public Claim claim(MinecraftServer s, RocketTransferSavedData j, RocketTransferRecord r) { return previous.claim(s, j, r); }
        @Override public Boundary beforeAction(MinecraftServer s, RocketTransferSavedData j, RocketTransferRecord r, Action a) {
            if (!injected && flight != null && r.transferId().equals(flight.record().transferId())
                    && r.phase() == RocketTransferPhase.DESTINATION_SPAWNED && a == Action.TICK_SOURCE) { return Boundary.held(); }
            return previous.beforeAction(s, j, r, a);
        }
        @Override public void phaseEffect(MinecraftServer s, RocketTransferSavedData j, RocketTransferRecord r, Action a, Runnable effect) {
            previous.phaseEffect(s, j, r, a, effect);
        }
        @Override public void endpointChanged(MinecraftServer s, RocketTransferSavedData j, RocketTransferRecord r) { previous.endpointChanged(s, j, r); }
        @Override public void escrowConflict(MinecraftServer s, RocketTransferSavedData j, RocketTransferRecord r) { previous.escrowConflict(s, j, r); }
        @Override public boolean reconnectReleased(MinecraftServer s, RocketTransferSavedData j, RocketTransferRecord r, ServerPlayer p) {
            return previous.reconnectReleased(s, j, r, p);
        }

        void close() {
            if (closed) { return; } closed = true;
            var server = helper.getLevel().getServer();
            if (previous != null) {
                try { var field = RocketTransferBayDispatch.class.getDeclaredField("gate"); field.setAccessible(true); field.set(dispatch(), previous); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
            if (player != null) { player.stopRiding(); server.getPlayerList().remove(player); }
            if (flight == null) { return; }
            bays(helper).forgetForTests(flight.record().logicalRocketId());
            var journal = RocketTransferSavedData.get(server);
            journal.find(flight.record().transferId()).ifPresent(record -> {
                var destination = RocketTransferEntities.findDestination(server, record);
                if (destination != null) { destination.discard(); }
                journal.remove(record.transferId());
            });
            flight.source().discard();
            if (receipt != null) { BayReceiptTestHooks.removeReleasedFixture(server, receipt.op()); }
            RocketTransferEntities.releaseFlightTickets(server, flight.record());
        }
    }

    /** Fixture-only seating through the existing exact scope, as in BayCopiesGameTests. */
    static void seat(ServerPlayer player, RocketEntity rocket, BayTransferRecovery coordinator) throws ReflectiveOperationException {
        var steps = BayTransferRecovery.class.getDeclaredField("copies"); steps.setAccessible(true);
        var field = BayCopySteps.class.getDeclaredField("passengers"); field.setAccessible(true);
        var routing = field.get(steps.get(coordinator));
        var p = BayPassengerRouting.class.getDeclaredField("permittedPlayer"); p.setAccessible(true);
        var r = BayPassengerRouting.class.getDeclaredField("permittedSource"); r.setAccessible(true);
        p.set(routing, player); r.set(routing, rocket);
        try { if (!player.startRiding(rocket, true)) { throw new IllegalStateException("Fixture rider refused"); } }
        finally { p.set(routing, null); r.set(routing, null); }
    }
}
