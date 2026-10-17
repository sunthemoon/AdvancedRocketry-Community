package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** F5 acknowledgements precede their effects. COMMITTED does not release ARMED or prove storage. */
public final class EscrowGovernedPhases {
    public enum Result { HELD, WAITING, COMMITTED }
    private Mount mount;
    private final EscrowGovernedPhaseTracking tracking = new EscrowGovernedPhaseTracking();
    private final EscrowTerminalRetries passengerRetries;
    public EscrowGovernedPhases() { passengerRetries = new EscrowTerminalRetries(); }
    EscrowGovernedPhases(java.util.function.Consumer<EscrowSuccessorRetry.Hold> logger) {
        passengerRetries = new EscrowTerminalRetries(logger);
    }
    public boolean allowsNative(RocketEntity source, RocketEntity destination) { return tracking.allows(source) && tracking.allows(destination); }

    /** A retired flight's observations cannot constrain its successor's next flight. */
    public boolean releaseCompleted(EscrowCoordinator coordinator, RocketTransferSavedData journal, EscrowFlightTables.Armed terminal) {
        if (!EscrowGovernedCleanup.absent(coordinator, journal, terminal)) { return false; }
        tracking.completed(terminal);
        passengerRetries.completed(terminal.logical());
        return true;
    }
    void observe(RocketEntity rocket) { tracking.observe(rocket); }
    void follow(RocketEntity rocket, io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData flight, Vec3 position) {
        tracking.follow(rocket, flight, position);
    }
    void clear() { mount = null; tracking.clear(); passengerRetries.clear(); }

