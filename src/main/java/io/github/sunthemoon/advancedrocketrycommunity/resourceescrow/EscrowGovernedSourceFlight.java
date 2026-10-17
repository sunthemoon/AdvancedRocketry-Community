package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/** Source-only F3 executor. TRANSIT readiness is not permission to publish a destination. */
public final class EscrowGovernedSourceFlight {
    public enum Result { HELD, COUNTDOWN, ASCENT, TRANSIT, DESTINATION_DUE }
    private final Map<UUID, Binding> bindings = new HashMap<>();
    private final EscrowGovernedEnvelopeHolds envelopeHolds = new EscrowGovernedEnvelopeHolds();

    public Result tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity source, BooleanSupplier boundary) {
        try { return tickChecked(server, journal, record, source, boundary); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) { return Result.HELD; }
    }

    private Result tickChecked(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity source, BooleanSupplier boundary) {
        if (!server.isSameThread() || source == null || !boundary.getAsBoolean()) { return Result.HELD; }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return Result.HELD; }
        var authority = EscrowRuntime.of(server).governedFlights().authority(data::ledger,
                () -> data.bind(server).governedAvailable(record.logicalRocketId()), journal, record);
        if (authority == null || authority.decision().route() != EscrowGovernedDispatch.Route.ACTIVE
                || record.phase() != RocketTransferPhase.PREPARED) { return Result.HELD; }
        bindings.entrySet().removeIf(entry -> journal.find(entry.getValue().authority.record().transferId()).isEmpty());
        envelopeHolds.retain(logical -> journal.findByLogicalRocket(logical).isPresent());
        long levelTime = source.level().getGameTime();
        var binding = bindings.get(record.logicalRocketId());
        if (binding != null && binding.entity != source && binding.entity.isRemoved()) {
            // A loaded replacement must prove complete E again. Keep no clock or native observation
            // from its old entity instance; a second still-live instance remains a conflict.
            bindings.remove(record.logicalRocketId()); binding = null;
        }
        if (binding != null && !binding.sameNative(source)) { return Result.HELD; }
        if (binding == null || !binding.valid(source, boundary)) {
            var previous = binding;
            var clock = envelopeHolds.capture(record.logicalRocketId(), data.ledger(), source, null, () -> {
                var observed = EscrowGovernedEnvelope.capture(source);
                if (previous != null) { observed = EscrowGovernedFrame.normalized(observed, previous.frame, previous.flight.state()); }
                var candidate = previous == null ? EscrowGovernedSourceClock.resume(record, observed, levelTime) : previous.clock;
                return EscrowGovernedSourceRecovery.sourceMatches(record, observed, candidate.at(levelTime)) ? candidate : null;
            });
            if (clock == null) { return Result.HELD; }
            if (binding == null && bindings.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { return Result.HELD; }
            binding = new Binding(authority, source, clock);
            if (previous != null) { binding.frame = EscrowGovernedFrame.normalized(binding.frame, previous.frame, previous.flight.state()); }
            bindings.put(record.logicalRocketId(), binding);
        }
        long now = binding.clock.at(levelTime);
        var flight = source.flightData().orElseThrow();
        if (flight.state() != RocketFlightState.COUNTDOWN && flight.state() != RocketFlightState.ASCENT
                && flight.state() != RocketFlightState.TRANSIT) {
            // An acknowledged F2 may have stopped before its countdown callback. Only exact E reaches here.
            return transition(source, binding, EscrowGovernedSourceAction.Action.START_COUNTDOWN, now, boundary, Result.COUNTDOWN);
        }
        long elapsed = now <= flight.stateStartedGameTime() ? 0 : now - flight.stateStartedGameTime();
        if (flight.state() == RocketFlightState.COUNTDOWN) {
            return elapsed < RocketFlightLimits.COUNTDOWN_TICKS ? Result.COUNTDOWN
                    : transition(source, binding, EscrowGovernedSourceAction.Action.COMPLETE_COUNTDOWN, now, boundary, Result.ASCENT);
        }
        if (flight.state() == RocketFlightState.ASCENT) {
            source.moveGovernedSource(binding.motion(source, levelTime, boundary));
            return elapsed < RocketFlightLimits.ASCENT_TICKS ? Result.ASCENT
                    : transition(source, binding, EscrowGovernedSourceAction.Action.BEGIN_TRANSIT, now, boundary, Result.TRANSIT);
        }
        return elapsed < RocketFlightLimits.TRANSIT_TICKS ? Result.TRANSIT : Result.DESTINATION_DUE;
    }

    private Result transition(RocketEntity source, Binding binding,
            EscrowGovernedSourceAction.Action action, long now, BooleanSupplier boundary, Result result) {
        var authority = binding.authority;
        var permit = EscrowGovernedSourceAction.issue(authority, action, EscrowGovernedEnvelope.capture(source), now,
                source.level().getGameTime(), boundary);
        source.advanceGovernedSource(permit);
        bindings.put(authority.record().logicalRocketId(), new Binding(authority, source, binding.clock));
        return result;
    }

    public void clear() { bindings.clear(); envelopeHolds.clear(); }

    /** F4 uses the checked source's logical phase clock, not a rolled-back Level timestamp. */
    long publicationTime(EscrowGovernedDispatch.Authority authority, RocketEntity source, BooleanSupplier boundary) {
        EscrowTags.require(authority != null && authority.valid()
                && authority.decision().route() == EscrowGovernedDispatch.Route.ACTIVE && boundary.getAsBoolean(),
                "Publication lost its source authority");
        var record = authority.record();
        EscrowTags.require(record.phase() == RocketTransferPhase.PREPARED || record.phase() == RocketTransferPhase.DESTINATION_SPAWNED,
                "Publication source phase changed");
        var binding = bindings.get(record.logicalRocketId());
        if (binding != null && binding.entity != source && binding.entity.isRemoved()) {
            bindings.remove(record.logicalRocketId()); binding = null;
        }
        EscrowTags.require(binding == null || binding.sameNative(source)
                && binding.authority.record().nativeTransfer().equals(record.nativeTransfer()), "Publication source changed");
        if (binding == null) {
            EscrowTags.require(bindings.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS, "Source binding capacity exhausted");
            var clock = EscrowGovernedSourceClock.resume(record, EscrowGovernedEnvelope.capture(source), source.level().getGameTime());
            binding = new Binding(authority, source, clock); bindings.put(record.logicalRocketId(), binding);
        }
        return binding.clock.at(source.level().getGameTime());
    }

    MotionPermit movement(RocketEntity source, BooleanSupplier boundary) {
        var binding = source.assemblyTransactionId().map(bindings::get).orElse(null);
        EscrowTags.require(binding != null, "No validated source binding");
        return binding.motion(source, source.level().getGameTime(), boundary);
    }

    /** Observe the normalized moving frame; all persistent native fields still invalidate the binding. */
    void observe(RocketEntity rocket) {
        var binding = rocket.assemblyTransactionId().map(bindings::get).orElse(null);
        if (binding != null && binding.entity == rocket) {
            try { if (!binding.sameNative(rocket)) { binding.changed = true; } }
            catch (IllegalArgumentException | IllegalStateException invalid) { binding.changed = true; }
        }
    }

    /** No snapshot serialization/hash on interpolation ticks. Every transition still validates complete E. */
    private static final class Binding {
        private final EscrowGovernedDispatch.Authority authority;
        private final RocketEntity entity;
        private final RocketStructureSnapshot snapshot;
        private final RocketFlightData flight;
        private final RocketEscrowSection wrapper;
        private final long observation;
        private final EscrowGovernedSourceClock clock;
        private CompoundTag frame;
        private boolean changed;

        Binding(EscrowGovernedDispatch.Authority authority, RocketEntity entity, EscrowGovernedSourceClock clock) {
            this.authority = authority; this.entity = entity; snapshot = entity.snapshot().orElseThrow();
            this.clock = clock;
            flight = entity.flightData().orElseThrow(); wrapper = entity.escrowSection();
            observation = entity.governedObservation(); frame = EscrowGovernedEnvelope.frame(entity);
        }
        boolean valid(RocketEntity rocket, BooleanSupplier boundary) {
            return authority.valid() && boundary.getAsBoolean() && observation == rocket.governedObservation() && sameNative(rocket);
        }
        boolean sameNative(RocketEntity rocket) {
            return !changed && entity == rocket && rocket.isAlive() && rocket.isAddedToWorld() && rocket.operational()
                    && rocket.snapshot().orElseThrow() == snapshot && rocket.flightData().orElseThrow() == flight
                    && rocket.escrowSection().equals(wrapper) && EscrowGovernedFrame.matches(authority.record(), rocket, frame, flight.state());
        }
        MotionPermit motion(RocketEntity rocket, long now, BooleanSupplier boundary) {
            EscrowTags.require(valid(rocket, boundary) && flight.state() == RocketFlightState.ASCENT, "Invalid ascent binding");
            var origin = authority.record().sourceSnapshot().sourceOrigin();
            double y = ascentHeight(origin.y(), flight.stateStartedGameTime(), clock.at(now));
            EscrowTags.require(y >= frame.getList("pos", 6).getDouble(1), "Ascent clock moved backwards");
            return new MotionPermit(this, boundary, now, new Vec3(origin.x() + 0.5, y, origin.z() + 0.5));
        }
    }

    static double ascentHeight(int originY, long started, long now) {
        EscrowTags.require(started >= 0 && now >= started, "Invalid ascent clock");
        return originY + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS
                * Math.min(1.0, (double) (now - started) / RocketFlightLimits.ASCENT_TICKS);
    }

    /** Tick-local one-use movement, not a mount, resource, transition or external movement exemption. */
    public static final class MotionPermit {
        private final Binding binding;
        private final BooleanSupplier boundary;
        private final long tick;
        private final Vec3 position;
        private boolean used;
        private MotionPermit(Binding binding, BooleanSupplier boundary, long tick, Vec3 position) {
            this.binding = binding; this.boundary = boundary; this.tick = tick; this.position = position;
        }
        public Vec3 consume(RocketEntity rocket) {
            EscrowTags.require(!used, "Ascent permit already consumed"); used = true;
            EscrowTags.require(!rocket.level().isClientSide && rocket.getServer().isSameThread()
                    && rocket.level().getGameTime() == tick && binding.valid(rocket, boundary), "Ascent binding changed before effect");
            var pos = new ListTag(); pos.add(DoubleTag.valueOf(position.x));
            pos.add(DoubleTag.valueOf(position.y)); pos.add(DoubleTag.valueOf(position.z));
            binding.frame.put("pos", pos);
            return position;
        }
    }
}
