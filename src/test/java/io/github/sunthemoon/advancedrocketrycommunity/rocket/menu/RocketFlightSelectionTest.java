package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketFlightSelectionTest {
    private static final UUID FIRST = new UUID(1, 1);
    private static final UUID SECOND = new UUID(2, 2);

    @Test
    void cancellationUsesTheActiveBodyRatherThanTheEditedChoice() {
        RocketFlightSelection selection = selection();
        selection.initialize(RocketDestination.MOON, RocketDestination.EARTH, null);
        selection.selectNextStation();
        var active = new RocketFlightPlanSnapshot(RocketDestination.MOON, null);
        assertEquals(active, selection.target(RocketFlightAction.CANCEL, active));
    }

    @Test
    void cancellationUsesAnotherPlayersStationNotTheOpeningOrSelectedStation() {
        RocketFlightSelection selection = selection();
        selection.initialize(RocketDestination.SPACE_STATION, RocketDestination.EARTH, FIRST);
        var active = new RocketFlightPlanSnapshot(RocketDestination.SPACE_STATION, SECOND);
        assertEquals(active, selection.target(RocketFlightAction.CANCEL, active));
    }

    @Test
    void resizingDoesNotResetAChosenStationToTheOpeningPayload() {
        RocketFlightSelection selection = selection();
        selection.initialize(RocketDestination.MOON, RocketDestination.EARTH, FIRST);
        selection.selectNextStation();
        selection.selectNextStation();
        selection.initialize(RocketDestination.SPACE_STATION, RocketDestination.EARTH, FIRST);
        assertEquals(SECOND, selection.stationId());
        assertEquals(RocketDestination.SPACE_STATION, selection.selected());
    }

    @Test
    void cancellationWaitsForAnActivePlanInsteadOfInventingOne() {
        RocketFlightSelection selection = selection();
        selection.initialize(RocketDestination.MOON, RocketDestination.EARTH, null);
        assertEquals(RocketFlightPlanSnapshot.empty(),
                selection.target(RocketFlightAction.CANCEL, RocketFlightPlanSnapshot.empty()));
    }

    @Test
    void launchStillUsesTheEditedChoiceNotAnOldPlan() {
        RocketFlightSelection selection = selection();
        selection.initialize(RocketDestination.MOON, RocketDestination.EARTH, null);
        selection.selectNextStation();
        assertEquals(new RocketFlightPlanSnapshot(RocketDestination.SPACE_STATION, FIRST),
                selection.target(RocketFlightAction.LAUNCH,
                        new RocketFlightPlanSnapshot(RocketDestination.MOON, null)));
    }

    @Test
    void stationCancellationDoesNotRequireTheTargetInTheOpeningAccessibleList() {
        RocketFlightSelection selection = new RocketFlightSelection(List.of());
        var active = new RocketFlightPlanSnapshot(RocketDestination.SPACE_STATION, SECOND);
        assertEquals(active, selection.target(RocketFlightAction.CANCEL, active));
    }

    private static RocketFlightSelection selection() {
        return new RocketFlightSelection(List.of(
                new StationDestinationSummary(FIRST, "First"),
                new StationDestinationSummary(SECOND, "Second")
        ));
    }

    @Test
    void countdownBodyMarkersIgnoreTheEditableStationChoice() {
        for (var body : List.of(RocketDestination.EARTH, RocketDestination.MOON)) {
            var selection = selection();
            selection.initialize(RocketDestination.SPACE_STATION, RocketDestination.MOON, FIRST);
            assertEquals(body, selection.displayedDestination(RocketFlightState.COUNTDOWN,
                    new RocketFlightPlanSnapshot(body, null)));
            assertEquals(RocketDestination.SPACE_STATION, selection.selected());
            assertEquals(FIRST, selection.stationId());
        }
    }

    @Test
    void countdownStationMarkerDoesNotOverwriteTheEditableBodyOrStation() {
        var selection = selection();
        selection.initialize(RocketDestination.MOON, RocketDestination.EARTH, FIRST);
        var active = new RocketFlightPlanSnapshot(RocketDestination.SPACE_STATION, SECOND);
        assertEquals(RocketDestination.SPACE_STATION,
                selection.displayedDestination(RocketFlightState.COUNTDOWN, active));
        assertEquals(RocketDestination.MOON, selection.displayedDestination(RocketFlightState.FUELED,
                RocketFlightPlanSnapshot.empty()));
        assertEquals(FIRST, selection.stationId());
    }

    @Test
    void missingCountdownPlanDoesNotHighlightAnEditableDestination() {
        var selection = selection();
        selection.initialize(RocketDestination.MOON, RocketDestination.EARTH, FIRST);
        assertNull(selection.displayedDestination(RocketFlightState.COUNTDOWN,
                RocketFlightPlanSnapshot.empty()));
    }
}
