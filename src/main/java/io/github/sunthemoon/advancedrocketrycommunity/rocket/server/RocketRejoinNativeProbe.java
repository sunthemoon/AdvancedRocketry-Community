package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Connected dedicated-server passenger for W158's real, gated admission and the W159 crash matrix. */
final class RocketRejoinNativeProbe {
    static final UUID PLAYER = UUID.fromString("01580000-0000-4000-8000-000000000002");
    private final MinecraftServer server;
    private ServerPlayer player;
    private boolean seeded;
    private int changedAt = -1;
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
            if (!player.isAlive() && RocketReleaseNativeCuts.selection().matches("H[12]:C18:(recover|check)")) {
                player = ReleaseTestPlayers.respawn(server, player);
            }
        }
        if (RocketRejoinNativeCuts.failedLeaseSeed() && RocketRejoinNativeCuts.heldLease()) {
            if (changedAt < 0) {
                if (!RocketRejoinProtection.guarded(player)) { throw new IllegalStateException("Held P2 player is unguarded"); }
                if (RocketReleaseNativeCuts.selection().contains(":C18:")) {
                    player.setHealth(0);
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(player, player.damageSources().generic()));
                } else {
                    player.serverLevel().setBlockAndUpdate(player.blockPosition().below(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
                server.getPlayerList().saveAll(); server.saveEverything(false, true, true);
                changedAt = server.getTickCount();
            } else if (server.getTickCount() > changedAt + 1) { RocketRejoinNativeCuts.releaseLease(server); }
            return false;
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
        return ReleaseTestPlayers.login(server, PLAYER, "W159Passenger", server.overworld());
    }
}
