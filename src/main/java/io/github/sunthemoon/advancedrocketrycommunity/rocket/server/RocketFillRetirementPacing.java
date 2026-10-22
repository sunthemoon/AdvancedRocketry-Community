package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * W130F (Root decision R59; W130-R3): the BEFORE_FILL site's demand pacing, kept apart from W98's proof state machine in
 * {@link RocketOrdinaryRetirement}. RAM only and cleared with the coordinator, so nothing starts at startup.
 *
 * <ul>
 *   <li>R52: at most one attempt per rocket per {@link RocketOrdinaryRetirement#RETRY_TICKS}, kept through an abandoned or
 *       recreated entry, and at most {@link RocketOrdinaryRetirement#MAX_ATTEMPTS} starts per tick.</li>
 * </ul>
 */
final class RocketFillRetirementPacing {
    /** The tick of each rocket's last attempt, within the existing pending bound. */
    private final Map<UUID, Long> attempts = new LinkedHashMap<>();
    private long tick = Long.MIN_VALUE;
    private int starts;

    /**
     * R52: {@code now} when L may start an attempt now, which is then counted; otherwise the tick to look again. At most
     * one attempt per rocket per retry interval, at most MAX_ATTEMPTS starts per tick, and none while {@code busy}.
     */
    long admit(UUID logical, long now, boolean busy) {
        attempts.entrySet().removeIf(attempt -> now - attempt.getValue() >= RocketOrdinaryRetirement.RETRY_TICKS);
        var last = attempts.get(logical);
        if (last != null) { return last + RocketOrdinaryRetirement.RETRY_TICKS; }
        if (tick != now) { tick = now; starts = 0; }
        if (starts >= RocketOrdinaryRetirement.MAX_ATTEMPTS || busy || attempts.size() >= RocketOrdinaryRetirement.MAX_PENDING) {
            return now + 1;
        }
        starts++;
        attempts.put(logical, now);
        return now;
    }

    /**
     * An admitted attempt's checks, then its ticket step: the existing ordinary ticket on the old launch site's origin
     * column, and D's refreshed. Returns the hold reason, or null once the tickets are placed.
     */
    String ticket(RocketOrdinaryRetirement.Port port, RocketTransferSavedData journal, RocketTransferRecord record, UUID survivor,
                  List<RocketRetirementProof.Slot> census, RocketTransferSavedData.Reservation token) {
        if (!port.escrowAllows(journal, record, token)) { return "BUSY"; }
        if (port.projection(record, survivor) == null) { return "SURVIVOR_CONFLICT"; }
        port.keepLoaded(record, census);
        return null;
    }

    void clear() {
        attempts.clear();
        tick = Long.MIN_VALUE; starts = 0;
    }
}
