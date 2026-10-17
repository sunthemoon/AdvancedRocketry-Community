package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * The loaders' fair server-thread queue (W11B3-DESIGN section 2, "One attempt"), runtime-owned. A paced device that
 * would start a fill waits here once, in arrival order. Each tick visits at most eight entries and runs at most one
 * endpoint capture; a visited entry leaves the queue whatever its outcome, so a refusal retries only at its device's
 * next paced attempt and no device can hold the head. It is the only production caller of prepareFill, and it drives
 * the L1 commits of exactly the fills it queued, never another caller's preparations.
 */
public final class EscrowFillQueue {
    public static final int CAPACITY = 256;
    public static final int VISITS_PER_TICK = 8;
    /** One queued device's visit; true when it ran an endpoint capture, whatever the outcome. */
    @FunctionalInterface public interface Attempt { boolean visit(); }
    private final Thread owner = Thread.currentThread();
    private final LinkedHashMap<UUID, Attempt> waiting = new LinkedHashMap<>();
    private final Set<UUID> preparing = new HashSet<>();

    /** True when the device waits (now or already); false when the bounded queue is full. */
    boolean offer(UUID device, Attempt attempt) {
        owner();
        if (waiting.containsKey(device)) { return true; }
        if (waiting.size() >= CAPACITY) { return false; }
        waiting.put(device, attempt); return true;
    }
    public boolean waiting(UUID device) { owner(); return waiting.containsKey(device); }
    public int size() { owner(); return waiting.size(); }
    /** A fill this queue admitted, until its exact L1 acknowledgement or definite failure. */
    void preparing(UUID operation) { owner(); preparing.add(operation); }
    void resolved(UUID operation) { owner(); preparing.remove(operation); }
    public boolean preparing() { owner(); return !preparing.isEmpty(); }

    void tick(MinecraftServer server) {
        visit();
        if (preparing.isEmpty()) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        if (data != null && data.operational()) { data.bind(server).commitPreparations(); }
    }
    /** One tick's visits, in arrival order; returns how many ran. */
    int visit() {
        owner(); int visits = 0;
        while (visits < VISITS_PER_TICK && !waiting.isEmpty()) {
            // Remove before visiting: a visit may admit, refuse or offer again without disturbing this iteration.
            var head = waiting.entrySet().iterator().next(); waiting.remove(head.getKey());
            visits++;
            if (head.getValue().visit()) { break; }
        }
        return visits;
    }
    void clear() { owner(); waiting.clear(); preparing.clear(); }
    private void owner() { EscrowTags.require(Thread.currentThread() == owner, "Fill queue requires its owner thread"); }
}
