package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.ReleaseTestServers;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

final class BayV2CampaignGateTest {
    private static final String PROPERTY = "advancedrocketrycommunity.bayV2CampaignPlayers";
    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000269001");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-000000269002");
    private static final String CLIENTS = A + "," + B;
    @TempDir Path directory;
    private final ReleaseTestServers servers = new ReleaseTestServers();
    private final Map<String, String> saved = new HashMap<>();
    private final List<MinecraftServer> started = new ArrayList<>();
    private final List<LevelStorageSource.LevelStorageAccess> worlds = new ArrayList<>();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @AfterEach void restore() throws Exception {
        started.forEach(server -> BayV2Campaign.stopped(new ServerStoppedEvent(server)));
        for (var world : worlds) { world.close(); }
        servers.close();
        saved.forEach((key, value) -> { if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); } });
    }

    private MinecraftServer campaign(boolean rcon, String marker) throws Exception {
        return campaignWithMarker(rcon, world -> {
            if (marker != null) { Files.writeString(world.resolve(BayV2Campaign.MARKER), marker); }
        });
    }

    @FunctionalInterface private interface MarkerSetup { void create(Path world) throws IOException; }

    private MinecraftServer campaignWithMarker(boolean rcon, MarkerSetup setup) throws Exception {
        var server = servers.server(directory, rcon);
        var world = LevelStorageSource.createDefault(directory).createAccess("world-" + worlds.size());
        worlds.add(world); started.add(server);
        UnstartedServer.set(MinecraftServer.class, server, "storageSource", world);
        setup.create(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
        BayV2Campaign.starting(new ServerAboutToStartEvent(server));
        return server;
    }

    @Test void sameLengthWrongMarkerBytesCloseTheGateAndLogUnmarked() throws Exception {
        String wrong = BayV2Campaign.MARKER_TEXT.replace("_V1", "_V2");
        assertEquals(BayV2Campaign.MARKER_TEXT.length(), wrong.length());
        assertUnmarked(world -> Files.writeString(world.resolve(BayV2Campaign.MARKER), wrong));
    }

    @Test void aDirectoryNamedAsTheMarkerClosesTheGateAndLogsUnmarked() throws Exception {
        assertUnmarked(world -> Files.createDirectory(world.resolve(BayV2Campaign.MARKER)));
    }

    @Test void aSymbolicLinkToCorrectMarkerBytesClosesTheGateAndLogsUnmarked() throws Exception {
        Path target = Files.writeString(directory.resolve("correct-marker"), BayV2Campaign.MARKER_TEXT);
        Path probe = directory.resolve("symlink-capability-probe");
        try { Files.createSymbolicLink(probe, target); }
        catch (UnsupportedOperationException | IOException | SecurityException unsupported) {
            Assumptions.abort("Platform cannot create symbolic links: " + unsupported);
        }
        Files.delete(probe);
        assertUnmarked(world -> Files.createSymbolicLink(world.resolve(BayV2Campaign.MARKER), target));
    }

    private void assertUnmarked(MarkerSetup setup) throws Exception {
        property(PROPERTY, CLIENTS); property(EscrowTestEndpoints.HOOKS, "true");
        try (var logs = new AuditLines()) {
            var server = campaignWithMarker(false, setup);
            for (UUID id : List.of(A, B)) { connected(server, id); }
            assertFalse(EscrowTestEndpoints.enabled(server));
            String fields = "property=" + PROPERTY + " clients=" + CLIENTS + " world="
                    + server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize() + " marker=false";
            assertEquals(List.of("INFO ARCE_BAY_V2_CAMPAIGN startup " + fields,
                    "INFO ARCE_BAY_V2_CAMPAIGN connect " + fields + " player=" + A,
                    "INFO ARCE_BAY_V2_CAMPAIGN connect " + fields + " player=" + B), logs.lines);
        }
    }

    private void connected(MinecraftServer server, UUID id) {
        var player = servers.join(server, id);
        var level = UnstartedServer.allocate(net.minecraft.server.level.ServerLevel.class);
        UnstartedServer.set(net.minecraft.server.level.ServerLevel.class, level, "server", server);
        UnstartedServer.set(net.minecraft.world.entity.Entity.class, player, "level", level);
        BayV2Campaign.connected(new PlayerEvent.PlayerLoggedInEvent(player));
    }

    private void named(MinecraftServer server) {
        servers.join(server, A); servers.join(server, B);
    }
    private void property(String key, String value) {
        if (!saved.containsKey(key)) { saved.put(key, System.getProperty(key)); }
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }

    @Test void theMarkedCampaignAllowsExactlyItsTwoNamedConnections() throws Exception {
        property(EscrowTestEndpoints.HOOKS, "true"); property(PROPERTY, CLIENTS);
        var server = campaign(false, BayV2Campaign.MARKER_TEXT);
        named(server);
        // A real facility-owned pair remains allowed alongside both named connections, without adopting either.
        var owned = servers.join(server, UUID.randomUUID());
        var own = io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers.class.getDeclaredMethod(
                "own", MinecraftServer.class, net.minecraft.server.level.ServerPlayer.class, io.netty.channel.embedded.EmbeddedChannel.class);
        own.setAccessible(true);
        io.netty.channel.embedded.EmbeddedChannel channel = UnstartedServer.get(net.minecraft.network.Connection.class,
                owned.connection.connection, "channel");
        try {
            own.invoke(null, server, owned, channel);
            assertTrue(EscrowTestEndpoints.enabled(server));
            assertFalse(io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers.ownsAllConnectedPlayers(server));
            servers.join(server, UUID.randomUUID());
            assertFalse(EscrowTestEndpoints.enabled(server));
        } finally {
            var clear = own.getDeclaringClass().getDeclaredMethod("clear", MinecraftServer.class);
            clear.setAccessible(true); clear.invoke(null, server); channel.finishAndReleaseAll();
        }
    }

    @Test void unsetPreservesTheEmptyAndForeignConnectionControlsWithoutMarkerAccess() throws Exception {
        property(PROPERTY, null); property(EscrowTestEndpoints.HOOKS, "true");
        var server = servers.server(directory, false); // No storage source: unset must not read the world.
        BayV2Campaign.starting(new ServerAboutToStartEvent(server));
        assertTrue(EscrowTestEndpoints.enabled(server));
        named(server); assertFalse(EscrowTestEndpoints.enabled(server));
    }

    @Test void theStrictPolicyDoesNotInspectCampaignMembershipForAnUnownedPlayerWithNoUuid() throws Exception {
        property(PROPERTY, null); property(EscrowTestEndpoints.HOOKS, "true");
        var server = servers.server(directory, false);
        var player = servers.join(server, UUID.randomUUID());
        UnstartedServer.set(net.minecraft.world.entity.Entity.class, player, "uuid", null);
        assertFalse(io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers.ownsAllConnectedPlayers(server));
    }

    @Test void hooksRconMarkerAndOwnerThreadAreAllRequiredForNamedClients() throws Exception {
        property(PROPERTY, CLIENTS); property(EscrowTestEndpoints.HOOKS, "true");
        var server = campaign(false, BayV2Campaign.MARKER_TEXT); named(server);
        assertTrue(EscrowTestEndpoints.enabled(server));
        property(EscrowTestEndpoints.HOOKS, "false"); assertFalse(EscrowTestEndpoints.enabled(server));
        property(EscrowTestEndpoints.HOOKS, "true");
        UnstartedServer.set(MinecraftServer.class, server, "serverThread", new Thread());
        assertFalse(EscrowTestEndpoints.enabled(server));
        for (var closed : List.of(campaign(true, BayV2Campaign.MARKER_TEXT), campaign(false, null),
                campaign(false, ""), campaign(false, BayV2Campaign.MARKER_TEXT + "extra"))) {
            named(closed); assertFalse(EscrowTestEndpoints.enabled(closed));
        }
    }

    @Test void malformedSelectionsRefuseStartupEvenWithHooksOffAndDoNotEchoTheInput() {
        property(EscrowTestEndpoints.HOOKS, "false");
        for (String invalid : List.of("", A.toString(), A + "," + A, CLIENTS + "," + UUID.randomUUID(),
                " " + CLIENTS, CLIENTS + ",", "1-1-1-1-1," + B, "not-a-uuid," + B, "x".repeat(10000))) {
            property(PROPERTY, invalid);
            var error = assertThrows(IllegalArgumentException.class,
                    () -> BayV2Campaign.starting(new ServerAboutToStartEvent(UnstartedServer.owned())));
            assertTrue(error.getMessage().startsWith(PROPERTY + " requires exactly two distinct canonical UUIDs"));
            assertTrue(error.getMessage().length() < 200);
        }
        assertEquals(java.util.Set.of(A, B), BayV2Campaign.parse(CLIENTS.toUpperCase(java.util.Locale.ROOT)));
    }

    @Test void gameTestAndNonDedicatedServersIgnoreEvenMalformedSelection() {
        property(PROPERTY, "malformed"); property(EscrowTestEndpoints.HOOKS, "false");
        var gameTest = UnstartedServer.allocate(GameTestServer.class);
        UnstartedServer.set(MinecraftServer.class, gameTest, "serverThread", Thread.currentThread());
        assertDoesNotThrow(() -> BayV2Campaign.starting(new ServerAboutToStartEvent(gameTest)));
        assertTrue(EscrowTestEndpoints.enabled(gameTest));
        var integrated = UnstartedServer.allocate(net.minecraft.client.server.IntegratedServer.class);
        UnstartedServer.set(MinecraftServer.class, integrated, "serverThread", Thread.currentThread());
        assertDoesNotThrow(() -> BayV2Campaign.starting(new ServerAboutToStartEvent(integrated)));
        assertFalse(EscrowTestEndpoints.enabled(integrated));
    }

    @Test void selectionIsStartupBoundServerLocalAndClearedAtStop() throws Exception {
        property(PROPERTY, CLIENTS); property(EscrowTestEndpoints.HOOKS, "true");
        var selected = campaign(false, BayV2Campaign.MARKER_TEXT); named(selected);
        var other = servers.server(directory, false); named(other);
        assertFalse(EscrowTestEndpoints.enabled(other));
        property(PROPERTY, "malformed"); assertTrue(EscrowTestEndpoints.enabled(selected));
        BayV2Campaign.stopped(new ServerStoppedEvent(selected));
        assertFalse(EscrowTestEndpoints.enabled(selected));
    }

    @Test void infoLinesNameTheSelectionWorldAndEachNamedLoginAndUnsetIsSilent() throws Exception {
        property(PROPERTY, null); property(EscrowTestEndpoints.HOOKS, "true");
        try (var logs = new AuditLines()) {
            campaign(false, null); assertEquals(List.of(), logs.lines);
            property(PROPERTY, CLIENTS);
            var server = campaign(false, BayV2Campaign.MARKER_TEXT);
            String fields = "property=" + PROPERTY + " clients=" + CLIENTS + " world="
                    + server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize() + " marker=true";
            assertEquals(List.of("INFO ARCE_BAY_V2_CAMPAIGN startup " + fields), logs.lines);
            for (UUID id : List.of(A, B, UUID.randomUUID())) {
                connected(server, id);
            }
            assertEquals(List.of("INFO ARCE_BAY_V2_CAMPAIGN startup " + fields,
                    "INFO ARCE_BAY_V2_CAMPAIGN connect " + fields + " player=" + A,
                    "INFO ARCE_BAY_V2_CAMPAIGN connect " + fields + " player=" + B), logs.lines);
        }
    }

    private static final class AuditLines extends org.apache.logging.log4j.core.appender.AbstractAppender implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger root =
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        private final List<String> lines = new java.util.concurrent.CopyOnWriteArrayList<>();
        AuditLines() {
            super("bay-v2-campaign-audit", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
            start(); root.addAppender(this);
        }
        @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
            String line = event.getMessage().getFormattedMessage();
            if (line.startsWith("ARCE_BAY_V2_CAMPAIGN ")) { lines.add(event.getLevel() + " " + line); }
        }
        @Override public void close() { root.removeAppender(this); stop(); }
    }
}
