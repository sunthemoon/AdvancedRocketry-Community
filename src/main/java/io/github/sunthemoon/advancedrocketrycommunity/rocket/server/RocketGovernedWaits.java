package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * W90 (Root decision R21 item 6): bounded RAM diagnostics of the governed dispatcher, one entry per visited journal
 * record (at most 64), dropped when the record leaves the journal and on restart. A post-F2 readiness wait writes one
 * INFO line after 400 ticks continuously not ready and one when the ends are ready again. Executor holds, the HOLD and
 * CLEANUP routes and bay claims are excluded: they keep their own logs, and a visit gap longer than
 * {@link #MAX_VISIT_GAP_TICKS} (a blocked journal) restarts an unreported wait. Never adds authority.
 */
final class RocketGovernedWaits {
    static final int WAIT_LOG_TICKS = 400;
    static final int MAX_VISIT_GAP_TICKS = 64;
    enum Wait { NONE, LOADING, CENSUS }

    private final Map<UUID, State> states = new HashMap<>();
    private final Consumer<String> info;

    RocketGovernedWaits() { this(message -> AdvancedRocketryCommunity.LOGGER.info(message)); }
    RocketGovernedWaits(Consumer<String> info) { this.info = info; }

    void retain(List<RocketTransferRecord> records) {
        var present = records.stream().map(RocketTransferRecord::transferId).collect(Collectors.toSet());
        states.keySet().retainAll(present);
    }

    /** One checked ACTIVE, unheld visit. {@code margins} is read only when a WAITING line is written. */
    void observe(RocketTransferRecord record, long tick, Wait wait, Supplier<String> margins) {
        var state = state(record.transferId());
        if (state.since >= 0 && !state.logged && tick - state.lastVisit > MAX_VISIT_GAP_TICKS) { state.since = -1; }
        state.lastVisit = tick;
        if (wait == Wait.NONE) {
            if (state.logged) {
                info.accept("ARCE_GOVERNED_ENDS_READY transfer=" + record.transferId() + " waited=" + (tick - state.since));
            }
            state.reset(); return;
        }
        if (state.since < 0) { state.since = tick; }
        state.wait = wait;
        if (!state.logged && tick - state.since >= WAIT_LOG_TICKS) {
            state.logged = true;
            info.accept("ARCE_GOVERNED_ENDS_WAITING transfer=" + record.transferId() + " logical=" + record.logicalRocketId()
                    + " phase=" + record.phase() + " cause=" + wait + " " + margins.get());
        }
    }

    /** A held, non-ACTIVE or bay-claimed visit: no wait and no refreshed tickets. */
    void excluded(UUID transfer) {
        var state = states.get(transfer);
        if (state != null) { state.reset(); state.tickets = 0; }
    }

    void tickets(UUID transfer, int count) {
        if (count < 0 || count > 2 * RocketFlightLimits.MAX_LANDING_CHUNKS) { throw new IllegalArgumentException("Ticket count outside its bound"); }
        state(transfer).tickets = count;
    }

    int tickets(UUID transfer) { var state = states.get(transfer); return state == null ? 0 : state.tickets; }
    Wait wait(UUID transfer) { var state = states.get(transfer); return state == null ? Wait.NONE : state.wait; }
    int size() { return states.size(); }
    void clear() { states.clear(); }

    private State state(UUID transfer) {
        var state = states.get(transfer);
        if (state != null) { return state; }
        state = new State();
        // Diagnostics never fail a tick; past the transfer bound an entry is simply not retained.
        if (states.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { states.put(transfer, state); }
        return state;
    }

    private static final class State {
        long since = -1, lastVisit = -1;
        boolean logged;
        Wait wait = Wait.NONE;
        int tickets;
        void reset() { since = -1; logged = false; wait = Wait.NONE; }
    }
}
