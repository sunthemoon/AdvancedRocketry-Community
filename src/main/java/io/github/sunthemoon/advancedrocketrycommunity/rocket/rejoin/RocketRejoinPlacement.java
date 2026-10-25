package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/** Bounded owner-thread placement track. The guard does not depend on admission to this queue. */
final class RocketRejoinPlacement implements AutoCloseable {
    static final int CAPACITY = 128, ACTIONS = 4;
    static final class Work {
        final RocketRejoinGuards.Session session;
        Entry entry;
        Batch batch;
        Attempt attempt;
        boolean placed;
        Work(RocketRejoinGuards.Session session) { this.session = session; }
    }
    private final MinecraftServer server;
    private final RocketRejoinSavedData data;
    private final RocketRejoinGuards guards;
    private final RocketRejoinWorld world;
    private final Map<UUID, Work> work = new LinkedHashMap<>();
    private int cursor;
    RocketRejoinPlacement(MinecraftServer server, RocketRejoinSavedData data, RocketRejoinGuards guards) {
        this.server = server; this.data = data; this.guards = guards; world = new RocketRejoinWorld(server);
    }
    void tick() {
        for (var s : guards.sessions()) {
            if (s.online && !s.disconnected && !work.containsKey(s.player.getUUID())) {
                if (work.size() < CAPACITY) { work.put(s.player.getUUID(), new Work(s)); }
                else { guards.reason(s, "capacity"); }
            }
        }
        var entries = new ArrayList<>(work.values()); int size = entries.size();
        for (int i = 0; i < Math.min(ACTIONS, size); i++) {
            var next = entries.get(Math.floorMod(cursor++, size));
            if (work.get(next.session.player.getUUID()) != next) { continue; }
            step(next);
        }
        cursor = Math.floorMod(cursor, Math.max(1, work.size()));
    }
    private void step(Work w) {
        var s = w.session;
        if (guards.session(s.player.getUUID()) != s || s.disconnected || !s.online) { cancel(s); return; }
        if (!s.player.isAlive() || s.player.deathTime != 0) { guards.reason(s, "dead"); return; }
        var entries = data.root().entries().stream().filter(e -> e.player().equals(s.player.getUUID()) && e.placement() == Placement.PENDING).toList();
        if (entries.size() > 1) { guards.reason(s, "conflict"); return; }
        if (entries.isEmpty()) { return; } // B0/S1 admission, or P2 awaiting acknowledgement.
        w.entry = entries.get(0);
        w.batch = data.root().batches().stream().filter(b -> b.release().equals(w.entry.release())).findFirst().orElseThrow();
        var journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) { guards.reason(s, "io"); return; }
        if (journal.entries().stream().anyMatch(r -> !r.transferId().equals(w.batch.transfer())
                && (r.sourceFlightData().passengers().assignment(s.player.getUUID()).isPresent()
                || r.destinationFlightData().passengers().assignment(s.player.getUUID()).isPresent()))) {
            guards.reason(s, "conflict"); return;
        }
        if (!RocketRuntime.rejoinPlacementReady(server, w.entry.release())) { return; }
        if (!world.ticket(s, w.batch)) { guards.reason(s, "tickets"); return; }
        if (w.placed) { return; }
        Attempt attempt = w.entry.attempt();
        if (attempt == null || !world.safe(s.player, w.batch, attempt)) {
            if (!data.pendingRoots().isEmpty()) { return; }
            attempt = world.choose(s.player, w.batch);
            if (attempt == null) { guards.reason(s, "unsafe"); return; }
            var replacement = new Entry(w.entry.release(), w.entry.player(), w.entry.seatIndex(), Placement.PENDING, w.entry.seat(), attempt);
            if (!data.submit(data.root().update(replacement))) { guards.reason(s, "io"); }
            return;
        }
        var acknowledged = data.acknowledgedRoot();
        if (acknowledged == null || !acknowledged.entries().contains(w.entry)) { return; }
        // Exact P1, same live session, and a second safety check immediately before any teleport.
        if (!world.safe(s.player, w.batch, attempt)) { guards.reason(s, "unsafe"); return; }
        w.attempt = attempt; s.level = world.level(w.batch, attempt.target()); s.position = new Vec3(attempt.x(), attempt.y(), attempt.z());
        RocketRejoinGuards.pin(s);
        s.player.getPersistentData().put(RocketRejoinMarker.KEY, RocketRejoinMarker.encode(
                new RocketRejoinMarker.Value(w.entry.release(), w.entry.player(), attempt.generation())));
        w.placed = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_REJOIN_PLACED player={} release={} generation={} target={}",
                w.entry.player(), w.entry.release(), attempt.generation(), attempt.target());
    }
    void cancel(RocketRejoinGuards.Session session) {
        var w = work.get(session.player.getUUID());
        if (w != null && w.session == session) { work.remove(session.player.getUUID()); world.release(session.player.getUUID()); }
    }
    @Override public void close() { work.clear(); world.close(); }
}
