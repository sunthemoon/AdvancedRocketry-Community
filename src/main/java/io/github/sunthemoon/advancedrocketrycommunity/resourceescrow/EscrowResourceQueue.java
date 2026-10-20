package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/** One server-wide FIFO. A cargo continuation retains its reconstruction turn until it finishes or cancels. */
public final class EscrowResourceQueue {
    public static final int CAPACITY = 256;
    public static final int VISITS_PER_TICK = 8;
    public static final int COMMITS_PER_TICK = 64;
    @FunctionalInterface public interface Attempt { boolean visit(); }
    private record Entry(UUID device, Attempt fuel, BooleanSupplier cargo, Runnable cancel) { }
    private final Thread owner = Thread.currentThread();
    private final LinkedHashMap<UUID, Entry> waiting = new LinkedHashMap<>();
    private final Set<UUID> preparing = new HashSet<>();
    private Entry active;
    boolean offer(UUID device, Attempt attempt) { return offer(new Entry(device, attempt, null, () -> { })); }
    boolean offerCargo(UUID device, BooleanSupplier step, Runnable cancel) { return offer(new Entry(device, null, step, cancel)); }
    private boolean offer(Entry entry) {
        owner(); if (waiting(entry.device())) { return true; }
        if (size() >= CAPACITY) { return false; }
        waiting.put(entry.device(), entry); return true;
    }
    public boolean waiting(UUID device) { owner(); return waiting.containsKey(device) || active != null && active.device().equals(device); }
    public int size() { owner(); return waiting.size() + (active == null ? 0 : 1); }
    void cancel(UUID device) {
        owner(); var entry = waiting.remove(device);
        if (active != null && active.device().equals(device)) { entry = active; active = null; }
        if (entry != null) { entry.cancel().run(); }
    }
    void preparing(UUID operation) { owner(); preparing.add(operation); }
    void resolved(UUID operation) { owner(); preparing.remove(operation); }
    public boolean preparing() { owner(); return !preparing.isEmpty(); }
    void tick(MinecraftServer server) {
        visit();
        if (preparing.isEmpty()) { return; }
        EscrowRuntime.of(server).checkpoint("LOADER_PREPARATIONS_QUEUED");
        var data = ResourceEscrowSavedData.existing(server);
        if (data != null && data.operational()) { data.bind(server).commitPreparations(); }
    }
    /** Eight visits bound admission below 64 commits; fuel still permits only one capture, costing no reconstruction. */
    int visit() {
        owner(); int visits = 0; boolean cargoVisited = active != null;
        if (active != null) { visitCargo(active); visits++; }
        // Snapshot keys permit callback cancellation/rebinding without invalidating an iterator.
        for (var id : java.util.List.copyOf(waiting.keySet())) {
            if (visits == Math.min(VISITS_PER_TICK, COMMITS_PER_TICK)) { break; }
            var entry = waiting.get(id); if (entry == null) { continue; }
            visits++;
            if (entry.cargo() != null) {
                if (cargoVisited) { continue; }
                waiting.remove(id); active = entry; cargoVisited = true; visitCargo(entry);
            } else {
                waiting.remove(id);
                if (entry.fuel().visit()) { break; }
            }
        }
        return visits;
    }
    private void visitCargo(Entry entry) {
        try {
            if (entry.cargo().getAsBoolean() && active == entry) { active = null; entry.cancel().run(); }
        } catch (RuntimeException | Error failure) {
            if (active == entry) { active = null; }
            entry.cancel().run(); throw failure;
        }
    }
    void clear() {
        owner(); var entries = new java.util.ArrayList<>(waiting.values()); waiting.clear();
        if (active != null) { entries.add(active); active = null; }
        entries.forEach(entry -> entry.cancel().run()); preparing.clear();
    }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Resource queue requires its owner thread"); }
}
