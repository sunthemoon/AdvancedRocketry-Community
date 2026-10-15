package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;

/** One-use, non-resource source transitions; never KEEP_SOURCE, reconstruction or a general idle exemption. */
public final class EscrowGovernedSourceAction {
    public enum Action { START_COUNTDOWN, COMPLETE_COUNTDOWN, BEGIN_TRANSIT }
    private EscrowGovernedSourceAction() { }

    public static final class Permit {
        private final EscrowGovernedDispatch.Authority authority;
        private final CompoundTag before;
        private final RocketFlightData after;
        private final BooleanSupplier boundary;
        private final long gameTime;
        private boolean used;

        private Permit(EscrowGovernedDispatch.Authority authority, CompoundTag before,
                       RocketFlightData after, BooleanSupplier boundary, long gameTime) {
            this.authority = authority; this.before = before.copy(); this.after = after; this.boundary = boundary; this.gameTime = gameTime;
        }

        public RocketFlightData consume(RocketEntity rocket) {
            return consume(() -> {
                EscrowTags.require(rocket.isAlive() && !rocket.level().isClientSide && rocket.getServer().isSameThread()
                        && rocket.level().getGameTime() == gameTime, "Governed source action requires its live server-owned tick");
                return EscrowNativeEnvelope.rocket(rocket);
            });
        }

        RocketFlightData consume(java.util.function.Supplier<CompoundTag> observed) {
            EscrowTags.require(!used, "Governed source action was already consumed"); used = true;
            EscrowTags.require(authority.valid() && boundary.getAsBoolean() && before.equals(observed.get()),
                    "Governed source action lost its checked authority or complete source");
            return after;
        }
    }

    /** The executor supplies its final bay/transaction/loaded-candidate boundary, not a caller's fresh flag. */
    static Permit issue(EscrowGovernedDispatch.Authority authority, Action action,
                        CompoundTag observed, long gameTime, BooleanSupplier boundary) {
        java.util.Objects.requireNonNull(authority); java.util.Objects.requireNonNull(action);
        java.util.Objects.requireNonNull(boundary);
        EscrowTags.require(gameTime >= 0 && authority.valid() && boundary.getAsBoolean()
                && authority.decision().route() == EscrowGovernedDispatch.Route.ACTIVE
                && authority.record().phase() == RocketTransferPhase.PREPARED, "Source progression lacks checked PREPARED authority");
        var record = authority.record();
        var expected = record.nativeTransfer().orElseThrow().sourceEnvelope(record);
        var initial = flight(expected);
        EscrowTags.require(gameTime >= initial.stateStartedGameTime(), "Source action clock moved backwards");
        var actual = flight(observed);
        var planned = initial.withPlan(record.sourceFlightData().plan().orElseThrow());
        RocketFlightData before;
        RocketFlightData after;
        switch (action) {
            case START_COUNTDOWN -> { before = initial; after = planned.startCountdown(gameTime); }
            case COMPLETE_COUNTDOWN -> {
                before = planned.startCountdown(actual.stateStartedGameTime());
                EscrowTags.require(elapsed(gameTime, before.stateStartedGameTime()) >= RocketFlightLimits.COUNTDOWN_TICKS,
                        "Countdown has not completed");
                after = before.completeCountdown(gameTime);
            }
            case BEGIN_TRANSIT -> {
                before = planned.startCountdown(actual.stateStartedGameTime()).completeCountdown(actual.stateStartedGameTime());
                EscrowTags.require(elapsed(gameTime, before.stateStartedGameTime()) >= RocketFlightLimits.ASCENT_TICKS,
                        "Ascent has not completed");
                var origin = record.sourceSnapshot().sourceOrigin();
                // The bounded interpolation reaches this exact position before the transition.
                var pos = new ListTag(); pos.add(DoubleTag.valueOf(origin.x() + 0.5));
                pos.add(DoubleTag.valueOf(origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS));
                pos.add(DoubleTag.valueOf(origin.z() + 0.5)); expected.put("pos", pos);
                after = before.beginTransit(record.transferId(), gameTime);
            }
            default -> throw new IllegalArgumentException("Unsupported source action");
        }
        expected.put("flight", RocketFlightNbtCodec.encode(before));
        EscrowTags.require(expected.equals(observed), "Source differs from the permitted complete progression");
        EscrowTags.require(before.fuel().equals(after.fuel()), "Source action changed resources");
        return new Permit(authority, observed, after, boundary, gameTime);
    }

    private static RocketFlightData flight(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(EscrowTags.compound(envelope, "flight")).data()
                .orElseThrow(() -> new IllegalArgumentException("Invalid source flight envelope"));
    }
    private static long elapsed(long now, long started) { return now <= started ? 0 : now - started; }
}
