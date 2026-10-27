package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Owner-thread admission turns only. Running proofs, native quotas and their deadlines remain with their owners. */
final class SealedProofTurns {
    private static final long NONE = Long.MIN_VALUE;
    private final Map<UUID, BooleanSupplier> ordinary = new LinkedHashMap<>();
    private BooleanSupplier sealedWaiting = () -> false;
    // W152J-R5: the sealed owner binds its server tick. A unit composition without one counts ordinary service ticks.
    private long serviceTicks;
    private LongSupplier now = () -> serviceTicks;
    private boolean ordinaryTurn;
    // One class-level retry opportunity, never a retained caller, callback, token or pending entry: its absolute expiry.
    private long refusedUntil = NONE;

    void bind(BooleanSupplier waiting) { sealedWaiting = java.util.Objects.requireNonNull(waiting); }
    /** The server tick both services already read, so the window also expires while ordinary service is stopped. */
    void bindServerTick(LongSupplier tick) { now = java.util.Objects.requireNonNull(tick); }
    private boolean refusedOpen() { return refusedUntil != NONE && now.getAsLong() < refusedUntil; }
    private UUID nextOrdinary() {
        return ordinary.entrySet().stream().filter(entry -> entry.getValue().getAsBoolean())
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    boolean sealedRoom() { return !ordinaryTurn || nextOrdinary() == null && !refusedOpen(); }
    boolean ordinaryRoom(UUID id) { return ordinaryRoom(id, () -> true); }
    boolean ordinaryRoom(UUID id, BooleanSupplier due) {
        ordinary.putIfAbsent(id, due);
        return !sealedWaiting.getAsBoolean() || ordinaryTurn && id.equals(nextOrdinary());
    }
    /** Before caller mutation: a new caller cannot pass an older due return. */
    boolean ordinaryReservationRoom(UUID id) {
        if (!sealedWaiting.getAsBoolean()) { return true; }
        UUID next = nextOrdinary();
        return ordinaryTurn && (next == null || id.equals(next));
    }
    /** Only refusals behind an active sealed attempt announce this bounded, anonymous opportunity. */
    void reservationRefused() { refusedUntil = now.getAsLong() + RocketOrdinaryRetirement.ATTEMPT_TICKS + 2; }
    void reservationAdmitted() { refusedUntil = NONE; }
    /** Called even with no pending returns, once per ordinary service tick; the clock only without a server tick. */
    void tick() { serviceTicks++; }
    void ordinaryStarted(UUID id) { ordinary.remove(id); ordinaryReservationHeld(); }
    /** Ineligible holds yield admission; an unavailable native window alone does not spend the capture turn. */
    void ordinaryReservationHeld() { ordinaryTurn = false; refusedUntil = NONE; }
    void withdraw(UUID id) { ordinary.remove(id); }
    void sealedEnded() {
        ordinaryTurn = true;
        if (refusedUntil != NONE) { refusedUntil = Math.min(refusedUntil, now.getAsLong() + 2); }
    }
    void clear() { ordinary.clear(); ordinaryTurn = false; refusedUntil = NONE; }
}
