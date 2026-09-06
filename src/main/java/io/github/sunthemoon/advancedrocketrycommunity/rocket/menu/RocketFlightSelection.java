package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.UUID;

/** Menu-local editable typed choice, independent of the server's active flight plan. */
public final class RocketFlightSelection {
    private final List<StationDestinationSummary> stations;
    private TravelTarget selectedTarget;
    private int stationIndex;
    private boolean initialized;

    public RocketFlightSelection(List<StationDestinationSummary> stations) {
        this.stations = List.copyOf(stations);
    }

    public void initialize(RocketDestination planned, RocketDestination current, UUID plannedStation) {
        if (planned == null && current == RocketDestination.SPACE_STATION && !initialized) {
            initialized = true;
            selectedTarget = LegacyTravelTargetAdapter.fromLegacy(RocketDestination.EARTH, null);
            return;
        }
        initialize(
                planned == null ? null : LegacyTravelTargetAdapter.fromLegacy(
                        planned,
                        planned == RocketDestination.SPACE_STATION ? plannedStation : null
                ),
                current == null ? null : legacyCurrent(current)
        );
    }

    public void initialize(TravelTarget planned, TravelTarget current) {
        if (initialized) {
            return;
        }
        initialized = planned != null || current != null;
        selectedTarget = planned != null ? planned : defaultDestination(current);
        stationIndex = 0;
        UUID plannedStation = planned instanceof TravelTarget.Station station ? station.instanceId() : null;
        for (int index = 0; index < stations.size(); index++) {
            if (stations.get(index).stationId().equals(plannedStation)) {
                stationIndex = index;
                break;
            }
        }
    }

    public void select(RocketDestination destination) {
        if (destination == RocketDestination.SPACE_STATION) {
            selectNextStation();
            return;
        }
        selectedTarget = LegacyTravelTargetAdapter.fromLegacy(destination, null);
    }

    public void select(TravelTarget target) {
        selectedTarget = target;
    }

    public void selectNextStation() {
        if (stations.isEmpty()) {
            return;
        }
        if (selectedTarget instanceof TravelTarget.Station) {
            stationIndex = (stationIndex + 1) % stations.size();
        }
        selectedTarget = new TravelTarget.Station(stations.get(stationIndex).stationId());
    }

    public RocketDestination selected() {
        return selectedTarget == null
                ? null
                : LegacyTravelTargetAdapter.toLegacy(selectedTarget)
                .map(LegacyTravelTargetAdapter.LegacyDestination::destination)
                .orElse(null);
    }

    public TravelTarget selectedTarget() {
        return selectedTarget;
    }

    public RocketDestination displayedDestination(RocketFlightState state, RocketFlightPlanSnapshot activePlan) {
        TravelTarget displayed = displayedTarget(state, activePlan);
        return displayed == null
                ? null
                : LegacyTravelTargetAdapter.toLegacy(displayed)
                .map(LegacyTravelTargetAdapter.LegacyDestination::destination)
                .orElse(null);
    }

    public TravelTarget displayedTarget(RocketFlightState state, RocketFlightPlanSnapshot activePlan) {
        return state == RocketFlightState.COUNTDOWN ? activePlan.target() : selectedTarget;
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
        TravelTarget target = selectedTarget != null ? selectedTarget : activePlan.target();
        return target == null ? RocketFlightPlanSnapshot.empty() : new RocketFlightPlanSnapshot(target);
    }

    private static TravelTarget legacyCurrent(RocketDestination current) {
        if (current == RocketDestination.SPACE_STATION) {
            return null;
        }
        return LegacyTravelTargetAdapter.fromLegacy(current, null);
    }

    private static TravelTarget defaultDestination(TravelTarget current) {
        if (current == null) {
            return null;
        }
        return LegacyTravelTargetAdapter.toLegacy(current)
                .map(LegacyTravelTargetAdapter.LegacyDestination::destination)
                .map(destination -> destination == RocketDestination.EARTH
                        ? LegacyTravelTargetAdapter.fromLegacy(RocketDestination.MOON, null)
                        : LegacyTravelTargetAdapter.fromLegacy(RocketDestination.EARTH, null))
                .orElse(null);
    }
}
