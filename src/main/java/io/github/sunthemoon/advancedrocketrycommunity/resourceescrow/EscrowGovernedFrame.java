package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** R17: warm bindings observe fields after the tick's motion/plan normalization, not transient impulses. */
final class EscrowGovernedFrame {
    private EscrowGovernedFrame() { }

    static boolean transientMotion(RocketEntity rocket) {
        return rocket != null && transientMotion(rocket.getDeltaMovement());
    }
    static boolean transientMotion(Vec3 motion) { return !Vec3.ZERO.equals(motion); }
    /** The same rule over a live envelope; a missing or malformed vector is not transient and still mismatches. */
    static boolean transientMotion(CompoundTag observed) {
        var motion = observed.getList("motion", Tag.TAG_DOUBLE);
        return motion.size() == 3 && transientMotion(new Vec3(motion.getDouble(0), motion.getDouble(1), motion.getDouble(2)));
    }

    static boolean moving(RocketFlightState state) {
        return state == RocketFlightState.ASCENT || state == RocketFlightState.DESCENT;
    }
    static CompoundTag normalized(CompoundTag observed, CompoundTag permitted, RocketFlightState state) {
        var result = observed.copy();
        result.put("motion", EscrowGovernedArrival.position(Vec3.ZERO));
        if (moving(state)) {
            result.put("pos", permitted.get("pos").copy()); result.put("chunk", permitted.get("chunk").copy());
        }
        return result;
    }
    static boolean persistentPlacement(CompoundTag permitted, CompoundTag observed, RocketFlightState state) {
        return !moving(state) && (!java.util.Objects.equals(permitted.get("pos"), observed.get("pos"))
                || !java.util.Objects.equals(permitted.get("chunk"), observed.get("chunk")));
    }
    static boolean matches(CompoundTag permitted, CompoundTag observed, RocketFlightState state) {
        return permitted.equals(normalized(observed, permitted, state));
    }
    static boolean matches(RocketTransferRecord record, RocketEntity rocket, CompoundTag permitted, RocketFlightState state) {
        var observed = EscrowGovernedEnvelope.frame(rocket);
        if (persistentPlacement(permitted, observed, state)) {
            var server = rocket.getServer(); var runtime = EscrowRuntime.of(server); var journal = RocketTransferSavedData.get(server);
            placementHold(journal, record, current -> runtime.governedFlights().classify(server, journal, current).route(),
                    current -> runtime.executorHolds().hold(server, current, EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT));
        }
        return matches(permitted, observed, state);
    }

    /**
     * Review N3: like the executors' report(...), R17 item 6 holds only a flight whose exact transfer (T and its ARMED
     * tuple) the journal still holds and classifies ACTIVE. A retired or CLEANUP flight's stale observation logs nothing.
     */
    static boolean placementHold(RocketTransferSavedData journal, RocketTransferRecord record,
                                 Function<RocketTransferRecord, EscrowGovernedDispatch.Route> classify, Consumer<RocketTransferRecord> hold) {
        var armed = record.nativeTransfer().map(EscrowTransferEnvelope::armed);
        var current = journal.find(record.transferId())
                .filter(value -> armed.isPresent() && value.nativeTransfer().map(EscrowTransferEnvelope::armed).equals(armed)).orElse(null);
        if (current == null || classify.apply(current) != EscrowGovernedDispatch.Route.ACTIVE) { return false; }
        hold.accept(current); return true;
    }
}
