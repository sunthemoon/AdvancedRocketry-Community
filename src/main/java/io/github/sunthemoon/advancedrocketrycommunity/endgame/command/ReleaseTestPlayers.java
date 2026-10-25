package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.IdentityHashMap;
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
 * the game mode. Their channels belong to one server: {@link #clear} forgets them when it stops (review C13-F8).
 */
public final class ReleaseTestPlayers {
    private static final String HOOKS = "advancedrocketrycommunity.releaseTestHooks";
    private static final Map<MinecraftServer, Map<ServerPlayer, OwnedChannel>> CHANNELS = new IdentityHashMap<>();

    private ReleaseTestPlayers() {
    }

    /** The player with this ID at {@code at} in {@code level}, joining it first when it is offline. */
    public static ServerPlayer place(MinecraftServer server, UUID id, String name, ServerLevel level, Vec3 at) {
        requireEnabled(server);
        boolean offline = server.getPlayerList().getPlayer(id) == null;
        var player = login(server, id, name, level);
        if (offline) { player.setGameMode(GameType.CREATIVE); }
        player.teleportTo(level, at.x, at.y, at.z, 0.0F, 0.0F);
        return player;
    }

    /** Native login without a fixture teleport or game-mode change, for exact installed-player-file tests. */
    public static ServerPlayer login(MinecraftServer server, UUID id, String name, ServerLevel level) {
        requireEnabled(server);
        if (level.getServer() != server) { throw new IllegalArgumentException("Foreign test-player Level"); }
        var channels = CHANNELS.computeIfAbsent(server, ignored -> new IdentityHashMap<>());
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            player = new ServerPlayer(server, level, new GameProfile(id, name));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            try {
                server.getPlayerList().placeNewPlayer(connection, player);
                own(server, player, channel);
            } catch (RuntimeException failed) { channel.finishAndReleaseAll(); throw failed; }
        } else if (!owned(channels, player, player.connection)) {
            throw new IllegalStateException("Cannot adopt a connection not created by the release-test facility");
        }
        return player;
    }

    /**
     * Ticks every test player as the server ticks a connected one, then drains its outbound packets and answers
     * keep-alives; forgets players who left. The server ticks only the connections it accepted, so without this a test
     * player would never run its player tick, nor the gravity-field lookup in it.
     */
    public static void pump(MinecraftServer server) {
        requireEnabled(server);
        var channels = CHANNELS.get(server);
        if (channels == null) { return; }
        Iterator<Map.Entry<ServerPlayer, OwnedChannel>> entries = channels.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            ServerPlayer player = entry.getKey();
            if (server.getPlayerList().getPlayer(player.getUUID()) != player
                    || !owned(channels, player, player.connection)) {
                entry.getValue().channel.finishAndReleaseAll();
                entries.remove();
                continue;
            }
            player.connection.tick();
            Object message;
            while ((message = entry.getValue().channel.readOutbound()) != null) {
                if (message instanceof ClientboundKeepAlivePacket keepAlive) {
                    player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
                io.netty.util.ReferenceCountUtil.release(message);
            }
        }
    }

    /** Only this server's exact facility-created player/connection pairs may enter escrow fixtures. */
    public static boolean ownsAllConnectedPlayers(MinecraftServer server) {
        if (!Boolean.getBoolean(HOOKS) || !server.isSameThread()) { return false; }
        var channels = CHANNELS.get(server);
        return server.getPlayerList().getPlayers().stream().allMatch(player ->
                owned(channels, player, player.connection));
    }

    /** Records the exact player, connection and channel that {@link #place} created; the wiring test uses it too. */
    static void own(MinecraftServer server, ServerPlayer player, EmbeddedChannel channel) {
        CHANNELS.computeIfAbsent(server, ignored -> new IdentityHashMap<>()).put(player, new OwnedChannel(player.connection, channel));
    }

    // Identity, not UUID/name/equality, prevents a later login from inheriting fixture authority.
    static boolean owned(Map<?, OwnedChannel> channels, Object player, Object connection) {
        var entry = channels == null ? null : channels.get(player);
        return entry != null && entry.connection == connection && entry.channel.isOpen();
    }

    static final class OwnedChannel {
        final Object connection;
        final EmbeddedChannel channel;
        OwnedChannel(Object connection, EmbeddedChannel channel) { this.connection = connection; this.channel = channel; }
    }

    private static void requireEnabled(MinecraftServer server) {
        if (!Boolean.getBoolean(HOOKS) || !server.isSameThread()) {
            throw new IllegalStateException("Release-test players require enabled hooks and the server thread");
        }
    }

    /** At server stop: the channels reach that server's players and Levels. */
    static void clear(MinecraftServer server) {
        var channels = CHANNELS.remove(server);
        if (channels != null) { channels.values().forEach(owned -> owned.channel.finishAndReleaseAll()); }
    }
}
