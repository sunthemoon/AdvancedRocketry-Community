package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** Final complete-envelope landing check, also used after deferred world-first acknowledgement. */
final class EscrowGovernedLandingAction {
    private EscrowGovernedLandingAction() { }

    static Permit issue(EscrowGovernedDispatch.Authority authority, CompoundTag before, long now, BooleanSupplier boundary) {
        EscrowTags.require(authority != null && authority.valid() && authority.decision().route() == EscrowGovernedDispatch.Route.ACTIVE
                && boundary.getAsBoolean() && EscrowGovernedArrival.ready(authority.record(), before, now),
                "Landing lacks its exact checked terminal pose");
        return new Permit(authority, before, now, boundary);
    }

    static final class Permit {
        private final EscrowGovernedDispatch.Authority authority;
        private final CompoundTag before;
        private final long now;
        private final BooleanSupplier boundary;
        private boolean used;
        private Permit(EscrowGovernedDispatch.Authority authority, CompoundTag before, long now, BooleanSupplier boundary) {
            this.authority = authority; this.before = before.copy(); this.now = now; this.boundary = boundary;
        }
        RocketFlightData consume(Supplier<CompoundTag> observed) {
            EscrowTags.require(!used, "Landing permit already consumed"); used = true;
            EscrowTags.require(authority.valid() && boundary.getAsBoolean() && before.equals(observed.get()),
                    "Landing callback lost its authority or complete destination");
            return EscrowGovernedArrivalState.flight(before).land(now);
        }
    }
}
