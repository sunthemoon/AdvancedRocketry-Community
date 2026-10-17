package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** R17: warm bindings observe fields after the tick's motion/plan normalization, not transient impulses. */
final class EscrowGovernedFrame {
    private EscrowGovernedFrame() { }

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
            EscrowRuntime.of(rocket.getServer()).executorHolds().hold(rocket.getServer(), record,
                    EscrowGovernedExecutorHolds.Reason.PERSISTENT_PLACEMENT);
        }
        return matches(permitted, observed, state);
    }
}
