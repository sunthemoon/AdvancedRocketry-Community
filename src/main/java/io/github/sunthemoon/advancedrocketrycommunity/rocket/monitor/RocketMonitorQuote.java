package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketRequiredCosts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;

/** RAM-only quote. The view token never becomes a request ID or a saved flight plan. */
public record RocketMonitorQuote(TravelTarget target, RocketFlightRequestCode code, long cost,
                                 RocketFlightPlan plan, RocketFlightData source, String authority, RocketRequiredCosts requiredCosts) {
    public RocketMonitorQuote {
        Objects.requireNonNull(requiredCosts, "requiredCosts");
        if (cost != requiredCosts.primary()) { throw new IllegalArgumentException("Primary cost disagrees with display vector"); }
    }
    public static RocketMonitorQuote planned(TravelTarget target, RocketFlightPlanResult result, RocketFlightData source, String authority) {
        return new RocketMonitorQuote(target, RocketFlightRequestCode.fromPlanCode(result.code()), result.requiredCosts().primary(),
                result.plan(), source, authority, result.requiredCosts());
    }
    public boolean ready() { return code == RocketFlightRequestCode.SUCCESS && plan != null; }
    public boolean same(RocketMonitorQuote other) {
        return other != null && Objects.equals(target, other.target) && code == other.code && cost == other.cost
                && requiredCosts.equals(other.requiredCosts)
                && Objects.equals(source, other.source) && authority.equals(other.authority)
                && Objects.equals(plan == null ? null : plan.propulsionDebit(), other.plan == null ? null : other.plan.propulsionDebit());
    }
}
