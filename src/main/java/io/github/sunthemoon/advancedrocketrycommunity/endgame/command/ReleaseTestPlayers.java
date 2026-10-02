package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Release-test players for the C13 packaged-server hooks: connected players with an embedded channel instead of a
 * client, so the server treats them as real (menus, intents, rides, field lookups). {@link #pump} ticks them, drains
 * what the server sends them and answers keep-alives, so a long run neither times them out nor buffers their packets.
 * They play in creative mode: drill owners and riders stand in vacuum, and nothing in the endgame systems depends on
 * the game mode.
 */
final class ReleaseTestPlayers {
    private static final Map<UUID, EmbeddedChannel> CHANNELS = new HashMap<>();

    private ReleaseTestPlayers() {
    }

    /** The player with this ID at {@code at} in {@code level}, joining it first when it is offline. */
    static ServerPlayer place(MinecraftServer server, UUID id, String name, ServerLevel level, Vec3 at) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            player = new ServerPlayer(server, level, new GameProfile(id, name));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            CHANNELS.put(id, new EmbeddedChannel(connection));
            server.getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(GameType.CREATIVE);
        }
        player.teleportTo(level, at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /**
     * Ticks every test player as the server ticks a connected one, then drains its outbound packets and answers
     * keep-alives; forgets players who left. The server ticks only the connections it accepted, so without this a test
     * player would never run its player tick, nor the gravity-field lookup in it.
     */
    static void pump(MinecraftServer server) {
        Iterator<Map.Entry<UUID, EmbeddedChannel>> entries = CHANNELS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, EmbeddedChannel> entry = entries.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                entries.remove();
                continue;
            }
            player.connection.tick();
            Object message;
            while ((message = entry.getValue().readOutbound()) != null) {
                if (message instanceof ClientboundKeepAlivePacket keepAlive) {
                    player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
            }
        }
    }
}
