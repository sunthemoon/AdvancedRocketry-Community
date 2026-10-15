package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Present-source actions only. Neither a loaded absence nor this permit is an F6 storage proof. */
public final class EscrowGovernedSourceRecovery {
    public enum Action { RESUME_EXACT_SOURCE, RETURN_EXACT_SOURCE }

    /** An executor's bounded, already-loaded census; no lookup, ticket or chunk load is performed here. */
    static final class Observation {
        private final boolean loaded;
        private final int sourceCount;
        private final int destinationCount;
        private final CompoundTag source;

        Observation(boolean loaded, List<CompoundTag> sources, List<CompoundTag> destinations) {
            sourceCount = sources.size(); destinationCount = destinations.size(); this.loaded = loaded;
            EscrowTags.require(sourceCount <= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                    && destinationCount <= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES, "Source census exceeds its bound");
            // Only a unique source can ever receive this action. Do not decode/copy 64 complete snapshots merely to refuse ambiguity.
            if (loaded && sourceCount == 1 && destinationCount == 0) {
                EscrowTags.bound(sources.get(0), EscrowNativeEnvelope.MAX_BYTES, 24, 131_072); source = sources.get(0).copy();
            } else { source = null; }
        }
        CompoundTag source() { return source == null ? null : source.copy(); }
        @Override public boolean equals(Object other) {
            return other instanceof Observation value && loaded == value.loaded && sourceCount == value.sourceCount
                    && destinationCount == value.destinationCount && Objects.equals(source, value.source);
        }
        @Override public int hashCode() { return Objects.hash(loaded, sourceCount, destinationCount, source); }
    }

    public record Change(RocketFlightData flight, Vec3 position) { }

    /** Unforgeable outside this adapter, owner-thread bound, and consumed even when its final check fails. */
    public static final class Permit {
        private final EscrowGovernedDispatch.Authority authority;
        private final Action action;
        private final Supplier<Observation> observations;
        private final Observation before;
        private final BooleanSupplier boundary;
        private final long gameTime;
        private final Change change;
        private boolean used;

        private Permit(EscrowGovernedDispatch.Authority authority, Action action, Supplier<Observation> observations,
                Observation before, BooleanSupplier boundary, long gameTime, Change change) {
            this.authority = authority; this.action = action; this.observations = observations;
            this.before = before; this.boundary = boundary; this.gameTime = gameTime; this.change = change;
        }

        public Change consume(RocketEntity rocket) {
            return consume(() -> {
                EscrowTags.require(rocket.isAlive() && rocket.isAddedToWorld() && !rocket.level().isClientSide
                        && rocket.getServer().isSameThread() && rocket.level().getGameTime() == gameTime,
                        "Source recovery requires its live server-owned tick");
                return EscrowNativeEnvelope.rocket(rocket);
            });
        }

        Change consume(Supplier<CompoundTag> source) {
            EscrowTags.require(!used, "Source recovery permit was already consumed"); used = true;
            EscrowTags.require(authority.valid() && boundary.getAsBoolean(), "Source recovery lost its final boundary");
            var current = observations.get();
            EscrowTags.require(EscrowRecoveryFence.reason(authority, action, current, gameTime) == null
                    && before.equals(current) && before.source.equals(source.get()),
                    "Source recovery lost its complete source or loaded census");
            return change;
        }
    }

    private EscrowGovernedSourceRecovery() { }

    /** The caller must provide its final bay/transaction boundary and repeat the loaded census on consumption. */
    static Permit issue(EscrowGovernedDispatch.Authority authority, Action action, Supplier<Observation> observations,
                        long gameTime, BooleanSupplier boundary) {
        Objects.requireNonNull(action); Objects.requireNonNull(observations); Objects.requireNonNull(boundary);
        EscrowTags.require(authority != null && authority.valid() && boundary.getAsBoolean(), "Unchecked source recovery");
        var observed = observations.get();
        EscrowTags.require(EscrowRecoveryFence.reason(authority, action, observed, gameTime) == null,
                "Source recovery is fenced");
        var source = observed.source; var flight = flight(source);
        var position = source.getList("pos", Tag.TAG_DOUBLE);
        var next = new Change(flight, new Vec3(position.getDouble(0), position.getDouble(1), position.getDouble(2)));
        if (action == Action.RETURN_EXACT_SOURCE) {
            // Transform the observed model, not the journal's historical stationarySource projection.
            var returned = flight.markFailed(gameTime).recover(flight.fuel().availableForLaunch(), gameTime);
            var origin = authority.record().sourceSnapshot().sourceOrigin();
            EscrowTags.require(returned.fuel().equals(flight.fuel()) && returned.passengers().equals(flight.passengers()),
                    "Source return changed resources or assignments");
            next = new Change(returned, new Vec3(origin.x() + 0.5, origin.y(), origin.z() + 0.5));
        }
        return new Permit(authority, action, observations, observed, boundary, gameTime, next);
    }

