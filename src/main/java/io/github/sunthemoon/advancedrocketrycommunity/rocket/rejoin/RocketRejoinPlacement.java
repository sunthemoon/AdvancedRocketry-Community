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
        boolean placed, readRequested, proof, submitted;
        Work(RocketRejoinGuards.Session session) { this.session = session; }
    }
    private final MinecraftServer server;
    private final RocketRejoinSavedData data;
    private final RocketRejoinGuards guards;
    private final RocketRejoinWorld world;
    private final java.util.function.Predicate<UUID> releaseReady;
    private final RocketRejoinReader reader = new RocketRejoinReader();
    private final RocketRejoinSaveClock saves = new RocketRejoinSaveClock();
    private final Map<UUID, Work> work = new LinkedHashMap<>();
    private int cursor;
    RocketRejoinPlacement(MinecraftServer server, RocketRejoinSavedData data, RocketRejoinGuards guards) {
        this(server, data, guards, release -> RocketRuntime.rejoinPlacementReady(server, release));
    }
    RocketRejoinPlacement(MinecraftServer server, RocketRejoinSavedData data, RocketRejoinGuards guards,
                         java.util.function.Predicate<UUID> releaseReady) {
        this.server = server; this.data = data; this.guards = guards; this.releaseReady = releaseReady; world = new RocketRejoinWorld(server);
    }
    void tick() {
        for (var s : guards.sessions()) {
            if (s.online && !s.disconnected && !work.containsKey(s.player.getUUID())) {
                if (work.size() < CAPACITY) { work.put(s.player.getUUID(), new Work(s)); }
                else { guards.reason(s, "capacity"); }
            }
        }
        int actions = 0;
        var result = reader.poll(); if (result != null) { apply(result); actions++; }
        var entries = new ArrayList<>(work.values()); int size = entries.size();
        // Reserve one action for the server-wide save. No callback mutates owner state off-thread.
        for (int i = 0; i < Math.min(ACTIONS - actions - 1, size); i++) {
            var next = entries.get(Math.floorMod(cursor++, size));
            if (work.get(next.session.player.getUUID()) != next) { continue; }
            step(next);
        }
        cursor = Math.floorMod(cursor, Math.max(1, work.size()));
        boolean pendingSave = work.values().stream().anyMatch(w -> w.placed && !w.submitted && w.session.online && !w.session.disconnected);
        if (saves.due(server.getTickCount(), pendingSave, reader.busy())) {
            for (var w : List.copyOf(work.values())) { if (w.placed && !w.submitted) { RocketRejoinGuards.pin(w.session); } }
            saves.saved(server.getTickCount()); server.getPlayerList().saveAll();
        }
    }
    private void step(Work w) {
        var s = w.session;
        if (guards.session(s.player.getUUID()) != s || s.disconnected || s.invalidated
                || !s.online && (!w.placed || server.getTickCount() >= s.deadline)) { cancel(s); return; }
        if (s.online && (!s.player.isAlive() || s.player.deathTime != 0)) { guards.reason(s, "dead"); return; }
        if (w.submitted) { finish(w); return; }
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
        if (!s.online) { verify(w); return; }
        if (!releaseReady.test(w.entry.release())) { return; }
        if (!world.ticket(s, w.batch)) { guards.reason(s, "tickets"); return; }
        if (w.placed) {
            if (w.attempt.equals(w.entry.attempt()) && world.safe(s.player, w.batch, w.attempt)) { verify(w); return; }
            w.placed = false; w.proof = false; w.readRequested = false; w.attempt = null;
            s.player.getPersistentData().remove(RocketRejoinMarker.KEY);
        }
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
        if (!attemptAcknowledged(acknowledged, w.entry)) { return; }
        // Exact P1, same live session, and a second safety check immediately before any teleport.
        if (!world.safe(s.player, w.batch, attempt)) { guards.reason(s, "unsafe"); return; }
        w.attempt = attempt; s.level = world.level(w.batch, attempt.target()); s.position = new Vec3(attempt.x(), attempt.y(), attempt.z());
        RocketRejoinGuards.pin(s);
        s.player.getPersistentData().put(RocketRejoinMarker.KEY, RocketRejoinMarker.encode(
                new RocketRejoinMarker.Value(w.entry.release(), w.entry.player(), attempt.generation())));
        w.placed = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_REJOIN_PLACED player={} release={} generation={} target={}",
                w.entry.player(), w.entry.release(), attempt.generation(), attempt.target());
        RocketRejoinNativeCuts.placed(s.player, w.entry, data);
    }
    void saved(net.minecraft.server.level.ServerPlayer player) {
        var w = work.get(player.getUUID());
        if (w != null && w.session.player == player && w.placed && !w.submitted && !w.session.invalidated
                && RocketRejoinMarker.matches(player.getPersistentData(), w.entry)) { w.readRequested = true; }
        RocketRejoinNativeCuts.saved(player);
    }
    private void verify(Work w) {
        if (!w.proof) {
            if (w.readRequested && !reader.busy()) {
                w.readRequested = false;
                reader.start(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR), w,
                        new RocketRejoinPlayerFile.Expected(w.entry.player(), w.entry.release(), w.attempt.generation(),
                                w.attempt.target() == Target.PAD ? w.batch.dimension() : "minecraft:overworld",
                                w.attempt.x(), w.attempt.y(), w.attempt.z()));
            }
            return;
        }
        if (!data.pendingRoots().isEmpty()) { return; }
        var current = data.root().entries().stream().filter(e -> e.key().equals(w.entry.key())).findFirst().orElse(null);
        if (current == null || current.placement() != Placement.PENDING || !w.attempt.equals(current.attempt())) { return; }
        // Applying the read result is linearized here on the current owner-thread session, never in an IO callback.
        if (w.session.online && !world.safe(w.session.player, w.batch, w.attempt)) { guards.reason(w.session, "unsafe"); return; }
        w.submitted = data.submit(data.root().update(new Entry(current.release(), current.player(), current.seatIndex(), Placement.VERIFIED, current.seat(), null)));
        if (!w.submitted) { guards.reason(w.session, "io"); }
    }
    private void apply(RocketRejoinReader.Completion result) {
        var w = (Work) result.identity(); var s = w.session;
        if (work.get(s.player.getUUID()) != w || guards.session(s.player.getUUID()) != s || s.invalidated || s.disconnected
                || !w.placed || !w.attempt.generation().equals(result.expected().generation()) || w.submitted) { return; }
        if (s.online && (!s.player.isAlive() || s.player.deathTime != 0)) { guards.reason(s, "dead"); return; }
        w.proof = result.result().status() == RocketRejoinPlayerFile.Status.MATCH;
        if (w.proof) { RocketReleaseNativeCuts.checkpoint("C10"); }
        if (!w.proof) {
            boolean oversized = result.result().status() == RocketRejoinPlayerFile.Status.OVERSIZED || result.result().status() == RocketRejoinPlayerFile.Status.DEPTH;
            if (oversized && !s.reason.equals("oversized")) {
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_REJOIN_PLAYER_LIMIT player={} kind={} size={} ceiling={}",
                        w.entry.player(), result.result().status(), result.result().size(), result.result().ceiling());
            }
            guards.reason(s, oversized ? "oversized" : "io");
            if (result.result().status() == RocketRejoinPlayerFile.Status.CHANGED) { w.readRequested = true; }
        }
    }
    private void finish(Work w) {
        var installed = data.acknowledgedRoot(); if (installed == null) { return; }
        if (installed.entries().stream().anyMatch(e -> e.key().equals(w.entry.key()) && e.placement() == Placement.PENDING)) { return; }
        if (w.session.online && !world.safe(w.session.player, w.batch, w.attempt)) { guards.reason(w.session, "unsafe"); return; }
        w.session.player.getPersistentData().remove(RocketRejoinMarker.KEY);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_REJOIN_VERIFIED player={} release={} generation={}",
                w.entry.player(), w.entry.release(), w.attempt.generation());
        guards.complete(w.session);
    }
    void cancel(RocketRejoinGuards.Session session) {
        var w = work.get(session.player.getUUID());
        if (w != null && w.session == session) { work.remove(session.player.getUUID()); world.release(session.player.getUUID()); }
    }
    void logout(RocketRejoinGuards.Session session) { world.release(session.player.getUUID()); }
    static boolean attemptAcknowledged(RocketRejoinRoot installed, Entry entry) {
        return installed != null && entry.attempt() != null && installed.entries().stream().anyMatch(e -> e.key().equals(entry.key())
                && e.placement() == Placement.PENDING && entry.attempt().equals(e.attempt()));
    }
    @Override public void close() { reader.close(); work.clear(); world.close(); }
}
