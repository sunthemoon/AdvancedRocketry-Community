package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import io.netty.channel.embedded.EmbeddedChannel;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Review B F8: R16's gate wiring over a real {@link DedicatedPlayerList}, real {@link Connection}s on embedded channels with
 * real game listeners, and real dedicated-server properties. The server, player list, Level and player objects are
 * allocated without their world-loading constructors; every method under test runs unchanged.
 */
class ReleaseTestPlayersWiringTest {
    private static final String HOOKS = EscrowTestEndpoints.HOOKS;
    @TempDir Path directory;
    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private DedicatedServer server;
    private String hooks;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @AfterEach void restore() {
        if (server != null) { ReleaseTestPlayers.clear(server); }
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        if (hooks == null) { System.clearProperty(HOOKS); } else { System.setProperty(HOOKS, hooks); }
    }

    private void enableHooks() { hooks = System.getProperty(HOOKS); System.setProperty(HOOKS, "true"); }

    private DedicatedServer server(boolean rcon) throws Exception {
        Path properties = Files.writeString(directory.resolve(rcon + "-server.properties"),
                "enable-rcon=" + rcon + "\nrcon.password=wiring-test\nenforce-secure-profile=false\n");
        var created = UnstartedServer.owned();
        set(DedicatedServer.class, created, "settings", new DedicatedServerSettings(properties));
        // The constructor would initialize the data-pack registry loader, which needs a started server. The player
        // collections it creates are all that the methods under test read.
        var players = UnstartedServer.allocate(DedicatedPlayerList.class); var joined = new ArrayList<ServerPlayer>();
        set(PlayerList.class, players, "server", created); set(PlayerList.class, players, "players", joined);
        set(PlayerList.class, players, "playersView", java.util.Collections.unmodifiableList(joined));
        set(PlayerList.class, players, "playersByUUID", new java.util.HashMap<UUID, ServerPlayer>());
        set(MinecraftServer.class, created, "playerList", players);
        assertTrue(created.isSameThread() && created.isDedicatedServer());
        assertEquals(rcon, created.getProperties().enableRcon);
        return created;
    }

    private static void set(Class<?> owner, Object target, String name, Object value) { UnstartedServer.set(owner, target, name, value); }
    private static <T> T get(Class<?> owner, Object target, String name) { return UnstartedServer.get(owner, target, name); }

    /** A player whose real game listener runs on a real connection over an embedded channel, then joins the list. */
    private ServerPlayer join(MinecraftServer owner, UUID id) throws Exception {
        var player = UnstartedServer.allocate(ServerPlayer.class);
        // Entity equality is its numeric ID, which the skipped constructor draws from the shared counter.
        java.util.concurrent.atomic.AtomicInteger counter = get(net.minecraft.world.entity.Entity.class, null, "ENTITY_COUNTER");
        set(net.minecraft.world.entity.Entity.class, player, "id", counter.incrementAndGet());
        set(net.minecraft.world.entity.Entity.class, player, "uuid", id);
        set(ServerPlayer.class, player, "textFilter", TextFilter.DUMMY);
        connect(owner, player); add(owner, player);
        return player;
    }

    private EmbeddedChannel connect(MinecraftServer owner, ServerPlayer player) {
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
        channels.add(channel);
        new ServerGamePacketListenerImpl(owner, connection, player);
        assertSame(connection, player.connection.connection);
        return channel;
    }

    private static void add(MinecraftServer owner, ServerPlayer player) throws ReflectiveOperationException {
        List<ServerPlayer> players = get(PlayerList.class, owner.getPlayerList(), "players"); players.add(player);
        Map<UUID, ServerPlayer> byId = get(PlayerList.class, owner.getPlayerList(), "playersByUUID"); byId.put(player.getUUID(), player);
    }

    private static void leave(MinecraftServer owner, ServerPlayer player) throws ReflectiveOperationException {
        List<ServerPlayer> players = get(PlayerList.class, owner.getPlayerList(), "players"); players.remove(player);
        Map<UUID, ServerPlayer> byId = get(PlayerList.class, owner.getPlayerList(), "playersByUUID"); byId.remove(player.getUUID());
    }

    private EmbeddedChannel own(ServerPlayer player) {
        var channel = channels.get(channels.size() - 1);
        ReleaseTestPlayers.own(server, player, channel);
        return channel;
    }

