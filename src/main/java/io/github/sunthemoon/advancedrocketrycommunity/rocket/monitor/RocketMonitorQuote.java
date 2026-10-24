package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;

/** RAM-only quote. The view token never becomes a request ID or a saved flight plan. */
public record RocketMonitorQuote(TravelTarget target, RocketFlightRequestCode code, long cost,
                                 RocketFlightPlan plan, RocketFlightData source, String authority) {
    public boolean ready() { return code == RocketFlightRequestCode.SUCCESS && plan != null; }
    public boolean same(RocketMonitorQuote other) {
        return other != null && Objects.equals(target, other.target) && code == other.code && cost == other.cost
                && Objects.equals(source, other.source) && authority.equals(other.authority)
                && Objects.equals(plan == null ? null : plan.propulsionDebit(), other.plan == null ? null : other.plan.propulsionDebit());
    }
}
