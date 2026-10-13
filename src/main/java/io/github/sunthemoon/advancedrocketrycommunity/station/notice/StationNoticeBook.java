package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The bounded offline member-notice store of ADR-065 section 5.3: one summary per station, at most
 * {@link StationNoticeLimits#MAX_STATIONS}, each replaced in place, so it is never an event history. A station without
 * a summary has the empty one. Every change that names a live station reconciles its summary with that station first,
 * so no acknowledgement of a former owner or member survives it. A refused change leaves the book as it was.
 */
public final class StationNoticeBook {
    /** The outcome of one change. */
    public enum Admission {
        ADMITTED, UNCHANGED, FULL, EXHAUSTED
    }

    private final Map<UUID, StationNoticeSummary> summaries = new HashMap<>();

    public StationNoticeSummary summary(UUID stationId) {
        return summaries.getOrDefault(Objects.requireNonNull(stationId, "stationId"), StationNoticeSummary.EMPTY);
    }

    public boolean contains(UUID stationId) {
        return summaries.containsKey(stationId);
    }

    public int size() {
        return summaries.size();
    }

    /** A new station's empty summary. */
    public Admission add(UUID stationId) {
        Objects.requireNonNull(stationId, "stationId");
        if (summaries.containsKey(stationId)) {
            return Admission.UNCHANGED;
        }
        if (summaries.size() >= StationNoticeLimits.MAX_STATIONS) {
            return Admission.FULL;
        }
        summaries.put(stationId, StationNoticeSummary.EMPTY);
        return Admission.ADMITTED;
    }

    /**
     * Replaces the station's outcome in place with {@code outcome}'s summary at the next revision, then records the
     * acknowledgement of each current owner or member in {@code told}, who saw the outcome live.
     */
    public Admission record(StationState station, StationNotice outcome, Collection<UUID> told) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(told, "told");
        StationNoticeSummary current = summaries.get(station.stationId());
        if (current == null && summaries.size() >= StationNoticeLimits.MAX_STATIONS) {
            return Admission.FULL;
        }
        current = current == null ? StationNoticeSummary.EMPTY : current;
        if (current.revision() == Long.MAX_VALUE) {
            return Admission.EXHAUSTED;
        }
        StationNoticeSummary next = current.reconciled(station.ownerId(), station.members()).replacedBy(outcome);
        for (UUID player : told) {
            next = next.acknowledgedBy(player, station.ownerId(), station.members());
        }
        summaries.put(station.stationId(), next);
        return Admission.ADMITTED;
    }

    /** The outcome {@code player} has not acknowledged, if the player is the station's owner or a member now. */
    public Optional<StationNotice> pending(StationState station, UUID player) {
        return summary(station.stationId()).pendingFor(player, station.ownerId(), station.members());
    }

    /** Records that {@code player}, the station's owner or a member, has seen its current outcome. */
    public boolean acknowledge(StationState station, UUID player) {
        StationNoticeSummary current = summaries.get(station.stationId());
        if (current == null) {
            return false;
        }
        return replace(station.stationId(), current, current.reconciled(station.ownerId(), station.members())
                .acknowledgedBy(player, station.ownerId(), station.members()));
    }

    /** Drops the acknowledgements of players who are no longer the station's owner or a member. */
    public boolean reconcile(StationState station) {
        StationNoticeSummary current = summaries.get(station.stationId());
        return current != null && replace(station.stationId(), current,
                current.reconciled(station.ownerId(), station.members()));
    }

    public boolean remove(UUID stationId) {
        return summaries.remove(stationId) != null;
    }

    /** Removes the summaries of stations that no longer exist and returns how many were removed. */
    public int retain(Predicate<UUID> exists) {
        int before = summaries.size();
        summaries.keySet().removeIf(stationId -> !exists.test(stationId));
        return before - summaries.size();
    }

    /** Every summary in canonical order (lowercase UUID string), for a deterministic encoding. */
    public List<Map.Entry<UUID, StationNoticeSummary>> entries() {
        List<Map.Entry<UUID, StationNoticeSummary>> sorted = new ArrayList<>(summaries.size());
        summaries.forEach((stationId, summary) -> sorted.add(Map.entry(stationId, summary)));
        sorted.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        return List.copyOf(sorted);
    }

    /** Decoding only: one stored summary of a station not yet in the book, within the entry bound. */
    void restore(UUID stationId, StationNoticeSummary summary) {
        Objects.requireNonNull(summary, "summary");
        if (summaries.containsKey(Objects.requireNonNull(stationId, "stationId"))) {
            throw new IllegalArgumentException("Duplicate station notice summary " + stationId);
        }
        if (summaries.size() >= StationNoticeLimits.MAX_STATIONS) {
            throw new IllegalArgumentException("Station notice summaries exceed the fixed bound");
        }
        summaries.put(stationId, summary);
    }

    private boolean replace(UUID stationId, StationNoticeSummary current, StationNoticeSummary next) {
        if (next.equals(current)) {
            return false;
        }
        summaries.put(stationId, next);
        return true;
    }
}
