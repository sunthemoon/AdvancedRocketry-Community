package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameIdOrder;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The {@code elevator_pairs} section of the endgame root (ADR-059 section 1, ADR-054 section 10): at most 1,024 pairs,
 * in pair-ID order, with at most one pair per station, anchor, terminal and anchor column. It only stores; binding and
 * validity are decided by {@link ElevatorRules}. A mutation marks the root changed through the callback the root gives
 * it.
 */
public final class ElevatorPairTable {
    private final Map<UUID, ElevatorPair> pairs = new TreeMap<>(EndgameIdOrder.ORDER);
    private final Map<UUID, UUID> byStation = new HashMap<>();
    private final Map<UUID, UUID> byEndpoint = new HashMap<>();
    private final Map<ElevatorPair.Column, UUID> byColumn = new HashMap<>();
    private final Runnable changed;

    public ElevatorPairTable(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    public Collection<ElevatorPair> pairs() {
        return List.copyOf(pairs.values());
    }

    public int size() {
        return pairs.size();
    }

    public Optional<ElevatorPair> pair(UUID pairId) {
        return Optional.ofNullable(pairs.get(pairId));
    }

    public Optional<ElevatorPair> forStation(UUID stationId) {
        return Optional.ofNullable(byStation.get(stationId)).map(pairs::get);
    }

    /** The pair naming this endpoint as its anchor or terminal. */
    public Optional<ElevatorPair> forEndpoint(UUID endpoint) {
        return Optional.ofNullable(byEndpoint.get(endpoint)).map(pairs::get);
    }

    public Optional<ElevatorPair> forColumn(ElevatorPair.Column column) {
        return Optional.ofNullable(byColumn.get(column)).map(pairs::get);
    }

    /** Whether a pair names this endpoint: its tombstone is pinned and the endpoint is busy (ADR-054 section 9). */
    public boolean names(UUID endpoint) {
        return byEndpoint.containsKey(endpoint);
    }

    public long accountedBytes() {
        return (long) pairs.size() * EndgameLimits.PAIR_RECORD_BYTES;
    }

    /**
     * The first cardinality conflict of a new pair (section 1): {@code STATION_BOUND}, {@code ANCHOR_BOUND},
     * {@code TERMINAL_BOUND}, {@code COLUMN_BOUND}; {@code OK} when it fits.
     */
    public EndgameCode conflict(UUID stationId, UUID anchorId, UUID terminalId, ElevatorPair.Column column) {
        if (byStation.containsKey(stationId)) {
            return EndgameCode.STATION_BOUND;
        }
        if (byEndpoint.containsKey(anchorId)) {
            return EndgameCode.ANCHOR_BOUND;
        }
        if (byEndpoint.containsKey(terminalId)) {
            return EndgameCode.TERMINAL_BOUND;
        }
        return byColumn.containsKey(column) ? EndgameCode.COLUMN_BOUND : EndgameCode.OK;
    }

    /** Adds a pair that {@link #conflict} accepted, within 1,024 pairs. */
    public void add(ElevatorPair pair) {
        restore(pair);
        changed.run();
    }

    /** Strict restore: the cardinality rules and the bound hold for every decoded pair. */
    public void restore(ElevatorPair pair) {
        Objects.requireNonNull(pair, "pair");
        if (pairs.size() >= EndgameLimits.MAX_PAIRS) {
            throw new IllegalArgumentException("The elevator pairs exceed their fixed bound");
        }
        if (pairs.containsKey(pair.pairId()) || conflict(pair.stationId(), pair.anchorId(), pair.terminalId(),
                pair.column()) != EndgameCode.OK) {
            throw new IllegalArgumentException("An elevator pair breaks the one-pair rules");
        }
        pairs.put(pair.pairId(), pair);
        byStation.put(pair.stationId(), pair.pairId());
        byEndpoint.put(pair.anchorId(), pair.pairId());
        byEndpoint.put(pair.terminalId(), pair.pairId());
        byColumn.put(pair.column(), pair.pairId());
    }

    public Optional<ElevatorPair> remove(UUID pairId) {
        ElevatorPair pair = pairs.remove(pairId);
        if (pair == null) {
            return Optional.empty();
        }
        byStation.remove(pair.stationId());
        byEndpoint.remove(pair.anchorId());
        byEndpoint.remove(pair.terminalId());
        byColumn.remove(pair.column());
        changed.run();
        return Optional.of(pair);
    }
}
