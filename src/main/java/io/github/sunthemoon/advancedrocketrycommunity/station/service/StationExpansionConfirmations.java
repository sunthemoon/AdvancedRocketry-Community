package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-issued, one-shot expansion confirmations. Each is bound to its actor, the observed
 * immutable station state and the registry instance, and never survives logout or server stop.
 */
public final class StationExpansionConfirmations {
    private final Map<UUID, Pending> byActor = new LinkedHashMap<>();

    /** Replaces the actor's previous confirmation; rejects a new actor while the store is full. */
    public boolean issue(UUID actorId, StationState observed, Object authority, long nowTick) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(authority, "authority");
        purgeExpired(nowTick);
        Pending previous = byActor.remove(actorId);
        if (previous == null && byActor.size() >= StationLimits.MAX_PENDING_EXPANSIONS) {
            return false;
        }
        byActor.put(actorId, new Pending(observed, authority, nowTick));
        return true;
    }

    /** Removes and checks the actor's confirmation; it can be used at most once. */
    public Outcome take(UUID actorId, UUID stationId, Object authority, long nowTick) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(stationId, "stationId");
        Pending pending = byActor.remove(actorId);
        purgeExpired(nowTick);
        if (pending == null) {
            return new Outcome(Status.MISSING, null);
        }
        if (expired(pending, nowTick)) {
            return new Outcome(Status.EXPIRED, null);
        }
        if (!pending.observed().stationId().equals(stationId) || pending.authority() != authority) {
            return new Outcome(Status.MISMATCH, null);
        }
        return new Outcome(Status.READY, pending.observed());
    }

    public void clear(UUID actorId) {
        byActor.remove(Objects.requireNonNull(actorId, "actorId"));
    }

    public void clear() {
        byActor.clear();
    }

    public int size() {
        return byActor.size();
    }

    /** Insertion order is issue order, so purging stops at the first live entry. */
    private void purgeExpired(long nowTick) {
        Iterator<Pending> entries = byActor.values().iterator();
        while (entries.hasNext() && expired(entries.next(), nowTick)) {
            entries.remove();
        }
    }

    private static boolean expired(Pending pending, long nowTick) {
        return nowTick < pending.issuedAtTick()
                || nowTick - pending.issuedAtTick() > StationLimits.EXPANSION_CONFIRMATION_TICKS;
    }

    private record Pending(StationState observed, Object authority, long issuedAtTick) {
    }

    public enum Status {
        READY,
        MISSING,
        EXPIRED,
        MISMATCH
    }

    public record Outcome(Status status, StationState observed) {
    }
}
