package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionDebit;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * W131 A1: one governed launch of a cargo rocket to a same-Level destination through the production F1 entry, then
 * either a passenger's exact-source cancellation (CANCELLED) or a completed handoff (HANDED_OFF), through F6, the
 * real F7 writer and F8. {@link #tick} returns true once F8 has removed the ARMED entry and the survivor is idle.
 */
final class EscrowCargoLaunch implements AutoCloseable {
    private final GameTestHelper helper;
    private final EscrowCargoGameTestWorld world;
    private final RocketEntity source;
    private final BlockPos destination;
    private final boolean cancel;
    private final UUID transfer = UUID.randomUUID();
    private final String sourceHash;
    private final int cargo;
    private EscrowGameTestLaunch launch;
    private EscrowTerminalLeaseCut leaseCut;
    private ServerPlayer passenger;
    private RocketTransferRecord record;
    private RocketEntity survivor;
    private EscrowFlightTables.Armed arm;
    private boolean accepted, requested, sawF6, sawF7;

    EscrowCargoLaunch(GameTestHelper helper, EscrowCargoGameTestWorld world, RocketEntity source, BlockPos destination, boolean cancel) {
        this.helper = helper; this.world = world; this.source = source; this.destination = destination; this.cancel = cancel;
        sourceHash = source.snapshot().orElseThrow().contentHash(); cargo = EscrowCargoGameTestWorld.cargo(source);
        world.clear(destination); world.clear(destination.above()); world.transfers.add(transfer);
    }

    RocketEntity survivor() { return survivor; }
    EscrowFlightTables.Armed arm() { return arm; }

    boolean tick() {
        var server = world.server; var journal = RocketTransferSavedData.get(server); var data = world.data();
        if (launch == null) { return begin(); }
        launch.tick();
        if (!accepted) { return false; }
        if (record == null) {
            record = journal.find(transfer).orElseThrow();
            arm = data.ledger().armedFlights().stream().filter(value -> value.transfer().equals(transfer)).findFirst().orElseThrow();
            // The new record freezes the latest cargo snapshot; ARMED carries the READY anchor when a receipt backs the rocket.
            var head = data.ledger().rocketAuthorities().stream().filter(value -> value.logical().equals(arm.logical())).findFirst().orElse(null);
            check(record.sourceSnapshot().contentHash().equals(sourceHash) && arm.source().equals(source.getUUID())
                    && arm.epoch() == source.escrowSection().epoch() && (head == null ? arm.anchor() == null
                    : arm.anchor().equals(head.anchor()) && arm.releaseGeneration() == head.releaseGeneration() && arm.epoch() == head.epoch()),
                    "ARMED or PREPARED did not freeze the latest cargo source and its exact authority");
        }
        record = journal.find(transfer).orElse(record);
        var flight = source.flightData().orElseThrow();
        if (!requested) {
            if (cancel) {
                if (flight.state() != RocketFlightState.COUNTDOWN || !journal.acknowledgedCurrent()) { return false; }
                RocketRuntime.requestFlightIntent(passenger, source.getId(), RocketFlightAction.CANCEL,
                        flight.plan().orElseThrow().destinationTarget(), UUID.randomUUID());
                survivor = source;
            } else {
                var arrival = world.level.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
                if (!(arrival instanceof RocketEntity found) || record.phase() != RocketTransferPhase.COMMITTED
                        || !journal.actionAuthorityAvailable() || found.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return false; }
                survivor = found; world.rockets.add(found);
            }
            requested = true;
        }
        if (EscrowRuntime.of(server).terminal().checkpoint(server, record)) { sawF6 = true; }
        if (leaseCut == null && !sawF7 && data.bind(server).writerIdle()) {
            leaseCut = new EscrowTerminalLeaseCut(server, data, record, cancel ? EscrowFlightTables.Outcome.CANCELLED : EscrowFlightTables.Outcome.HANDED_OFF);
        }
        if (leaseCut != null && leaseCut.ready()) {
            check(sawF6, "F7 was captured without its joint F6 checkpoint");
            try { leaseCut.exercise(helper, EscrowTerminalLeaseCut.Fault.NONE, survivor, passenger); }
            finally { leaseCut.close(); leaseCut = null; }
            sawF7 = true;
        }
        if (data.ledger().armedFlights().stream().anyMatch(value -> value.transfer().equals(transfer))) { return false; }
        check(sawF6 && sawF7 && journal.find(transfer).isEmpty() && journal.actionAuthorityAvailable()
                && RocketEscrowGate.idle(survivor) && RocketRuntime.resourceMutationAllowed(survivor)
                && EscrowCargoGameTestWorld.cargo(survivor) == cargo, "F8 did not leave one idle survivor with the latest cargo");
        if (cancel) {
            check(survivor == source && source.isAlive() && source.snapshot().orElseThrow().contentHash().equals(sourceHash)
                    && world.level.getEntity(record.nativeTransfer().orElseThrow().armed().destination()) == null
                    && passenger.getVehicle() == source, "Cancellation reconstructed history or lost the source cargo");
        } else {
            check(source.isRemoved() && survivor.escrowSection().epoch() == 0 && survivor.escrowSection().markers().isEmpty()
                    && data.ledger().id().equals(survivor.escrowSection().ledger())
                    && survivor.snapshot().orElseThrow().snapshotId().equals(record.destinationSnapshot().snapshotId()),
                    "Handoff lost its fixed successor identity or independent epoch");
        }
        return true;
    }

    private boolean begin() {
        var level = world.level; var server = world.server;
        if (cancel && passenger == null) {
            passenger = new ServerPlayer(server, level, new GameProfile(source.ownerId().orElseThrow(), "W131Cancel")) {
                @Override public boolean isCreative() { return true; }
                @Override public boolean isSpectator() { return false; }
            };
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            new io.netty.channel.embedded.EmbeddedChannel(connection); server.getPlayerList().placeNewPlayer(connection, passenger);
            world.players.add(passenger);
            passenger.teleportTo(level, source.getX(), source.getY(), source.getZ(), 0, 0);
            RocketRuntime.requestFlightIntent(passenger, source.getId(), RocketFlightAction.BOARD,
                    new TravelTarget.BodySurface(CelestialIds.EARTH_ID), UUID.randomUUID());
            check(passenger.getVehicle() == source && source.flightData().orElseThrow().passengers().assignment(passenger.getUUID()).isPresent(),
                    "The released cargo rocket did not board its passenger");
        }
        long now = level.getGameTime(); var flight = source.flightData().orElseThrow(); var fuel = flight.fuel();
        // The plan starts at the rocket's current body; a successor that landed on the Moon's plan flies back to Earth's.
        var from = flight.currentBody(); var target = RocketFlightPlanner.MOON.bodyId().equals(from) ? RocketFlightPlanner.EARTH.bodyId() : RocketFlightPlanner.MOON.bodyId();
        var plan = new RocketFlightPlan(3, transfer, from, target,
                level.dimension().location(), level.dimension().location(), 300, now).withPropulsionDebit(new RocketPropulsionDebit(300, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W131 cargo launch")));
        var countdown = flight.withPlan(plan).startCountdown(now);
        if (!EscrowGovernedPreparation.available(source, countdown)) { return false; }
        RocketStructureSnapshot to = source.snapshot().orElseThrow().relocated(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(destination.getX(), destination.getY(), destination.getZ()), now);
        check(new io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector().availableLoaded(level, to),
                "The cargo launch's landing area is not clear");
        launch = new EscrowGameTestLaunch(helper, world.data().ledger().id(), flight.logicalRocketId(), transfer,
                (accept, discard) -> EscrowGovernedPreparation.begin(source, countdown, to, candidate -> true, accept, discard),
                accepted -> this.accepted = true, () -> helper.fail("The cargo launch's F1 preparation was discarded"));
        launch.begin();
        return false;
    }

    private void check(boolean value, String message) { helper.assertTrue(value, message); }

    @Override public void close() {
        if (leaseCut != null) { leaseCut.close(); leaseCut = null; }
        if (launch != null) { launch.close(); }
    }
}
