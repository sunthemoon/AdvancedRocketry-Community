package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/** COMMITTED descent/landing actions. Neither LANDED nor a position tick releases ARMED. */
public final class EscrowGovernedArrival {
    public enum Result { HELD, DESCENDING, READY_TO_LAND, LANDED }
    private final Map<UUID, Binding> bindings = new HashMap<>();

    public Result tick(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                       RocketEntity destination, BooleanSupplier boundary) {
        if (!server.isSameThread() || EscrowGovernedFrame.transientMotion(destination)) { return Result.HELD; }
        // The separately gated FP_* fixture ends at F5, before this executor's first action.
        if (EscrowRuntime.of(server).phaseCutActive()) { return Result.HELD; }
        try {
            var binding = binding(server, journal, record, destination, boundary);
            if (binding == null) { return Result.HELD; }
            var flight = destination.flightData().orElseThrow();
            if (flight.state() == RocketFlightState.LANDED) { return Result.LANDED; }
            long now = binding.time(destination.level().getGameTime());
            double y = EscrowGovernedArrivalState.height(binding.initialHeight, record.destinationSnapshot().sourceOrigin().y(),
                    flight.stateStartedGameTime(), now);
            EscrowTags.require(y <= binding.frame.getList("pos", 6).getDouble(1), "Descent moved upwards");
            var origin = record.destinationSnapshot().sourceOrigin();
            apply(destination, binding, flight, new Vec3(origin.x() + 0.5, y, origin.z() + 0.5), boundary);
            return now - flight.stateStartedGameTime() >= RocketFlightLimits.DESCENT_TICKS
                    ? Result.READY_TO_LAND : Result.DESCENDING;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) {
            if (boundary.getAsBoolean()) { report(server, journal, record); } return Result.HELD;
        }
    }

    /** Reacquires the exact authority after any deferred world-first acknowledgement. */
    public boolean land(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                        RocketEntity destination, BooleanSupplier boundary) {
        try {
            var binding = binding(server, journal, record, destination, boundary);
            if (binding == null) { return false; }
            var flight = destination.flightData().orElseThrow(); long now = binding.time(destination.level().getGameTime());
            var before = EscrowGovernedEnvelope.capture(destination);
            if (!ready(record, before, now)) { return false; }
            var permit = EscrowGovernedLandingAction.issue(binding.authority, before, now, boundary);
            apply(destination, binding, permit.consume(() -> EscrowGovernedEnvelope.capture(destination)), destination.position(), boundary);
            EscrowRuntime.of(server).checkpoint("F6_LANDED"); return true;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) { return false; }
    }

    public boolean ready(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                         RocketEntity destination, BooleanSupplier boundary) {
        try {
            var binding = binding(server, journal, record, destination, boundary);
            return binding != null && ready(record, EscrowGovernedEnvelope.capture(destination), binding.time(destination.level().getGameTime()));
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException refused) { return false; }
    }

    static boolean ready(RocketTransferRecord record, CompoundTag envelope, long now) {
        var flight = EscrowGovernedArrivalState.flight(envelope);
        return flight.state() == RocketFlightState.DESCENT && now >= flight.stateStartedGameTime()
                && now - flight.stateStartedGameTime() >= RocketFlightLimits.DESCENT_TICKS
                && envelope.getList("pos", 6).getDouble(1) == record.destinationSnapshot().sourceOrigin().y()
                && EscrowGovernedArrivalState.matches(record, envelope, now);
    }

