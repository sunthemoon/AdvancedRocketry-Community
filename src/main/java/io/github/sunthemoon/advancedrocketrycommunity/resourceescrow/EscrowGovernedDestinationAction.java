package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** F4's one-use initialization authority. It neither debits the source nor permits a handoff. */
public final class EscrowGovernedDestinationAction {
    private EscrowGovernedDestinationAction() { }

    static boolean sourceDue(RocketTransferRecord record, CompoundTag source, long phaseTime) {
        if (!EscrowGovernedSourceRecovery.sourceMatches(record, source, phaseTime)) { return false; }
        var flight = RocketFlightNbtCodec.decode(source.getCompound("flight")).data().orElseThrow();
        return flight.state() == RocketFlightState.TRANSIT
                && phaseTime - flight.stateStartedGameTime() >= RocketFlightLimits.TRANSIT_TICKS;
    }

    public static final class Permit {
        private final EscrowGovernedDispatch.Authority authority;
        private final CompoundTag source;
        private final Supplier<CompoundTag> observed;
        private final BooleanSupplier boundary;
        private final BooleanSupplier proofs;
        private final long tick;
        private boolean used;

        private Permit(EscrowGovernedDispatch.Authority authority, CompoundTag source, Supplier<CompoundTag> observed,
                       BooleanSupplier boundary, BooleanSupplier proofs, long tick) {
            this.authority = authority; this.source = source.copy(); this.observed = observed;
            this.boundary = boundary; this.proofs = proofs; this.tick = tick;
        }

        public RocketTransferRecord consume(RocketEntity destination) {
            return consume(() -> !destination.level().isClientSide && destination.getServer().isSameThread()
                    && destination.level().getGameTime() == tick && !destination.isAddedToWorld()
                    && destination.snapshot().isEmpty() && destination.flightData().isEmpty()
                    && destination.level().dimension().location().equals(authority.record().destinationSnapshot().sourceDimension()));
        }

        RocketTransferRecord consume(BooleanSupplier target) {
            EscrowTags.require(!used, "Destination permit already consumed"); used = true;
            EscrowTags.require(target.getAsBoolean() && authority.valid() && boundary.getAsBoolean()
                    && proofs.getAsBoolean() && source.equals(observed.get()), "Destination publication lost its final proof");
            return authority.record();
        }
    }

    static Permit issue(EscrowGovernedDispatch.Authority authority, CompoundTag source, long phaseTime, long tick,
                        Supplier<CompoundTag> observed, BooleanSupplier boundary, BooleanSupplier proofs) {
        EscrowTags.require(authority != null && authority.valid() && authority.decision().route() == EscrowGovernedDispatch.Route.ACTIVE
                && authority.record().phase() == RocketTransferPhase.DESTINATION_SPAWNED
                && boundary.getAsBoolean() && proofs.getAsBoolean() && tick >= 0
                && EscrowGovernedSourceRecovery.sourceMatches(authority.record(), source, phaseTime),
                "Destination lacks checked F4 and fresh absence proofs");
        return new Permit(authority, source, observed, boundary, proofs, tick);
    }
}
