package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import java.util.List;
import java.util.UUID;

/** Menu-local editable choice, independent of the server's active flight plan. */
public final class RocketFlightSelection {
    private final List<StationDestinationSummary> stations;
    private RocketDestination selected;
    private int stationIndex;
    private boolean initialized;

    public RocketFlightSelection(List<StationDestinationSummary> stations) {
        this.stations = List.copyOf(stations);
    }

    public void initialize(RocketDestination planned, RocketDestination current, UUID plannedStation) {
        if (initialized) {
            return;
        }
        initialized = planned != null || current != null;
        selected = planned != null ? planned : current == null ? null : current.opposite();
        stationIndex = 0;
        for (int index = 0; index < stations.size(); index++) {
            if (stations.get(index).stationId().equals(plannedStation)) {
                stationIndex = index;
                break;
            }
        }
    }

    public void select(RocketDestination destination) {
        selected = destination;
    }

    public void selectNextStation() {
        if (stations.isEmpty()) {
            return;
        }
        if (selected == RocketDestination.SPACE_STATION) {
            stationIndex = (stationIndex + 1) % stations.size();
        }
        selected = RocketDestination.SPACE_STATION;
    }

    public RocketDestination selected() {
        return selected;
    }

    public RocketDestination displayedDestination(RocketFlightState state, RocketFlightPlanSnapshot activePlan) {
        return state == RocketFlightState.COUNTDOWN ? activePlan.destination() : selected;
    }

    public int stationIndex() {
        return stationIndex;
    }

    public UUID stationId() {
        return stations.isEmpty() ? null : stations.get(stationIndex).stationId();
    }

    public RocketFlightPlanSnapshot target(RocketFlightAction action, RocketFlightPlanSnapshot activePlan) {
        if (action == RocketFlightAction.CANCEL) {
            return activePlan;
        }
        RocketDestination destination = selected != null ? selected : activePlan.destination();
        if (destination == RocketDestination.SPACE_STATION && stationId() == null) {
            return RocketFlightPlanSnapshot.empty();
        }
        return new RocketFlightPlanSnapshot(destination,
                destination == RocketDestination.SPACE_STATION ? stationId() : null);
    }
}
