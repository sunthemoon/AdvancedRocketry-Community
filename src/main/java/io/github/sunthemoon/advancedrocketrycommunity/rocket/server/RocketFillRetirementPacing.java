package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * W130F (Root decision R59; W130-R1, R3 and R4): the BEFORE_FILL site's demand pacing, kept apart from W98's proof state
 * machine in {@link RocketOrdinaryRetirement}. RAM only and cleared with the coordinator, so nothing starts at startup.
 *
 * <ul>
 *   <li>A loader's fill attempt only queues a demand. The coordinator's tick begins at most
 *       {@link RocketOrdinaryRetirement#MAX_ATTEMPTS} queued demands, never the loader's fair-queue visit (W130-R4).</li>
 *   <li>R52: at most one attempt per rocket per {@link RocketOrdinaryRetirement#RETRY_TICKS}, kept through an abandoned or
 *       recreated entry, and at most {@link RocketOrdinaryRetirement#MAX_ATTEMPTS} starts per tick.</li>
 *   <li>R59: an attempt checks the holds that no ticket can end before its ticket step, and one demand allows at most
 *       {@link #DEMAND_TICKETS} ticket steps; the coordinator then ends the entry with T kept (W130-R1).</li>
 * </ul>
 */
final class RocketFillRetirementPacing {
    /** Ticket steps one demand allows: a cold old site needs two (load, then prove), and the third absorbs one failed proof. */
    static final int DEMAND_TICKETS = 3;
    /** W199: one cold I6 pair (at most 16 columns per end) enters chunk scheduling per server tick. */
    static final int COLD_PREPARATIONS_PER_TICK = 1;
    private final Map<UUID, UUID> demands = new LinkedHashMap<>();
    /** The tick of each rocket's last attempt, within the existing pending bound. */
    private final Map<UUID, Long> attempts = new LinkedHashMap<>();
    private final Set<String> warned = new HashSet<>();
    private long tick = Long.MIN_VALUE;
    private int starts, coldStarts;

    /** Queues a loader's demand for L's landed rocket; a demand already queued, or a full queue, adds nothing. */
    void demand(UUID logical, UUID survivor) {
        if (demands.size() < RocketOrdinaryRetirement.MAX_PENDING) { demands.putIfAbsent(logical, survivor); }
    }

    /** The coordinator's tick: hands at most {@code max} queued demands, oldest first, to {@code begin}. */
    void drain(int max, BiConsumer<UUID, UUID> begin) {
        var taken = new ArrayList<Map.Entry<UUID, UUID>>();
        for (var demand : demands.entrySet()) {
            if (taken.size() == max) { break; }
            taken.add(Map.entry(demand.getKey(), demand.getValue()));
        }
        taken.forEach(demand -> demands.remove(demand.getKey()));
        taken.forEach(demand -> begin.accept(demand.getKey(), demand.getValue()));
    }

    /**
     * R52: {@code now} when L may start an attempt now, which is then counted; otherwise the tick to look again. At most
     * one attempt per rocket per retry interval, at most MAX_ATTEMPTS starts per tick, and none while {@code busy}.
     */
    long admit(UUID logical, long now, boolean busy) {
        return admit(logical, now, busy, false);
    }

    long admit(UUID logical, long now, boolean busy, boolean coldPreparation) {
        attempts.entrySet().removeIf(attempt -> now - attempt.getValue() >= RocketOrdinaryRetirement.RETRY_TICKS);
        var last = attempts.get(logical);
        if (last != null) { return last + RocketOrdinaryRetirement.RETRY_TICKS; }
        if (tick != now) { tick = now; starts = 0; coldStarts = 0; }
        if (starts >= RocketOrdinaryRetirement.MAX_ATTEMPTS || busy || attempts.size() >= RocketOrdinaryRetirement.MAX_PENDING
                || coldPreparation && coldStarts >= COLD_PREPARATIONS_PER_TICK) {
            return now + 1;
        }
        starts++;
        if (coldPreparation) { coldStarts++; }
        attempts.put(logical, now);
        return now;
    }

    /**
     * An admitted attempt's checks, then its ticket step: R124's existing ordinary tickets on both recorded checked
     * discovery sets. Returns the hold reason, or null once the tickets are placed. The first three holds never
     * end by themselves and are read without loading anything (R59): either end's discovery region over its limit, a
     * census Level with saving off, and entity storage that gives no own-store proof (D2-4). Each WARNs once per Level.
     */
    String ticket(RocketOrdinaryRetirement.Port port, RocketTransferSavedData journal, RocketTransferRecord record, UUID survivor,
                  List<RocketRetirementProof.Slot> census, RocketTransferSavedData.Reservation token) {
        if (!RocketDiscoveryColumns.of(record.sourceSnapshot()).admissible()
                || !RocketDiscoveryColumns.of(record.destinationSnapshot()).admissible()) { return "DISCOVERY_BOUNDS"; }
        for (var slot : census) {
            if (!port.saveOff(slot)) { warned.remove("SAVE_OFF " + slot.dimension()); continue; }
            if (warned.add("SAVE_OFF " + slot.dimension())) {
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_FILL_RETIREMENT_SAVE_OFF level={}; escrow fills retain the transfer until saving resumes",
                        slot.dimension());
            }
            return "SAVE_OFF";
        }
        for (var slot : census) {
            if (port.captureSupported(slot)) { continue; }
            if (warned.add("STORAGE " + slot.dimension())) {
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_FILL_RETIREMENT_UNSUPPORTED_STORAGE level={}; escrow fills retain the transfer until entity storage supports a checked proof", slot.dimension());
            }
            return "UNSUPPORTED_ENTITY_STORAGE";
        }
        if (!port.escrowAllows(journal, record, token)) { return "BUSY"; }
        if (!port.ticketReady(record, survivor)) { return "SURVIVOR_CONFLICT"; }
        port.keepLoaded(record, census);
        return null;
    }

    void clear() {
        demands.clear(); attempts.clear(); warned.clear();
        tick = Long.MIN_VALUE; starts = 0; coldStarts = 0;
    }
}
