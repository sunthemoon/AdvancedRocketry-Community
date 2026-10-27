package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Owner-thread admission turns only. Running proofs, native quotas and their deadlines remain with their owners. */
final class SealedProofTurns {
    private static final long NONE = Long.MIN_VALUE, UNLIMITED = Long.MAX_VALUE;
    private final Map<UUID, BooleanSupplier> ordinary = new LinkedHashMap<>();
    private BooleanSupplier sealedWaiting = () -> false;
    // W152J-R5: the sealed owner binds its server tick. A unit composition without one counts ordinary service ticks.
    private long serviceTicks;
    private LongSupplier now = () -> serviceTicks;
    private boolean ordinaryTurn;
    // W152J-R3: after an ineligible hold in the window, the turn lasts only to the window's expiry, and only for new callers.
    private long turnUntil = UNLIMITED;
    // One class-level retry opportunity, never a retained caller, callback, token or pending entry: its absolute expiry.
    private long refusedUntil = NONE;

    void bind(BooleanSupplier waiting) { sealedWaiting = java.util.Objects.requireNonNull(waiting); }
    /** The server tick both services already read, so the window also expires while ordinary service is stopped. */
    void bindServerTick(LongSupplier tick) { now = java.util.Objects.requireNonNull(tick); }
    private boolean refusedOpen() { return refusedUntil != NONE && now.getAsLong() < refusedUntil; }
    private boolean turn() { return ordinaryTurn && (turnUntil == UNLIMITED || now.getAsLong() < turnUntil); }
    private UUID nextOrdinary() {
        return ordinary.entrySet().stream().filter(entry -> entry.getValue().getAsBoolean())
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    boolean sealedRoom() { return !turn() || nextOrdinary() == null && !refusedOpen(); }
    boolean ordinaryRoom(UUID id) { return ordinaryRoom(id, () -> true); }
    boolean ordinaryRoom(UUID id, BooleanSupplier due) {
        ordinary.putIfAbsent(id, due);
        return !sealedWaiting.getAsBoolean() || turn() && id.equals(nextOrdinary());
    }
    boolean ordinaryReservationRoom(UUID id) { return ordinaryReservationRoom(id, false); }
    /** Before caller mutation: a new caller cannot pass an older due return, and a held window takes no pending caller. */
    boolean ordinaryReservationRoom(UUID id, boolean pending) {
        if (!sealedWaiting.getAsBoolean()) { return true; }
        if (!turn() || pending && turnUntil != UNLIMITED) { return false; }
        UUID next = nextOrdinary();
        return next == null || id.equals(next);
    }
    /** Only refusals behind an active sealed attempt announce this bounded, anonymous opportunity. */
    void reservationRefused() { refusedUntil = now.getAsLong() + RocketOrdinaryRetirement.ATTEMPT_TICKS + 2; }
    /** An eligible claim that keeps its queue place (a native-window BUSY) uses the window up and keeps the turn. */
    void reservationAdmitted() { refusedUntil = NONE; turnUntil = UNLIMITED; }
    /** Called even with no pending returns, once per ordinary service tick; the clock only without a server tick. */
    void tick() { serviceTicks++; }
    /** A first capture or removeUnproved ends the turn and uses the window up. */
    void ordinaryStarted(UUID id) { ordinary.remove(id); ordinaryTurn = false; turnUntil = UNLIMITED; refusedUntil = NONE; }
    /**
     * An ineligible creation-tick hold yields admission. While the window is open it leaves the turn to a new caller
     * until the window's own expiry instead, and never moves that expiry. A native-window BUSY is not such a hold.
     */
    void ordinaryReservationHeld() {
        if (refusedOpen()) { turnUntil = Math.min(turnUntil, refusedUntil); } else { ordinaryTurn = false; }
    }
    void withdraw(UUID id) { ordinary.remove(id); }
    void sealedEnded() {
        ordinaryTurn = true; turnUntil = UNLIMITED;
        if (refusedUntil != NONE) { refusedUntil = Math.min(refusedUntil, now.getAsLong() + 2); }
    }
    void clear() { ordinary.clear(); ordinaryTurn = false; turnUntil = UNLIMITED; refusedUntil = NONE; }
}
