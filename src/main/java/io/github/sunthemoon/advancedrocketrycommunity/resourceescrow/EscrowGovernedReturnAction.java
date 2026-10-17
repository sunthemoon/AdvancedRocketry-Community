package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Exact-source return after F2, with fresh own-slot absence proofs even after F4. Not F7 authority. */
public final class EscrowGovernedReturnAction {
    private EscrowGovernedReturnAction() { }

    static boolean phase(RocketTransferRecord record) {
        return record.phase() == RocketTransferPhase.PREPARED || record.phase() == RocketTransferPhase.DESTINATION_SPAWNED;
    }

    static RocketFlightData flight(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(EscrowTags.compound(envelope, "flight")).data().orElseThrow();
    }

    /** A returned progression is distinct from F1's E; its clock is not independent authority. */
    static boolean returned(RocketTransferRecord record, CompoundTag source) {
        if (!phase(record) || source == null) { return false; }
        try {
            var expected = record.nativeTransfer().orElseThrow().sourceEnvelope(record);
            var initial = flight(expected); var actual = flight(source);
            long started = actual.stateStartedGameTime();
            if (started <= initial.stateStartedGameTime() || started < record.createdAtGameTime()) { return false; }
            var next = initial.markFailed(started).recover(initial.fuel().availableForLaunch(), started);
            expected.put("flight", RocketFlightNbtCodec.encode(next));
            EscrowGovernedPhaseAction.riders(record, source);
            return EscrowGovernedEnvelope.same(expected, source);
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) { return false; }
    }

    static Permit issue(EscrowGovernedDispatch.Authority authority, CompoundTag source, long phaseTime, long levelTime,
                        Supplier<CompoundTag> observed, BooleanSupplier boundary, BooleanSupplier jointAbsence) {
        EscrowTags.require(authority != null && authority.valid() && authority.decision().route() == EscrowGovernedDispatch.Route.ACTIVE
                && phase(authority.record()) && boundary.getAsBoolean() && jointAbsence.getAsBoolean(),
                "Return lacks checked authority and joint absence proofs");
        var record = authority.record();
        EscrowTags.require(EscrowGovernedPhaseAction.sourceMatches(record, source, phaseTime), "Return source changed");
        var current = flight(source);
        long resumed = EscrowGovernedSourceClock.resume(record,
                EscrowGovernedPhaseAction.sourceComparison(record, source), phaseTime).at(phaseTime);
        long now = Math.max(resumed, Math.addExact(flight(record.nativeTransfer().orElseThrow().sourceEnvelope(record))
                .stateStartedGameTime(), 1));
        var after = current.markFailed(now).recover(current.fuel().availableForLaunch(), now);
        EscrowTags.require(current.fuel().equals(after.fuel()) && current.passengers().equals(after.passengers()),
                "Return changed resources or assignments");
        var origin = record.sourceSnapshot().sourceOrigin();
        return new Permit(authority, source, observed, boundary, jointAbsence, levelTime,
                new EscrowGovernedSourceRecovery.Change(after, new Vec3(origin.x() + 0.5, origin.y(), origin.z() + 0.5)));
    }

    public static final class Permit {
        private final EscrowGovernedDispatch.Authority authority;
        private final CompoundTag before;
        private final Supplier<CompoundTag> observed;
        private final BooleanSupplier boundary;
        private final BooleanSupplier jointAbsence;
        private final long tick;
        private final EscrowGovernedSourceRecovery.Change change;
        private boolean used;

        private Permit(EscrowGovernedDispatch.Authority authority, CompoundTag before, Supplier<CompoundTag> observed,
                BooleanSupplier boundary, BooleanSupplier jointAbsence, long tick, EscrowGovernedSourceRecovery.Change change) {
            this.authority = authority; this.before = before.copy(); this.observed = observed;
            this.boundary = boundary; this.jointAbsence = jointAbsence; this.tick = tick; this.change = change;
        }

        public EscrowGovernedSourceRecovery.Change consume(RocketEntity rocket) {
            return consume(() -> {
                EscrowTags.require(rocket.isAlive() && rocket.isAddedToWorld() && !rocket.level().isClientSide
                        && rocket.getServer().isSameThread() && rocket.level().getGameTime() == tick, "Return lost its owner tick");
                return EscrowNativeEnvelope.rocket(rocket);
            });
        }

        EscrowGovernedSourceRecovery.Change consume(Supplier<CompoundTag> source) {
            EscrowTags.require(!used, "Return permit already consumed"); used = true;
            EscrowTags.require(authority.valid() && boundary.getAsBoolean() && jointAbsence.getAsBoolean()
                    && EscrowGovernedEnvelope.same(before, observed.get()) && EscrowGovernedEnvelope.same(before, source.get()), "Return lost its final authority or source");
            return change;
        }
    }
}
