package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;

/** Checked whole-vector quotation after the existing route/thrust/authority checks. */
public final class RocketPropulsionQuoter {
    private RocketPropulsionQuoter() { }
    public static RocketFlightPlanResult quote(RocketFuelState fuel, RocketFlightPlan plan, RocketFluidFuelCatalog catalog) {
        long primary = plan.requiredFuel(), oxidizer = 0;
        var nativeFuel = fuel.propulsion().orElse(null);
        try {
            if (nativeFuel != null) {
                var descriptor = nativeFuel.descriptor();
                if (!descriptor.settings().rocketsRequireFuel()) { primary = 0;
                } else {
                    if (nativeFuel.primary().branch() == RocketFuelBank.Branch.EMPTY) {
                        return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_FUEL, primary);
                    }
                    if (nativeFuel.primary().branch() == RocketFuelBank.Branch.FLUID) {
                        primary = cost(plan.requiredFuel(), descriptor, descriptor.primaryRole(), nativeFuel.primary(), catalog);
                    }
                    if (descriptor.mode() == RocketPropulsion.Mode.BIPROPELLANT) {
                        if (nativeFuel.oxidizer().branch() != RocketFuelBank.Branch.FLUID) {
                            return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_FUEL, primary);
                        }
                        oxidizer = cost(plan.requiredFuel(), descriptor, RocketPropulsion.Role.OXIDIZER, nativeFuel.oxidizer(), catalog);
                    }
                }
            }
            if (primary > RocketFlightLimits.MAX_TRAVEL_FUEL || oxidizer > RocketFlightLimits.MAX_TRAVEL_FUEL) {
                return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_CAPACITY, 0);
            }
            if (primary > fuel.capacity() || nativeFuel != null && oxidizer > nativeFuel.descriptor().oxidizerCapacity()) {
                return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_CAPACITY, primary);
            }
            if (primary > fuel.amount() || nativeFuel != null && oxidizer > nativeFuel.oxidizer().amount()) {
                return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_FUEL, primary);
            }
            if (nativeFuel != null) {
                plan = plan.withPropulsionDebit(new RocketPropulsionDebit(primary, oxidizer, nativeFuel.revision(),
                        fuel.resourceSignature(), catalog.signature()));
            }
            return new RocketFlightPlanResult(RocketFlightPlanCode.SUCCESS, plan.requiredFuel(), plan);
        } catch (ArithmeticException overflow) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.ARITHMETIC_OVERFLOW, 0);
        } catch (IllegalArgumentException invalid) {
            return RocketFlightPlanResult.failure(RocketFlightPlanCode.INSUFFICIENT_FUEL, 0);
        }
    }
    private static long cost(long q, RocketPropulsion descriptor, RocketPropulsion.Role role, RocketFuelBank bank,
            RocketFluidFuelCatalog catalog) {
        var efficiency = catalog.find(role, bank.fluidId());
        if (efficiency == null) { throw new IllegalArgumentException("Unavailable role/Fluid definition"); }
        return efficiency.debit(q, descriptor.coefficientNumerator(), descriptor.coefficientDenominator());
    }
}
