package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.phys.Vec3;

/** Server-lifetime protection independent of placement queue admission. No persistent immunity flags. */
public final class RocketRejoinGuards {
    public static final int DEADLINE = 200;
    static final class Session {
        final UUID token = UUID.randomUUID();
        final ServerPlayer player;
        final long deadline;
        final Set<MobEffect> originalEffects;
        ServerLevel level;
        Vec3 position;
        boolean online = true, disconnected, durable, invalidated;
        String reason = "waiting";
        Session(ServerPlayer player, long now, boolean durable) {
            this.player = player; deadline = now + DEADLINE; this.durable = durable;
            level = player.serverLevel(); position = player.position();
            originalEffects = new HashSet<>(player.getActiveEffectsMap().keySet());
        }
    }
    private final MinecraftServer server;
    private final RocketRejoinSavedData data;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final RocketRejoinPlacement placement;

    RocketRejoinGuards(MinecraftServer server, RocketRejoinSavedData data) {
        this(server, data, release -> RocketRuntime.rejoinPlacementReady(server, release));
    }
    RocketRejoinGuards(MinecraftServer server, RocketRejoinSavedData data, java.util.function.Predicate<UUID> releaseReady) {
        this.server = server; this.data = data; placement = new RocketRejoinPlacement(server, data, this, releaseReady);
    }

    RocketRejoinIndex index() {
        var roots = new ArrayList<>(data.pendingRoots()); roots.add(data.root());
        if (data.acknowledgedRoot() != null) { roots.add(data.acknowledgedRoot()); }
        return new RocketRejoinIndex(roots, RocketRuntime.rejoinAdmissions(server));
    }
    public boolean required(UUID player) { return index().contains(player); }
    public boolean active(ServerPlayer player) { var s = sessions.get(player.getUUID()); return s != null && s.player == player; }
    Session session(UUID player) { return sessions.get(player); }
    java.util.List<Session> sessions() { return java.util.List.copyOf(sessions.values()); }
    public void join(ServerPlayer player) {
        if (data.root().entries().stream().noneMatch(e -> e.player().equals(player.getUUID())
                && e.placement() == RocketRejoinRoot.Placement.PENDING && RocketRejoinMarker.matches(player.getPersistentData(), e))) {
            player.getPersistentData().remove(RocketRejoinMarker.KEY);
        }
        var old = sessions.get(player.getUUID());
        if (old != null && old.player == player && old.online) { return; }
        if (old != null) { placement.cancel(old); sessions.remove(player.getUUID()); }
        var index = index();
        if (!index.contains(player.getUUID())) { return; }
        if (index.overflow() || sessions.size() >= RocketRejoinRoot.MAX_ENTRIES) {
            pin(player, player.serverLevel(), player.position());
            player.connection.disconnect(message("capacity", true)); return;
        }
        var session = new Session(player, server.getTickCount(), durable(player.getUUID()));
        sessions.put(player.getUUID(), session); pin(session);
        log(session, "guard");
        player.displayClientMessage(message("waiting", false), false);
    }
    private boolean durable(UUID player) {
        return data.root().entries().stream().anyMatch(e -> e.player().equals(player)
                && e.placement() == RocketRejoinRoot.Placement.PENDING);
    }
    public void tick() {
        var index = index();
        for (var s : sessions()) {
            s.durable |= durable(s.player.getUUID());
            if (!index.contains(s.player.getUUID()) && !s.durable) {
                complete(s);
                if (s.online && !s.disconnected) { RocketRuntime.resumeDroppedRejoin(s.player); }
            } else if (s.online && !s.disconnected && server.getTickCount() >= s.deadline) {
                pin(s); s.disconnected = true; placement.cancel(s); log(s, "disconnect"); s.player.connection.disconnect(message(s.reason, true));
            }
        }
        placement.tick();
    }
    void reason(Session s, String reason) {
        if (!s.reason.equals(reason)) {
            s.reason = reason; log(s, reason);
            if (s.online) { s.player.displayClientMessage(message(reason, false), false); }
        }
    }
    public void pin(ServerPlayer player) { var s = sessions.get(player.getUUID()); if (s != null && s.player == player) { pin(s); } }
    static void pin(Session s) { if (s.online) { pin(s.player, s.level, s.position); } }
    static void pin(ServerPlayer player, ServerLevel level, Vec3 position) {
        player.stopRiding();
        if (player.connection != null && (player.level() != level || !player.position().equals(position))) {
            player.teleportTo(level, position.x, position.y, position.z, player.getYRot(), player.getXRot());
        } else { player.setPos(position); }
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0; player.clearFire(); player.setTicksFrozen(0);
    }
    public void logout(ServerPlayer player) {
        var s = sessions.get(player.getUUID());
        if (s != null && s.player == player) { pin(s); s.online = false; placement.logout(s); }
    }
    public void saved(ServerPlayer player) { placement.saved(player); }
    public void invalidate(ServerPlayer player) {
        var s = sessions.get(player.getUUID());
        if (s != null && s.player == player) { s.invalidated = true; placement.cancel(s); reason(s, "dead"); }
    }
    void complete(Session s) {
        if (sessions.get(s.player.getUUID()) != s) { return; }
        pin(s); cleanup(s.player, s.originalEffects); placement.cancel(s); sessions.remove(s.player.getUUID());
    }
    static void cleanup(ServerPlayer player, Set<MobEffect> original) {
        player.clearFire(); player.setTicksFrozen(0);
        for (var effect : Set.copyOf(player.getActiveEffectsMap().keySet())) {
            if (effect.getCategory() == MobEffectCategory.HARMFUL && !original.contains(effect)) { player.removeEffect(effect); }
        }
    }
    public void close() { sessions.values().forEach(RocketRejoinGuards::pin); placement.close(); sessions.clear(); }
    private static void log(Session s, String state) {
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_REJOIN_GUARD player={} state={} reason={}", s.player.getUUID(), state, s.reason);
    }
    private static Component message(String reason, boolean disconnect) {
        return Component.translatable("message.advancedrocketrycommunity.rejoin." + (disconnect ? "disconnect" : "guard"),
                Component.translatable("message.advancedrocketrycommunity.rejoin." + reason));
    }
}
