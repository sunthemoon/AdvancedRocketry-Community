package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;

/**
 * The release-test gate's real inputs for a unit test, built as {@code ReleaseTestPlayersWiringTest} builds them: a
 * dedicated server owned by the calling thread with real server properties, a real {@link DedicatedPlayerList}, and
 * players whose real game listeners run on real connections over embedded channels. Nothing loads a world.
 */
public final class ReleaseTestServers implements AutoCloseable {
    private final List<EmbeddedChannel> channels = new ArrayList<>();

    /** A dedicated server with {@code enable-rcon} as given and an empty player list. */
    public DedicatedServer server(Path directory, boolean rcon) throws IOException {
        Path properties = Files.writeString(directory.resolve(rcon + "-" + UUID.randomUUID() + "-server.properties"),
                "enable-rcon=" + rcon + "\nrcon.password=gate-test\nenforce-secure-profile=false\n");
        var created = UnstartedServer.owned();
        UnstartedServer.set(DedicatedServer.class, created, "settings", new DedicatedServerSettings(properties));
        var players = UnstartedServer.allocate(DedicatedPlayerList.class);
        var joined = new ArrayList<ServerPlayer>();
        UnstartedServer.set(PlayerList.class, players, "server", created);
        UnstartedServer.set(PlayerList.class, players, "players", joined);
        UnstartedServer.set(PlayerList.class, players, "playersView", java.util.Collections.unmodifiableList(joined));
        UnstartedServer.set(PlayerList.class, players, "playersByUUID", new java.util.HashMap<UUID, ServerPlayer>());
        UnstartedServer.set(MinecraftServer.class, created, "playerList", players);
        return created;
    }

    /** A connected player that no release-test facility created: the gate must treat it as foreign. */
    public ServerPlayer join(MinecraftServer owner, UUID id) {
        var player = UnstartedServer.allocate(ServerPlayer.class);
        java.util.concurrent.atomic.AtomicInteger counter =
                UnstartedServer.get(net.minecraft.world.entity.Entity.class, null, "ENTITY_COUNTER");
        UnstartedServer.set(net.minecraft.world.entity.Entity.class, player, "id", counter.incrementAndGet());
        UnstartedServer.set(net.minecraft.world.entity.Entity.class, player, "uuid", id);
        UnstartedServer.set(ServerPlayer.class, player, "textFilter", TextFilter.DUMMY);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        new ServerGamePacketListenerImpl(owner, connection, player);
        List<ServerPlayer> players = UnstartedServer.get(PlayerList.class, owner.getPlayerList(), "players");
        players.add(player);
        Map<UUID, ServerPlayer> byId = UnstartedServer.get(PlayerList.class, owner.getPlayerList(), "playersByUUID");
        byId.put(id, player);
        return player;
    }

    @Override
    public void close() { channels.forEach(EmbeddedChannel::finishAndReleaseAll); }
}
