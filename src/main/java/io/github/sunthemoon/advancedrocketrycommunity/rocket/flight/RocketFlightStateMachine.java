package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** The only legal v0.6 flight-state transition table. */
public final class RocketFlightStateMachine {
    private static final Map<RocketFlightState, Map<RocketFlightEvent, RocketFlightState>> TRANSITIONS =
            transitions();

    private RocketFlightStateMachine() {
    }

    public static RocketFlightTransition apply(
            RocketFlightState state,
            RocketFlightEvent event
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(event, "event");
        RocketFlightState next = TRANSITIONS.getOrDefault(state, Map.of()).get(event);
        return next == null
                ? new RocketFlightTransition(state, event, state, false)
                : new RocketFlightTransition(state, event, next, true);
    }

    public static boolean isLegal(RocketFlightState state, RocketFlightEvent event) {
        return apply(state, event).applied();
    }

    private static Map<RocketFlightState, Map<RocketFlightEvent, RocketFlightState>> transitions() {
        EnumMap<RocketFlightState, Map<RocketFlightEvent, RocketFlightState>> table =
                new EnumMap<>(RocketFlightState.class);
        add(table, RocketFlightState.ASSEMBLED, RocketFlightEvent.FUEL_AVAILABLE, RocketFlightState.FUELED);
        add(table, RocketFlightState.FUELED, RocketFlightEvent.FUEL_AVAILABLE, RocketFlightState.FUELED);
        add(table, RocketFlightState.LANDED, RocketFlightEvent.FUEL_AVAILABLE, RocketFlightState.FUELED);
        add(table, RocketFlightState.FUELED, RocketFlightEvent.START_COUNTDOWN, RocketFlightState.COUNTDOWN);
        add(table, RocketFlightState.COUNTDOWN, RocketFlightEvent.CANCEL_COUNTDOWN, RocketFlightState.FUELED);
        add(table, RocketFlightState.COUNTDOWN, RocketFlightEvent.COUNTDOWN_COMPLETE, RocketFlightState.ASCENT);
        add(table, RocketFlightState.ASCENT, RocketFlightEvent.ASCENT_COMPLETE, RocketFlightState.TRANSIT);
        add(
                table,
                RocketFlightState.TRANSIT,
                RocketFlightEvent.DESTINATION_AUTHORITY_ACQUIRED,
                RocketFlightState.DESCENT
        );
        add(table, RocketFlightState.DESCENT, RocketFlightEvent.LANDING_COMPLETE, RocketFlightState.LANDED);
        add(
                table,
                RocketFlightState.FAILED_RECOVERABLE,
                RocketFlightEvent.RECOVER_ASSEMBLED,
                RocketFlightState.ASSEMBLED
        );
        add(
                table,
                RocketFlightState.FAILED_RECOVERABLE,
                RocketFlightEvent.RECOVER_FUELED,
                RocketFlightState.FUELED
        );
        for (RocketFlightState state : RocketFlightState.values()) {
            if (state != RocketFlightState.DISASSEMBLED
                    && state != RocketFlightState.FAILED_RECOVERABLE) {
                add(table, state, RocketFlightEvent.MARK_FAILED, RocketFlightState.FAILED_RECOVERABLE);
            }
        }
        for (RocketFlightState state : new RocketFlightState[]{
                RocketFlightState.ASSEMBLED,
                RocketFlightState.FUELED,
                RocketFlightState.LANDED
        }) {
            add(table, state, RocketFlightEvent.DISASSEMBLE, RocketFlightState.DISASSEMBLED);
        }
        EnumMap<RocketFlightState, Map<RocketFlightEvent, RocketFlightState>> immutable =
                new EnumMap<>(RocketFlightState.class);
        table.forEach((state, row) -> immutable.put(state, Map.copyOf(row)));
        return Map.copyOf(immutable);
    }

    private static void add(
            EnumMap<RocketFlightState, Map<RocketFlightEvent, RocketFlightState>> table,
            RocketFlightState state,
            RocketFlightEvent event,
            RocketFlightState next
    ) {
        @SuppressWarnings("unchecked")
        EnumMap<RocketFlightEvent, RocketFlightState> row = (EnumMap<RocketFlightEvent, RocketFlightState>)
                table.computeIfAbsent(state, ignored -> new EnumMap<>(RocketFlightEvent.class));
        if (row.put(event, next) != null) {
            throw new IllegalStateException("Duplicate rocket flight transition");
        }
    }
}
