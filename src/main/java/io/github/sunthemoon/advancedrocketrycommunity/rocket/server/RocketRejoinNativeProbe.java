package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

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
    boolean advance(RocketRejoinSavedData data) {
        if (data.root().nextSequence() == 0) { return false; }
        if (player == null) {
            RocketRejoinNativeCuts.cleanFixtureBackup(server, PLAYER);
            for (var batch : RocketReleaseNativeCuts.selection().endsWith(":seed") ? data.root().batches() : java.util.List.<RocketRejoinRoot.Batch>of()) {
                var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        new net.minecraft.resources.ResourceLocation(batch.dimension())));
                if (level == null) { continue; }
                var safe = new BlockPos(batch.minimum().x() + (batch.maximum().x() - batch.minimum().x()) / 2,
                        batch.maximum().y() + 8, batch.minimum().z() - 1);
                if (!level.hasChunkAt(safe)) { continue; }
                for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) { for (int y = -1; y <= 2; y++) {
                    level.setBlockAndUpdate(safe.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                } } }
            }
            if (RocketReleaseNativeCuts.selection().endsWith(":seed") && !server.saveEverything(false, true, true)) {
                throw new IllegalStateException("Native placement terrain was not saved");
            }
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
