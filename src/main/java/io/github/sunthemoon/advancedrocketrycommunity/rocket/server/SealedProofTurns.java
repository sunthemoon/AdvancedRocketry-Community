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

    void bind(BooleanSupplier waiting) { sealedWaiting = java.util.Objects.requireNonNull(waiting); }
    private UUID nextOrdinary() {
        return ordinary.entrySet().stream().filter(entry -> entry.getValue().getAsBoolean())
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    boolean sealedRoom() { return !ordinaryTurn || nextOrdinary() == null; }
    boolean ordinaryRoom(UUID id) { return ordinaryRoom(id, () -> true); }
    boolean ordinaryRoom(UUID id, BooleanSupplier due) {
        ordinary.putIfAbsent(id, due);
        return !sealedWaiting.getAsBoolean() || ordinaryTurn && id.equals(nextOrdinary());
    }
    void ordinaryStarted(UUID id) { ordinary.remove(id); ordinaryTurn = false; }
    void withdraw(UUID id) { ordinary.remove(id); }
    void sealedEnded() { ordinaryTurn = true; }
    void clear() { ordinary.clear(); ordinaryTurn = false; }
}