    static String reason(EscrowGovernedDispatch.Authority authority, Action action, Observation observation, long gameTime) {
        if (authority == null || !authority.valid()) { return "GOVERNED_AUTHORITY_UNACKNOWLEDGED"; }
        if (action == null || authority.decision().route() != EscrowGovernedDispatch.Route.ACTIVE) { return "GOVERNED_ACTIVE_ACTION_REQUIRED"; }
        // A destination-authoritative return needs its separate publication/absence proof. Loaded emptiness is not that proof.
        if (authority.record().phase() != RocketTransferPhase.PREPARED) { return "GOVERNED_SOURCE_PHASE_MISMATCH"; }
        if (observation == null || !observation.loaded) { return "WAITING_UNLOADED"; }
        if (observation.sourceCount >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                || observation.destinationCount >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES) { return "MATCH_LIMIT"; }
        if (observation.sourceCount == 0) { return "MISSING_ENDPOINT"; }
        if (observation.sourceCount != 1 || observation.destinationCount != 0) { return "GOVERNED_ENDPOINT_CONFLICT"; }
        try {
            return sourceMatches(authority.record(), observation.source, gameTime) ? null : "GOVERNED_SOURCE_ENVELOPE_MISMATCH";
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
            return "GOVERNED_SOURCE_ENVELOPE_MISMATCH";
        }
    }

    /** Complete E or an allowed source progression; no historical fuel, wrapper, passenger or snapshot replacement. */
    static boolean sourceMatches(RocketTransferRecord record, CompoundTag observed, long gameTime) {
        var expected = record.nativeTransfer().orElseThrow().sourceEnvelope(record);
        var initial = flight(expected); var actual = flight(observed);
        if (gameTime < record.createdAtGameTime() || actual.stateStartedGameTime() > gameTime) { return false; }
        if (expected.equals(observed)) { return true; }
        var planned = EscrowGovernedLaunch.planned(initial, record.sourceFlightData());
        long started = actual.stateStartedGameTime();
        var origin = record.sourceSnapshot().sourceOrigin();
        var pos = observed.getList("pos", Tag.TAG_DOUBLE);
        if (pos.size() != 3 || pos.getDouble(0) != origin.x() + 0.5 || pos.getDouble(2) != origin.z() + 0.5) { return false; }
        RocketFlightData progression;
        long minimum = record.createdAtGameTime();
        if (actual.state() == RocketFlightState.COUNTDOWN) {
            progression = planned.startCountdown(started);
            if (pos.getDouble(1) != origin.y()) { return false; }
        } else if (actual.state() == RocketFlightState.ASCENT) {
            minimum = Math.addExact(minimum, RocketFlightLimits.COUNTDOWN_TICKS);
            progression = planned.startCountdown(started).completeCountdown(started);
            double maximum = origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS
                    * Math.min(1.0, (double) (gameTime - started) / RocketFlightLimits.ASCENT_TICKS);
            double y = pos.getDouble(1);
            if (!Double.isFinite(y) || y < origin.y() || y > maximum) { return false; }
        } else if (actual.state() == RocketFlightState.TRANSIT) {
            minimum = Math.addExact(minimum, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
            progression = planned.startCountdown(started).completeCountdown(started).beginTransit(record.transferId(), started);
            if (pos.getDouble(1) != origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS) { return false; }
        } else { return false; }
        if (started < minimum) { return false; }
        expected.put("flight", RocketFlightNbtCodec.encode(progression)); expected.put("pos", pos.copy());
        return expected.equals(observed);
    }

    private static RocketFlightData flight(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(EscrowTags.compound(envelope, "flight")).data()
                .orElseThrow(() -> new IllegalArgumentException("Invalid complete source flight"));
    }
}
