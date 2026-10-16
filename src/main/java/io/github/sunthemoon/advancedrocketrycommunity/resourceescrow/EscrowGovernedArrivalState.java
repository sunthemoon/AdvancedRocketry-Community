package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** The complete destination progression, without changing its frozen credit or independent epoch. */
final class EscrowGovernedArrivalState {
    private EscrowGovernedArrivalState() { }

    static RocketFlightData descent(RocketTransferRecord record, long started) {
        var flight = record.destinationFlightData();
        EscrowTags.require(started >= flight.stateStartedGameTime(), "Descent clock precedes its frozen plan");
        return RocketFlightData.restore(flight.schemaVersion(), flight.logicalRocketId(), flight.state(), flight.fuel(),
                flight.plan().orElse(null), flight.passengers(), flight.currentBody(), flight.currentDimension(),
                flight.currentOrigin(), started, flight.activeTransferId().orElse(null), flight.currentTarget().orElseThrow());
    }

    static RocketFlightData flight(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(EscrowTags.compound(envelope, "flight")).data().orElseThrow();
    }

    static double initialHeight(RocketTransferRecord record) {
        return record.nativeTransfer().orElseThrow().destinationEnvelope(record).getList("pos", Tag.TAG_DOUBLE).getDouble(1);
    }

    static double height(RocketTransferRecord record, long started, long now) {
        return height(initialHeight(record), record.destinationSnapshot().sourceOrigin().y(), started, now);
    }

    static double height(double top, int origin, long started, long now) {
        EscrowTags.require(now >= started, "Descent clock moved backwards");
        return top - (top - origin)
                * Math.min(1.0, (double) (now - started) / RocketFlightLimits.DESCENT_TICKS);
    }

    static boolean matches(RocketTransferRecord record, CompoundTag observed, long now) {
        if (record.phase() != RocketTransferPhase.COMMITTED || observed == null) { return false; }
        EscrowGovernedPhaseAction.riders(record, observed);
        var actual = flight(observed); var expected = record.nativeTransfer().orElseThrow().destinationEnvelope(record);
        var origin = record.destinationSnapshot().sourceOrigin(); var position = observed.getList("pos", Tag.TAG_DOUBLE);
        if (position.size() != 3 || position.getDouble(0) != origin.x() + 0.5 || position.getDouble(2) != origin.z() + 0.5
                || actual.stateStartedGameTime() > now) { return false; }
        double y = position.getDouble(1); double top = initialHeight(record);
        if (!Double.isFinite(y) || top < origin.y() || top > origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS
                || y < origin.y() || y > top) { return false; }
        RocketFlightData progression;
        if (actual.state() == RocketFlightState.DESCENT) {
            progression = descent(record, actual.stateStartedGameTime());
            // A stored frame may lag the clock, but cannot be ahead of its permitted descent.
            if (y < height(record, actual.stateStartedGameTime(), now)) { return false; }
        } else if (actual.state() == RocketFlightState.LANDED) {
            if (y != origin.y() || actual.stateStartedGameTime()
                    < Math.addExact(record.destinationFlightData().stateStartedGameTime(), RocketFlightLimits.DESCENT_TICKS)) { return false; }
            progression = record.destinationFlightData().land(actual.stateStartedGameTime());
        } else { return false; }
        expected.put("flight", RocketFlightNbtCodec.encode(progression)); expected.put("pos", position.copy());
        expected.put("passengers", observed.get("passengers").copy());
        return expected.equals(observed);
    }

    /** Recover only a rolled-back Level clock; complete E is still checked at the resulting time. */
    static long resumeTime(RocketTransferRecord record, CompoundTag observed, long levelTime) {
        EscrowTags.require(levelTime >= 0, "Invalid Level clock");
        var flight = flight(observed); long now = Math.max(levelTime, flight.stateStartedGameTime());
        if (flight.state() == RocketFlightState.DESCENT) {
            double top = initialHeight(record); double distance = top - record.destinationSnapshot().sourceOrigin().y();
            double y = observed.getList("pos", Tag.TAG_DOUBLE).getDouble(1);
            EscrowTags.require(Double.isFinite(y) && y <= top && y >= top - distance, "Descent outside recorded column");
            long elapsed = distance == 0 ? 0 : (long) Math.ceil((top - y) * RocketFlightLimits.DESCENT_TICKS / distance);
            now = Math.max(now, Math.addExact(flight.stateStartedGameTime(), elapsed));
        }
        EscrowTags.require(matches(record, observed, now), "Destination differs from its complete progression");
        return now;
    }

    static CompoundTag withoutRiders(CompoundTag envelope) {
        var copy = envelope.copy(); copy.put("passengers", new ListTag()); return copy;
    }
}
