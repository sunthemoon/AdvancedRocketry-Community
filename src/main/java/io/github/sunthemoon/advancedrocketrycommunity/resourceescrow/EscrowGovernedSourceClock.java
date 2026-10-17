package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Session-local phase time. A restored Level clock is not flight or resource authority. */
final class EscrowGovernedSourceClock {
    private final long levelBase;
    private final long phaseBase;
    private long lastLevelTime;

    private EscrowGovernedSourceClock(long levelBase, long phaseBase) {
        this.levelBase = levelBase; this.phaseBase = phaseBase; lastLevelTime = levelBase;
    }

    /** Called only after checked ARMED/PREPARED authority and the loaded candidate boundary. */
    static EscrowGovernedSourceClock resume(RocketTransferRecord record, CompoundTag observed, long levelTime) {
        EscrowTags.require(levelTime >= 0, "Invalid loaded Level clock");
        if (EscrowGovernedSourceRecovery.sourceMatches(record, observed, levelTime)) {
            return new EscrowGovernedSourceClock(levelTime, levelTime);
        }
        var flight = RocketFlightNbtCodec.decode(EscrowTags.compound(observed, "flight")).data().orElseThrow();
        long phaseTime = Math.max(record.createdAtGameTime(), flight.stateStartedGameTime());
        if (flight.state() == RocketFlightState.ASCENT) {
            var position = observed.getList("pos", Tag.TAG_DOUBLE);
            EscrowTags.require(position.size() == 3, "Invalid restored source position");
            double height = position.getDouble(1) - record.sourceSnapshot().sourceOrigin().y();
            EscrowTags.require(Double.isFinite(height) && height >= 0 && height <= RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                    "Restored ascent is outside its column");
            long elapsed = (long) Math.ceil(height * RocketFlightLimits.ASCENT_TICKS / RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS);
            phaseTime = Math.max(phaseTime, Math.addExact(flight.stateStartedGameTime(), elapsed));
        }
        // Only clock rollback is repaired. The same complete comparison still checks phase order,
        // plan, manifest, resources, debits, wrapper, column and every other native field.
        EscrowTags.require(phaseTime > levelTime && EscrowGovernedSourceRecovery.sourceMatches(record, observed, phaseTime),
                "Restored source differs from its permitted progression");
        return new EscrowGovernedSourceClock(levelTime, phaseTime);
    }

    long at(long levelTime) {
        EscrowTags.require(levelTime >= lastLevelTime, "Loaded Level clock moved backwards during flight");
        long phaseTime = Math.addExact(phaseBase, levelTime - levelBase);
        lastLevelTime = levelTime;
        return phaseTime;
    }
}