    private Binding binding(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record,
                            RocketEntity destination, BooleanSupplier boundary) {
        if (!server.isSameThread() || destination == null || record.phase() != RocketTransferPhase.COMMITTED
                || EscrowGovernedFrame.transientMotion(destination) || !boundary.getAsBoolean()) { return null; }
        if (!EscrowRuntime.of(server).flightPhases().allowsNative(null, destination)) { report(server, journal, record); return null; }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return null; }
        var authority = EscrowRuntime.of(server).governedFlights().authority(data::ledger,
                () -> data.bind(server).governedAvailable(record.logicalRocketId()), journal, record);
        if (authority == null || authority.decision().route() != EscrowGovernedDispatch.Route.ACTIVE) { return null; }
        bindings.entrySet().removeIf(entry -> journal.find(entry.getKey()).isEmpty());
        var old = bindings.get(record.transferId());
        if (old != null && old.entity != destination && old.entity.isRemoved()) { bindings.remove(record.transferId()); old = null; }
        if (old != null && !old.sameNative(destination)) { report(server, journal, record); return null; }
        if (old != null && old.authority.valid() && old.observation == destination.governedObservation()) {
            EscrowRuntime.of(server).executorHolds().healthy(record); return old;
        }
        var observed = EscrowGovernedEnvelope.capture(destination); long levelTime = destination.level().getGameTime();
        if (old != null) { observed = EscrowGovernedFrame.normalized(observed, old.frame, ((RocketFlightData) old.flight).state()); }
        long now = old == null ? EscrowGovernedArrivalState.resumeTime(record, observed, levelTime) : old.time(levelTime);
        EscrowTags.require(EscrowGovernedArrivalState.matches(record, observed, now), "Arrival complete envelope changed");
        EscrowRuntime.of(server).executorHolds().healthy(record);
        requireCapacity(bindings, record.transferId());
        var binding = new Binding(authority, destination, levelTime, now);
        if (old != null) { binding.frame = EscrowGovernedFrame.normalized(binding.frame, old.frame, ((RocketFlightData) old.flight).state()); }
        bindings.put(record.transferId(), binding);
        if (old == null && EscrowGovernedArrivalState.withoutRiders(observed)
                .equals(record.nativeTransfer().orElseThrow().destinationEnvelope(record))) {
            // Publication time was frozen before F1. Start the live descent at this checked COMMITTED visit.
            apply(destination, binding, EscrowGovernedArrivalState.descent(record, now), destination.position(), boundary);
        }
        return binding;
    }

    private static void apply(RocketEntity rocket, Binding binding, RocketFlightData flight, Vec3 position, BooleanSupplier boundary) {
        var permit = new Permit(binding, boundary, rocket.level().getGameTime(), flight, position);
        rocket.advanceGovernedArrival(permit);
        EscrowTags.require(binding.sameNative(rocket) && rocket.position().equals(position), "Native fields changed inside the arrival effect");
        binding.observation = rocket.governedObservation();
    }

    void observe(RocketEntity rocket) {
        for (var binding : bindings.values()) {
            if (binding.entity != rocket) { continue; }
            try { if (!binding.sameNative(rocket)) { binding.changed = true; } }
            catch (IllegalArgumentException | IllegalStateException invalid) { binding.changed = true; }
        }
    }
    void clear() { bindings.clear(); }

    /** Review N3: F8 drops the retired flight's binding with F5's captures; it supplied no authority. */
    public void completed(EscrowFlightTables.Armed terminal) {
        bindings.values().removeIf(binding -> EscrowGovernedPhaseTracking.completedCapture(
                binding.authority.record().nativeTransfer().orElseThrow().armed(), terminal));
    }
    boolean bound(UUID transfer) { return bindings.containsKey(transfer); }

    private static void report(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
        var runtime = EscrowRuntime.of(server);
        if (runtime.governedFlights().classify(server, journal, record).route() == EscrowGovernedDispatch.Route.ACTIVE) {
            runtime.executorHolds().hold(server, record, EscrowGovernedExecutorHolds.Reason.ARRIVAL_ENVELOPE);
        }
    }

    static void requireCapacity(Map<UUID, ?> bindings, UUID transfer) {
        EscrowTags.require(bindings.containsKey(transfer) || bindings.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                "Arrival binding limit exceeded");
    }

    private static CompoundTag frame(RocketEntity rocket) {
        return EscrowGovernedEnvelope.frame(rocket);
    }

    private static final class Binding {
        final EscrowGovernedDispatch.Authority authority;
        final RocketEntity entity;
        final Object snapshot;
        final RocketEscrowSection wrapper;
        final long levelBase;
        final long phaseBase;
        final double initialHeight;
        long lastTime;
        long observation;
        Object flight;
        CompoundTag frame;
        boolean changed;
        Binding(EscrowGovernedDispatch.Authority authority, RocketEntity entity, long levelTime, long phaseTime) {
            this.authority = authority; this.entity = entity; snapshot = entity.snapshot().orElseThrow();
            initialHeight = EscrowGovernedArrivalState.initialHeight(authority.record());
            wrapper = entity.escrowSection(); levelBase = levelTime; phaseBase = phaseTime; lastTime = levelTime; refresh(entity);
        }
        void refresh(RocketEntity rocket) {
            flight = rocket.flightData().orElseThrow(); frame = frame(rocket); observation = rocket.governedObservation();
        }
        boolean sameNative(RocketEntity rocket) {
            if (changed) { return false; }
            boolean same = entity == rocket && rocket.isAlive() && rocket.isAddedToWorld() && rocket.operational()
                    && rocket.snapshot().orElse(null) == snapshot && rocket.flightData().orElse(null) == flight
                    && rocket.escrowSection().equals(wrapper) && EscrowGovernedFrame.matches(authority.record(), rocket, frame, ((RocketFlightData) flight).state());
            if (!same) { changed = true; } return same;
        }
        long time(long levelTime) {
            EscrowTags.require(levelTime >= lastTime, "Loaded descent clock moved backwards"); lastTime = levelTime;
            return Math.addExact(phaseBase, levelTime - levelBase);
        }
    }

    /** Owner-tick, exact bound-state permit. Constructors are private to the checked executor. */
    public static final class Permit {
        private final Binding binding;
        private final BooleanSupplier boundary;
        private final long tick;
        private final RocketFlightData flight;
        private final Vec3 position;
        private boolean used;
        private Permit(Binding binding, BooleanSupplier boundary, long tick, RocketFlightData flight, Vec3 position) {
            this.binding = binding; this.boundary = boundary; this.tick = tick; this.flight = flight; this.position = position;
        }
        public EscrowGovernedSourceRecovery.Change consume(RocketEntity rocket) {
            EscrowTags.require(!used, "Arrival permit already consumed"); used = true;
            EscrowTags.require(!rocket.level().isClientSide && rocket.getServer().isSameThread()
                    && rocket.level().getGameTime() == tick && binding.authority.valid() && boundary.getAsBoolean()
                    && binding.sameNative(rocket), "Arrival lost its final bound authority");
            EscrowRuntime.of(rocket.getServer()).flightPhases().follow(rocket, flight, position);
            // Record the exact permitted native change before setPos can trigger an observation.
            binding.flight = flight; binding.frame.put("pos", position(position));
            return new EscrowGovernedSourceRecovery.Change(flight, position);
        }
    }

    static ListTag position(Vec3 position) {
        var tag = new ListTag(); tag.add(DoubleTag.valueOf(position.x)); tag.add(DoubleTag.valueOf(position.y));
        tag.add(DoubleTag.valueOf(position.z)); return tag;
    }
}
