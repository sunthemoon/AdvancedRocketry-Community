package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded loaded-work scheduling hints, not locks, reservations or persistent authority. */
final class EscrowTransferTurns implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final Map<UUID, Registration> loaded = new HashMap<>();
    private final Map<UUID, UUID> next = new HashMap<>();
    private final Map<UUID, UUID> active = new HashMap<>();
    private boolean closed;
    final class Registration implements AutoCloseable {
        private final UUID device, rocket;
        private Registration(UUID device, UUID rocket) { this.device = device; this.rocket = rocket; }
        /** Device unload, rebinding or cancellation releases only this registration's transient turn. */
        @Override public void close() {
            owner();
            if (loaded.remove(device, this)) { next.remove(rocket, device); active.remove(rocket, device); }
        }
    }
    Registration register(UUID device, UUID rocket) {
        owner(); java.util.Objects.requireNonNull(device); java.util.Objects.requireNonNull(rocket);
        EscrowTags.require(!closed && !loaded.containsKey(device) && loaded.size() < EscrowResourceQueue.CAPACITY,
                "Transfer turn registration unavailable");
        var registration = new Registration(device, rocket); loaded.put(device, registration); return registration;
    }
    void blocked(UUID device, UUID rocket) {
        owner(); var registration = loaded.get(device);
        if (registration != null && registration.rocket.equals(rocket)) { next.putIfAbsent(rocket, device); }
    }
    boolean allows(UUID device, UUID rocket) {
        owner(); return !closed && (active.containsKey(rocket) ? active.get(rocket).equals(device)
                : !next.containsKey(rocket) || next.get(rocket).equals(device));
    }
    boolean claim(UUID device, UUID rocket) {
        owner(); var registration = loaded.get(device);
        if (registration == null || !registration.rocket.equals(rocket) || !allows(device, rocket)) { return false; }
        active.put(rocket, device); next.remove(rocket, device); return true;
    }
    void release(UUID device, UUID rocket) { owner(); active.remove(rocket, device); next.remove(rocket, device); }
    void admitted(UUID device, UUID rocket) { release(device, rocket); }
    int size() { owner(); return next.size(); }
    @Override public void close() { owner(); closed = true; next.clear(); active.clear(); loaded.clear(); }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Transfer turns require owner thread"); }
}