    @Test void onlyThisServersExactFacilityConnectionsAreOwnedAcrossTheRealPlayerList() throws Exception {
        server = server(false);
        var first = join(server, UUID.randomUUID()); var firstChannel = own(first);
        var second = join(server, UUID.randomUUID()); own(second);
        assertFalse(ReleaseTestPlayers.ownsAllConnectedPlayers(server), "Hooks off still granted ownership");
        enableHooks();
        assertEquals(2, server.getPlayerList().getPlayers().size());
        assertTrue(ReleaseTestPlayers.ownsAllConnectedPlayers(server));

        var foreign = join(server, UUID.randomUUID());
        assertFalse(ReleaseTestPlayers.ownsAllConnectedPlayers(server), "A connection the facility did not create was accepted");
        var other = server(false); add(other, first);
        assertFalse(ReleaseTestPlayers.ownsAllConnectedPlayers(other), "Another server inherited this server's facility player");
        leave(server, foreign);
        assertTrue(ReleaseTestPlayers.ownsAllConnectedPlayers(server));

        connect(server, second); // A reconnect replaces the owned listener on the same player instance.
        assertFalse(ReleaseTestPlayers.ownsAllConnectedPlayers(server), "A replaced connection kept its fixture authority");
        leave(server, second);
        assertTrue(ReleaseTestPlayers.ownsAllConnectedPlayers(server));
        firstChannel.close();
        assertFalse(ReleaseTestPlayers.ownsAllConnectedPlayers(server), "A closed channel kept its fixture authority");

        var thread = new Thread(() -> assertFalse(ReleaseTestPlayers.ownsAllConnectedPlayers(server)), "off-thread-gate");
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        thread.setUncaughtExceptionHandler((ignored, thrown) -> failure.set(thrown)); thread.start(); thread.join();
        assertNull(failure.get(), "The gate answered off the server thread");
    }

    @Test void theDedicatedEscrowGateRefusesRconAndAnyForeignConnection() throws Exception {
        server = server(false);
        own(join(server, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> EscrowTestEndpoints.requireEnabled(server), "Hooks off enabled fixtures");
        enableHooks();
        assertDoesNotThrow(() -> EscrowTestEndpoints.requireEnabled(server));
        var foreign = join(server, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> EscrowTestEndpoints.requireEnabled(server), "A foreign player enabled fixtures");
        leave(server, foreign);
        assertDoesNotThrow(() -> EscrowTestEndpoints.requireEnabled(server));

        var rcon = server(true);
        ReleaseTestPlayers.own(rcon, join(rcon, UUID.randomUUID()), channels.get(channels.size() - 1));
        try {
            assertTrue(ReleaseTestPlayers.ownsAllConnectedPlayers(rcon), "Only the RCON setting may differ");
            assertThrows(IllegalArgumentException.class, () -> EscrowTestEndpoints.requireEnabled(rcon), "RCON enabled fixtures");
        } finally { ReleaseTestPlayers.clear(rcon); }
    }

    @Test void placeRefusesToAdoptAForeignOrReplacedConnection() throws Exception {
        server = server(false); enableHooks();
        var level = UnstartedServer.allocate(ServerLevel.class); set(ServerLevel.class, level, "server", server);
        var foreign = join(server, UUID.randomUUID());
        var refused = assertThrows(IllegalStateException.class,
                () -> ReleaseTestPlayers.place(server, foreign.getUUID(), "foreign", level, Vec3.ZERO));
        assertTrue(refused.getMessage().contains("Cannot adopt"), refused.getMessage());
        assertFalse(ReleaseTestPlayers.ownsAllConnectedPlayers(server), "The refused place adopted the connection");

        leave(server, foreign);
        var owned = join(server, UUID.randomUUID()); own(owned);
        assertTrue(ReleaseTestPlayers.ownsAllConnectedPlayers(server));
        connect(server, owned);
        assertThrows(IllegalStateException.class, () -> ReleaseTestPlayers.place(server, owned.getUUID(), "owned", level, Vec3.ZERO),
                "Place adopted a replacement connection of an owned player");
        var otherServer = server(false);
        assertThrows(IllegalArgumentException.class, () -> ReleaseTestPlayers.place(otherServer, UUID.randomUUID(), "x", level, Vec3.ZERO),
                "Place used another server's Level");
        System.clearProperty(HOOKS);
        assertThrows(IllegalStateException.class, () -> ReleaseTestPlayers.place(server, owned.getUUID(), "owned", level, Vec3.ZERO));
    }

    @Test void pumpDropsStaleOwnershipSoAReturningPlayerIsNotReadopted() throws Exception {
        server = server(false); enableHooks();
        var departed = join(server, UUID.randomUUID()); var departedChannel = own(departed);
        var replaced = join(server, UUID.randomUUID()); var replacedChannel = own(replaced);
        assertTrue(ReleaseTestPlayers.ownsAllConnectedPlayers(server));
        leave(server, departed); connect(server, replaced);
        // Both entries are stale, so pump drops them without ticking either listener.
        ReleaseTestPlayers.pump(server);
        assertFalse(departedChannel.isOpen() || replacedChannel.isOpen(), "Pump kept a stale facility channel open");
        leave(server, replaced);
        assertTrue(ReleaseTestPlayers.ownsAllConnectedPlayers(server), "An empty player list is owned");
        add(server, departed);
        assertFalse(ReleaseTestPlayers.ownsAllConnectedPlayers(server), "A returning player re-acquired dropped ownership");
        System.clearProperty(HOOKS);
        assertThrows(IllegalStateException.class, () -> ReleaseTestPlayers.pump(server));
    }
}