    public Result tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity source, RocketEntity destination, BooleanSupplier boundary) {
        if (!server.isSameThread()) { return Result.HELD; }
        if (EscrowRuntime.of(server).destinationCutActive()) { return Result.WAITING; }
        if (journal.actionAuthorityAvailable()) { passengerRetries.retain(logical -> journal.findByLogicalRocket(logical).isPresent()); }
        if (!passengerAttemptDue(record.logicalRocketId(), server.getTickCount())) { return Result.HELD; }
        try { return checked(server, journal, record, source, destination, boundary); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) {
            var checked = authority(server, journal, record);
            if (!EscrowGovernedFrame.transientMotion(source) && !EscrowGovernedFrame.transientMotion(destination)
                    && boundary.getAsBoolean() && checked != null && checked.decision().route() == EscrowGovernedDispatch.Route.ACTIVE) {
                EscrowRuntime.of(server).executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.PHASE_ENVELOPE);
            }
            return Result.HELD;
        }
        finally { mount = null; }
    }

    private Result checked(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                           RocketEntity source, RocketEntity destination, BooleanSupplier boundary) {
        var authority = authority(server, journal, record);
        if (authority == null || authority.decision().route() != EscrowGovernedDispatch.Route.ACTIVE
                || record.phase() == RocketTransferPhase.PREPARED || destination == null) {
            return Result.HELD;
        }
        if (EscrowGovernedFrame.transientMotion(source) || EscrowGovernedFrame.transientMotion(destination)) { return Result.WAITING; }
        if (!allowsNative(source, destination)) {
            EscrowRuntime.of(server).executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.PHASE_ENVELOPE); return Result.HELD;
        }
        if (!boundary.getAsBoolean()) { return Result.HELD; }
        if (!EscrowGovernedPhaseAction.matches(record, envelope(source), envelope(destination), sourceTime(server, source))) {
            EscrowRuntime.of(server).executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.PHASE_ENVELOPE); return Result.HELD;
        }
        EscrowRuntime.of(server).executorHolds().healthy(record);
        tracking.capture(source, journal); tracking.capture(destination, journal);
        if (record.phase() == RocketTransferPhase.DESTINATION_SPAWNED) {
            // F4's callback never transfers a passenger. This separate checked snapshot does.
            return advance(server, journal, record, RocketTransferPhase.PASSENGERS_TRANSFERRED);
        }
        // Each assignment is bounded by the manifest codec. Offline passengers remain assigned;
        // they are not reconstructed and must still prevent F6/F7 release.
        for (var seat : record.destinationFlightData().passengers().assignments()) {
            var player = server.getPlayerList().getPlayer(seat.passengerId());
            if (player == null) { continue; }
            if (!online(server, player)) { return passengerFailed(record, "PASSENGER_INELIGIBLE", server.getTickCount()); }
            if (player.getVehicle() != destination) {
                if (player.getVehicle() != null && player.getVehicle() != source) {
                    return passengerFailed(record, "PASSENGER_RIDING_OTHER", server.getTickCount());
                }
                if (!move(server, journal, record, source, destination, player, boundary)) {
                    return passengerFailed(record, "PASSENGER_MOVE_REFUSED", server.getTickCount());
                }
            }
        }
        if (source != null && !source.getPassengers().isEmpty()) { return Result.HELD; }
        // Reacquire after every physical dismount/join. Such events invalidate the previous authority.
        var permit = permit(server, journal, record, source, destination, () -> boundary.getAsBoolean()
                && onlinePassengersAtDestination(server, record, destination));
        if (record.phase() == RocketTransferPhase.PASSENGERS_TRANSFERRED) {
            permit.consume(); return advance(server, journal, record, RocketTransferPhase.SOURCE_REMOVED);
        }
        if (EscrowGovernedPhaseAction.sourceOptional(record)) {
            if (source != null) {
                EscrowRuntime.of(server).checkpoint("F5_SOURCE_REMOVAL_BEFORE");
                permit.remove(source);
                EscrowRuntime.of(server).checkpoint("F5_SOURCE_REMOVAL_AFTER");
                // Leave invalidation, loaded absence and a fresh permit must precede COMMITTED.
                return Result.WAITING;
            }
            permit.consume();
            return record.phase() == RocketTransferPhase.SOURCE_REMOVED
                    ? advance(server, journal, record, RocketTransferPhase.COMMITTED) : Result.COMMITTED;
        }
        permit.consume(); return Result.COMMITTED;
    }

    private static Result advance(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                                  RocketTransferPhase phase) {
        if (!journal.canQueueEffect()) { return Result.HELD; }
        var next = record.advance(phase); journal.put(next);
        EscrowRuntime.of(server).checkpoint("F5_" + phase + "_REQUESTED");
        // No captured entity/permit crosses this callback. The dispatcher repeats all checks next visit.
        journal.afterAcknowledged(server, record.logicalRocketId(), () -> {
            if (authority(server, journal, next) == null) { return false; }
            EscrowRuntime.of(server).checkpoint("F5_" + phase + "_ACK"); return true;
        }, () -> { });
        return Result.WAITING;
    }

    boolean passengerAttemptDue(java.util.UUID logical, long tick) { return passengerRetries.ready(logical, tick); }
    Result passengerFailed(RocketTransferRecord record, String reason, long tick) {
        passengerRetries.failed(record, "F5_PASSENGER", reason, tick);
        return Result.HELD;
    }

    private boolean move(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                         RocketEntity source, RocketEntity destination, ServerPlayer player, BooleanSupplier boundary) {
        if (!online(server, player) || player.getVehicle() != null && player.getVehicle() != source) { return false; }
        var beforeSource = envelope(source); var beforeDestination = envelope(destination);
        var permit = permit(server, journal, record, source, destination, () -> boundary.getAsBoolean() && online(server, player));
        permit.consume();
        EscrowRuntime.of(server).checkpoint("F5_PASSENGER_BEFORE");
        player.stopRiding();
        var detachedSource = EscrowGovernedPhaseAction.withoutRider(beforeSource, player.getUUID());
        BooleanSupplier detached = () -> boundary.getAsBoolean() && online(server, player) && player.getVehicle() == null
                && EscrowGovernedEnvelope.same(detachedSource, envelope(source)) && EscrowGovernedEnvelope.same(beforeDestination, envelope(destination));
        // stopRiding posts the ordinary invalidation event. Never suppress it or reuse the old permit.
        permit(server, journal, record, source, destination, detached).consume();
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        player.teleportTo((ServerLevel) destination.level(), destination.getX(), destination.getY() + 0.25,
                destination.getZ(), player.getYRot(), player.getXRot());
        permit(server, journal, record, source, destination, detached).consume();
        if (player.level() != destination.level() || player.distanceToSqr(destination) > 4) { return false; }
        var checked = authority(server, journal, record);
        mount = new Mount(player, destination, checked,
                () -> detached.getAsBoolean() && player.level() == destination.level() && player.distanceToSqr(destination) <= 4);
        boolean mounted;
        try { mounted = player.startRiding(destination, true); }
        finally { mount = null; }
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        // Another mod may veto the mount or change native state in its callback. No later effect follows that change.
        if (!mounted || player.getVehicle() != destination) { return false; }
        var expectedDestination = beforeDestination.copy();
        var rider = new CompoundTag(); rider.putUUID("physical", player.getUUID());
        expectedDestination.getList("passengers", net.minecraft.nbt.Tag.TAG_COMPOUND).add(rider);
        EscrowTags.require(EscrowGovernedEnvelope.same(detachedSource, envelope(source)) && EscrowGovernedEnvelope.same(expectedDestination, envelope(destination)),
                "Passenger callback changed a native endpoint");
        permit(server, journal, record, source, destination, boundary).consume();
        EscrowRuntime.of(server).checkpoint("F5_PASSENGER_AFTER"); return true;
    }

    /** Scoped to this exact synchronous server action; not a public idle/mount exemption. */
    boolean mountAllowed(Entity passenger, Entity destination) {
        var current = mount;
        if (current == null || current.used || current.player != passenger || current.destination != destination) { return false; }
        current.used = true;
        return current.authority != null && current.authority.valid() && current.boundary.getAsBoolean();
    }

    private static boolean online(MinecraftServer server, ServerPlayer player) {
        return server.isSameThread() && player.getServer() == server && !player.isRemoved() && player.isAlive()
                && !player.isSpectator() && player.connection != null && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static boolean onlinePassengersAtDestination(MinecraftServer server, RocketTransferRecord record, RocketEntity destination) {
        return record.destinationFlightData().passengers().assignments().stream().allMatch(seat -> {
            var player = server.getPlayerList().getPlayer(seat.passengerId());
            return player == null || online(server, player) && player.getVehicle() == destination;
        });
    }

    private static EscrowGovernedPhaseAction.Permit permit(MinecraftServer server, RocketTransferSavedData journal,
            RocketTransferRecord record, RocketEntity source, RocketEntity destination, BooleanSupplier boundary) {
        return EscrowGovernedPhaseAction.issue(authority(server, journal, record), envelope(source), envelope(destination), sourceTime(server, source),
                () -> envelope(source), () -> envelope(destination), boundary);
    }
    private static EscrowGovernedDispatch.Authority authority(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        var data = ResourceEscrowSavedData.existing(server);
        return data == null ? null : EscrowRuntime.of(server).governedFlights().authority(data::ledger,
                () -> data.bind(server).governedAvailable(record.logicalRocketId()), journal, record);
    }
    private static CompoundTag envelope(RocketEntity rocket) { return rocket == null ? null : EscrowNativeEnvelope.rocket(rocket); }
    private static long sourceTime(MinecraftServer server, RocketEntity source) {
        return source == null ? server.overworld().getGameTime() : source.level().getGameTime();
    }
    private static final class Mount {
        final ServerPlayer player;
        final RocketEntity destination;
        final EscrowGovernedDispatch.Authority authority;
        final BooleanSupplier boundary;
        boolean used;
        Mount(ServerPlayer player, RocketEntity destination, EscrowGovernedDispatch.Authority authority, BooleanSupplier boundary) {
            this.player = player; this.destination = destination; this.authority = authority; this.boundary = boundary;
        }
    }
}
