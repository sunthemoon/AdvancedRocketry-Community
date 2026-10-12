package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-issued, one-shot warp confirmations (ADR-044 §4): one per actor, 128 in total, each
 * bound to its quote and the registry instance, expiring after 200 ticks and never persisted.
 */
public final class StationWarpConfirmations {
    private final Map<UUID, Pending> byActor = new LinkedHashMap<>();

    /** Replaces the actor's previous confirmation; rejects a new actor while the store is full. */
    public boolean issue(WarpQuote quote, Object authority, long nowTick) {
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(authority, "authority");
        purgeExpired(nowTick);
        Pending previous = byActor.remove(quote.actorId());
        if (previous == null && byActor.size() >= StationLimits.MAX_PENDING_WARPS) {
            return false;
        }
        byActor.put(quote.actorId(), new Pending(quote, authority, nowTick));
        return true;
    }

    /** Removes and checks the actor's confirmation; it can be used at most once. */
    public Outcome take(UUID actorId, UUID stationId, Object authority, long nowTick) {
        return take(actorId, stationId, authority, nowTick, null);
    }

    /**
     * As {@link #take(UUID, UUID, Object, long)}; with {@code expected}, only that exact issued quote is ready and
     * any other confirmation of the actor is a mismatch (a screen confirms the quote it shows, W07 review R1).
     */
    public Outcome take(UUID actorId, UUID stationId, Object authority, long nowTick, WarpQuote expected) {
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
        if (!pending.quote().stationId().equals(stationId) || pending.authority() != authority
                || expected != null && pending.quote() != expected) {
            return new Outcome(Status.MISMATCH, null);
        }
        return new Outcome(Status.READY, pending.quote());
    }

    /** Whether the actor's live confirmation is exactly {@code quote}. */
    public boolean holds(UUID actorId, WarpQuote quote, long nowTick) {
        Objects.requireNonNull(actorId, "actorId");
        Pending pending = byActor.get(actorId);
        return quote != null && pending != null && pending.quote() == quote && !expired(pending, nowTick);
    }

    /** Removes the actor's confirmation only while it is exactly {@code quote}; another one stays. */
    public boolean withdraw(UUID actorId, WarpQuote quote) {
        Objects.requireNonNull(actorId, "actorId");
        Pending pending = byActor.get(actorId);
        if (quote == null || pending == null || pending.quote() != quote) {
            return false;
        }
        byActor.remove(actorId);
        return true;
    }

    /** Whether a live confirmation is pending for this station (ADR-059 section 3 step 5). */
    public boolean pending(UUID stationId, long nowTick) {
        Objects.requireNonNull(stationId, "stationId");
        for (Pending pending : byActor.values()) {
            if (!expired(pending, nowTick) && pending.quote().stationId().equals(stationId)) {
                return true;
            }
        }
        return false;
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
                || nowTick - pending.issuedAtTick() > StationLimits.WARP_CONFIRMATION_TICKS;
    }

    private record Pending(WarpQuote quote, Object authority, long issuedAtTick) {
    }

    public enum Status {
        READY,
        MISSING,
        EXPIRED,
        MISMATCH
    }

    public record Outcome(Status status, WarpQuote quote) {
    }
}
