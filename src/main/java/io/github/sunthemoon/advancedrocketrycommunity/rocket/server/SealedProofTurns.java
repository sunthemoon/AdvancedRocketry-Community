package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Owner-thread admission turns only. Running proofs, native quotas and their deadlines remain with their owners. */
final class SealedProofTurns {
    private final Set<UUID> ordinary = new LinkedHashSet<>();
    private BooleanSupplier sealedWaiting = () -> false;
    private boolean ordinaryTurn;

    void bind(BooleanSupplier waiting) { sealedWaiting = java.util.Objects.requireNonNull(waiting); }
    boolean sealedRoom() { return !ordinaryTurn || ordinary.isEmpty(); }
    boolean ordinaryRoom(UUID id) {
        ordinary.add(id);
        return !sealedWaiting.getAsBoolean() || ordinaryTurn && ordinary.iterator().next().equals(id);
    }
    void ordinaryStarted(UUID id) { ordinary.remove(id); ordinaryTurn = false; }
    void withdraw(UUID id) { ordinary.remove(id); }
    void sealedEnded() { ordinaryTurn = true; }
    void clear() { ordinary.clear(); ordinaryTurn = false; }
}
