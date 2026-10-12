package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import java.util.Objects;

/** Complete propulsion vector and revision. It deliberately excludes ordinary cargo. */
public record RocketPropulsionFuel(RocketPropulsion descriptor, RocketFuelBank primary,
        RocketFuelBank oxidizer, long revision) {
    public RocketPropulsionFuel {
        Objects.requireNonNull(descriptor); Objects.requireNonNull(primary); Objects.requireNonNull(oxidizer);
        if (revision < 0 || primary.amount() > descriptor.primaryCapacity()
                || oxidizer.amount() > descriptor.oxidizerCapacity()
                || primary.branch() == RocketFuelBank.Branch.ABSTRACT && !descriptor.acceptsAbstract()
                || primary.branch() == RocketFuelBank.Branch.FLUID && descriptor.mode() == RocketPropulsion.Mode.ABSTRACT
                || primary.fluidId() != null && !descriptor.settings().allows(descriptor.primaryRole(), primary.fluidId())
                || oxidizer.fluidId() != null && !descriptor.settings().allows(RocketPropulsion.Role.OXIDIZER, oxidizer.fluidId())
                || oxidizer.branch() == RocketFuelBank.Branch.ABSTRACT) {
            throw new IllegalArgumentException("Propulsion bank role mismatch");
        }
    }
    public static RocketPropulsionFuel empty(RocketPropulsion descriptor) {
        return new RocketPropulsionFuel(descriptor, RocketFuelBank.EMPTY, RocketFuelBank.EMPTY, 0);
    }
    public RocketPropulsionFuel updated(RocketFuelBank primary, RocketFuelBank oxidizer) {
        return new RocketPropulsionFuel(descriptor, primary, oxidizer, Math.incrementExact(revision));
    }
    public long totalAmount() { return primary.amount() + oxidizer.amount(); }
    public String resourceSignature() {
        return RocketNativeResources.hash("propulsion-resources-1:" + descriptor.signature() + ":"
                + primary.signature() + ":" + oxidizer.signature() + ":" + revision);
    }
    public String summary() {
        return primary.summary(descriptor.primaryRole()) + (descriptor.oxidizerCapacity() == 0 ? ""
                : "; " + oxidizer.summary(RocketPropulsion.Role.OXIDIZER)) + "; revision=" + revision;
    }
}
