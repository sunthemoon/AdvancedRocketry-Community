package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** F5 changes physical riders and removes only its exact source, never resource/manifest data. */
public final class EscrowGovernedPhaseAction {
    private EscrowGovernedPhaseAction() { }

    static boolean sourceOptional(RocketTransferRecord record) {
        return record.phase() == RocketTransferPhase.SOURCE_REMOVED || record.phase() == RocketTransferPhase.COMMITTED;
    }

    /** Normalize the validated physical rider observation for comparison only. No entity or journal is rewritten. */
    static CompoundTag sourceComparison(RocketTransferRecord record, CompoundTag observed) {
        riders(record, observed);
        return EscrowGovernedEnvelope.comparison(observed);
    }

    static boolean sourceMatches(RocketTransferRecord record, CompoundTag source, long levelTime) {
        if (source == null) { return sourceOptional(record); }
        var comparison = sourceComparison(record, source);
        var clock = EscrowGovernedSourceClock.resume(record, comparison, levelTime);
        return EscrowGovernedSourceRecovery.sourceMatches(record, comparison, clock.at(levelTime));
    }

    static boolean destinationMatches(RocketTransferRecord record, CompoundTag destination) {
        if (destination == null) { return false; }
        riders(record, destination);
        if (record.phase() == RocketTransferPhase.COMMITTED) {
            try {
                long now = EscrowGovernedArrivalState.resumeTime(record, destination, 0);
                return EscrowGovernedArrivalState.matches(record, destination, now);
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) { return false; }
        }
        var comparison = destination.copy();
        comparison.put("passengers", new ListTag());
        return comparison.equals(record.nativeTransfer().orElseThrow().destinationEnvelope(record));
    }

    static Set<UUID> riders(RocketTransferRecord record, CompoundTag envelope) {
        var result = new HashSet<UUID>();
        for (var raw : EscrowTags.list(envelope, "passengers", RocketFlightLimits.MAX_PASSENGERS)) {
            var rider = (CompoundTag) raw;
            EscrowTags.require(rider.getAllKeys().equals(Set.of("physical")) && rider.hasUUID("physical"), "Invalid physical rider");
            var id = rider.getUUID("physical");
            EscrowTags.require(record.sourceFlightData().passengers().assignment(id).isPresent() && result.add(id),
                    "Unexpected or duplicate physical rider");
        }
        return Set.copyOf(result);
    }

    static boolean matches(RocketTransferRecord record, CompoundTag source, CompoundTag destination, long levelTime) {
        if (!sourceMatches(record, source, levelTime) || !destinationMatches(record, destination)) { return false; }
        return source == null || java.util.Collections.disjoint(riders(record, source), riders(record, destination));
    }

    static CompoundTag withoutRider(CompoundTag envelope, UUID passenger) {
        if (envelope == null) { return null; }
        var result = envelope.copy(); var riders = result.getList("passengers", Tag.TAG_COMPOUND);
        riders.removeIf(raw -> passenger.equals(((CompoundTag) raw).getUUID("physical"))); return result;
    }

    /** Complete comparisons at issue and consumption; the supplier repeats the loaded-only census. */
    static Permit issue(EscrowGovernedDispatch.Authority authority, CompoundTag source, CompoundTag destination,
                        long levelTime, Supplier<CompoundTag> observedSource, Supplier<CompoundTag> observedDestination,
                        BooleanSupplier boundary) {
        EscrowTags.require(authority != null && authority.valid() && authority.decision().route() == EscrowGovernedDispatch.Route.ACTIVE
                && authority.record().phase().ordinal() >= RocketTransferPhase.PASSENGERS_TRANSFERRED.ordinal()
                && boundary.getAsBoolean() && matches(authority.record(), source, destination, levelTime), "F5 lacks exact checked endpoints");
        return new Permit(authority, source, destination, observedSource, observedDestination, boundary);
    }

    public static final class Permit {
        private final EscrowGovernedDispatch.Authority authority;
        private final CompoundTag source;
        private final CompoundTag destination;
        private final Supplier<CompoundTag> observedSource;
        private final Supplier<CompoundTag> observedDestination;
        private final BooleanSupplier boundary;
        private boolean used;

        private Permit(EscrowGovernedDispatch.Authority authority, CompoundTag source, CompoundTag destination,
                       Supplier<CompoundTag> observedSource, Supplier<CompoundTag> observedDestination, BooleanSupplier boundary) {
            this.authority = authority; this.source = source == null ? null : source.copy(); this.destination = destination.copy();
            this.observedSource = observedSource; this.observedDestination = observedDestination; this.boundary = boundary;
        }

        void consume() {
            EscrowTags.require(!used, "F5 permit already consumed"); used = true;
            EscrowTags.require(authority.valid() && boundary.getAsBoolean(), "F5 lost its final authority or boundary");
            var liveSource = observedSource.get(); var liveDestination = observedDestination.get();
            EscrowTags.require(EscrowGovernedEnvelope.same(source, liveSource) && EscrowGovernedEnvelope.same(destination, liveDestination)
                    && (liveSource == null || java.util.Collections.disjoint(
                            riders(authority.record(), liveSource), riders(authority.record(), liveDestination))),
                    "F5 lost its final authority or complete endpoint comparison");
        }

        public void remove(RocketEntity rocket) {
            consume();
            EscrowTags.require(sourceOptional(authority.record())
                    && rocket.isAlive() && rocket.isAddedToWorld() && rocket.getServer().isSameThread()
                    && rocket.getUUID().equals(authority.record().sourceEntityId()) && rocket.getPassengers().isEmpty()
                    && source != null && EscrowGovernedEnvelope.same(source, EscrowNativeEnvelope.rocket(rocket)), "Not the authorized empty source");
            rocket.discard();
        }
    }
}
