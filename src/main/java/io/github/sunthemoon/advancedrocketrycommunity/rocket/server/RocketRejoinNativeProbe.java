package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Connected dedicated-server passenger for W158's real, gated admission and the W159 crash matrix. */
final class RocketRejoinNativeProbe {
    static final UUID PLAYER = UUID.fromString("01580000-0000-4000-8000-000000000002");
    private final MinecraftServer server;
    private ServerPlayer player;
    private boolean seeded;
    RocketRejoinNativeProbe(MinecraftServer server) { this.server = server; RocketReleaseNativeCuts.validate(server); }
    void seedOldPlayer(RocketEntity rocket) {
        if (seeded) { return; }
        var old = connect(); old.setHealth(1); old.teleportTo((net.minecraft.server.level.ServerLevel) rocket.level(), rocket.getX(), rocket.getY(), rocket.getZ(), 0, 0);
        if (!old.startRiding(rocket, true)) { throw new IllegalStateException("Seed passenger could not mount old seat"); }
        server.getPlayerList().saveAll(); server.getPlayerList().remove(old); seeded = true;
    }
    void admitted() {
        if (player != null) { return; }
        player = connect();
        if (!RocketRejoinProtection.guarded(player)) { throw new IllegalStateException("B0 login was not guarded"); }
    }
    boolean advance(RocketRejoinSavedData data) {
        if (data.root().nextSequence() == 0) { return false; }
        if (player == null) {
            RocketRejoinNativeCuts.cleanFixtureBackup(server, PLAYER);
            player = connect();
        }
        if (!player.isAlive() || player.getHealth() <= 0) { throw new IllegalStateException("Native guarded passenger died"); }
        if (data.root().entries().stream().anyMatch(e -> e.player().equals(PLAYER) && e.placement() == RocketRejoinRoot.Placement.PENDING)
                || RocketRejoinProtection.guarded(player)) { return false; }
        if (player.getVehicle() != null || player.getPersistentData().contains("arce_passenger_rejoin")) {
            throw new IllegalStateException("Completed rejoin kept a vehicle or stale marker");
        }
        return data.acknowledgedRoot() != null && data.acknowledgedRoot().entries().stream().noneMatch(e -> e.seat() == RocketRejoinRoot.Seat.PENDING);
    }
    private ServerPlayer connect() {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(PLAYER, "W159Passenger"));
        var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player); return player;
    }
}
