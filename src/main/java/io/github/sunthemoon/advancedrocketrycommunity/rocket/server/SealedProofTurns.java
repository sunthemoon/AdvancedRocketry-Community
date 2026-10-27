package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Owner-thread admission turns only. Running proofs, native quotas and their deadlines remain with their owners. */
final class SealedProofTurns {
    private final Map<UUID, BooleanSupplier> ordinary = new LinkedHashMap<>();
    private BooleanSupplier sealedWaiting = () -> false;
    private boolean ordinaryTurn;
    // One class-level retry opportunity, never a retained caller, callback, token or pending entry.
    private int refusedTicks;

    void bind(BooleanSupplier waiting) { sealedWaiting = java.util.Objects.requireNonNull(waiting); }
    private UUID nextOrdinary() {
        return ordinary.entrySet().stream().filter(entry -> entry.getValue().getAsBoolean())
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    boolean sealedRoom() { return !ordinaryTurn || nextOrdinary() == null && refusedTicks == 0; }
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
    void reservationRefused() { refusedTicks = RocketOrdinaryRetirement.ATTEMPT_TICKS + 2; }
    void reservationAdmitted() { refusedTicks = 0; }
    /** Called even with no pending returns, once per ordinary service tick. */
    void tick() { if (refusedTicks > 0) { refusedTicks--; } }
    void ordinaryStarted(UUID id) { ordinary.remove(id); ordinaryReservationHeld(); }
    /** Ineligible holds yield admission; an unavailable native window alone does not spend the capture turn. */
    void ordinaryReservationHeld() { ordinaryTurn = false; refusedTicks = 0; }
    void withdraw(UUID id) { ordinary.remove(id); }
    void sealedEnded() { ordinaryTurn = true; refusedTicks = Math.min(refusedTicks, 2); }
    void clear() { ordinary.clear(); ordinaryTurn = false; refusedTicks = 0; }
}
