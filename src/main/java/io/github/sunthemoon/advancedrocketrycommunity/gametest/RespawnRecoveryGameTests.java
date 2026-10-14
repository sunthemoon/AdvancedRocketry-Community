package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SleepRespawnService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R2: a replacement installed before an interrupted event retains a native-saveable recovery record. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RespawnRecoveryGameTests {
    private static final BlockPos FAR = new BlockPos(25_000_000, 80, 25_000_000);
    private static final String INTERRUPTED = "W37F injected respawn listener interruption";

    private RespawnRecoveryGameTests() { }

    @GameTest(template = "atmosphere_test", batch = "respawn_recovery", timeoutTicks = 40)
    public static void anEarlierThrowingListenerLeavesABackupForLaterTicks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, UUID.randomUUID(), "W37FInterrupted");
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, -72.25F, true, false);
        Consumer<PlayerEvent.PlayerRespawnEvent> interrupt = event -> {
            if (event.getEntity().getUUID().equals(player.getUUID())) { throw new IllegalStateException(INTERRUPTED); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerEvent.PlayerRespawnEvent.class, interrupt);
        try {
            try {
                server.getPlayerList().respawn(player, false);
                helper.fail("The injected respawn listener did not interrupt dispatch");
            } catch (IllegalStateException expected) {
                helper.assertTrue(INTERRUPTED.equals(expected.getMessage()), "Unexpected exception: " + expected);
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(interrupt);
        }
        ServerPlayer replacement = server.getPlayerList().getPlayer(player.getUUID());
        helper.assertTrue(replacement != null && replacement != player && replacement.getRespawnPosition() == null
                        && hasBackup(replacement), "Interrupted respawn lost its persisted backup before retry");
        // Do not repair connection.player: the server-tick retry must also handle this native interruption window.
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            if (replacement.getRespawnPosition() == null) { return; }
            try {
                assertTuple(helper, replacement);
                helper.assertTrue(hasBackup(replacement) && replacement.connection.player == player,
                        "Retry deleted recovery while the native connection still owned the cleared old player");
                done[0] = true;
                helper.succeed();
            } finally {
                server.getPlayerList().remove(replacement);
            }
        });
    }

    /**
     * W37I: in the interrupted-listener window above, the PlayerList holds the replacement, which commands select, but
     * a disconnect saves the connection's player, the old one. A newer point set on the replacement survives that save.
     */
    @GameTest(template = "atmosphere_test", batch = "respawn_recovery", timeoutTicks = 40)
    public static void aNewerPointOnAnUnadoptedReplacementSurvivesTheLogoutSave(GameTestHelper helper) {
        interruptedLogout(helper, true, "W37INewerUnadopted");
    }

    /** W37I: with no newer point, the old player's logout save keeps the backup and the reconnect restores it. */
    @GameTest(template = "atmosphere_test", batch = "respawn_recovery", timeoutTicks = 40)
    public static void aRestoredPointOnAnUnadoptedReplacementSurvivesTheLogoutSave(GameTestHelper helper) {
        interruptedLogout(helper, false, "W37IRestoredUnadopted");
    }

    private static void interruptedLogout(GameTestHelper helper, boolean newer, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        ServerPlayer player = join(helper, id, name);
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, -72.25F, true, false);
        Consumer<PlayerEvent.PlayerRespawnEvent> interrupt = event -> {
            if (event.getEntity().getUUID().equals(id)) { throw new IllegalStateException(INTERRUPTED); }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerEvent.PlayerRespawnEvent.class, interrupt);
        try {
            try {
                server.getPlayerList().respawn(player, false);
                helper.fail("The injected respawn listener did not interrupt dispatch");
            } catch (IllegalStateException expected) {
                helper.assertTrue(INTERRUPTED.equals(expected.getMessage()), "Unexpected exception: " + expected);
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(interrupt);
        }
        ServerPlayer replacement = server.getPlayerList().getPlayer(id);
        helper.assertTrue(replacement != null && replacement != player && replacement.connection.player == player
                && hasBackup(replacement), "The fixture did not reach the interrupted-listener window");
        BlockPos point = helper.absolutePos(new BlockPos(2, 1, 2));
        Predicate<ServerPlayer> atNewer = holder -> Level.OVERWORLD.equals(holder.getRespawnDimension())
                && point.equals(holder.getRespawnPosition()) && holder.getRespawnAngle() == 12.5F
                && holder.isRespawnForced();
        DeathRespawnGameTests.afterEachEnd(helper, () -> {
                    server.getPlayerList().remove(replacement); // Vanilla leaves the unadopted replacement listed.
                    DeathRespawnGameTests.remove(server, id);
                },
                () -> {
                    assertTuple(helper, replacement); // The server-tick retry restored the point; it is not complete.
                    helper.assertTrue(hasBackup(replacement) && replacement.connection.player == player,
                            "The retry completed the backup before the connection adopted the replacement");
                    if (newer) {
                        RespawnSupersessionGameTests.spawnpoint(server, replacement, Level.OVERWORLD.location().toString(),
                                point, 12.5F);
                        helper.assertTrue(atNewer.test(replacement), "The command did not select the listed replacement");
                    }
                },
                () -> {
                    if (newer) {
                        helper.assertTrue(atNewer.test(replacement) && !hasBackup(replacement) && atNewer.test(player),
                                "The newer point was not carried to the player that the logout save writes");
                    }
                    server.getPlayerList().remove(player); // A disconnect removes and saves the connection's player.
                    ServerPlayer reconnected = join(helper, id, name);
                    if (newer) {
                        helper.assertTrue(atNewer.test(reconnected) && !hasBackup(reconnected),
                                "The newer point was lost at the logout save: " + reconnected.getRespawnPosition());
                    } else {
                        assertRestored(helper, reconnected);
                    }
                });
    }

    @GameTest(template = "atmosphere_test", batch = "respawn_recovery", timeoutTicks = 40)
    public static void pendingBackupSurvivesNativeLogoutAndPlayerFileReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        ServerPlayer player = join(helper, id, "W37FReconnect");
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, -72.25F, true, false);
        // Stop dispatch without throwing (and without calling our restoration) by removing the replacement at
        // HIGHEST, saving its pending root through the real PlayerList.remove player-file writer.
        Consumer<PlayerEvent.PlayerRespawnEvent> logout = event -> {
            if (event.getEntity().getUUID().equals(id)) {
                ServerPlayer replacement = (ServerPlayer) event.getEntity();
                helper.assertTrue(hasBackup(replacement) && replacement.getRespawnPosition() == null,
                        "The save cut did not contain a pending backup");
                server.getPlayerList().remove(replacement);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerEvent.PlayerRespawnEvent.class, logout);
        try {
            server.getPlayerList().respawn(player, false);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(logout);
        }
        // The event may restore its detached object later; the file written at the cut still contains the backup.
        ServerPlayer reconnected = join(helper, id, "W37FReconnect");
        try {
            assertRestored(helper, reconnected);
            server.getPlayerList().remove(reconnected);
            reconnected = join(helper, id, "W37FReconnect");
            assertRestored(helper, reconnected); // Verified tuple and backup deletion also survive the next save.
        } finally {
            server.getPlayerList().remove(reconnected);
        }
        helper.succeed();
    }

    /** W37H F6: a schema 1 backup whose outcome token this version does not know keeps its point. */
    @GameTest(template = "atmosphere_test", batch = "respawn_recovery", timeoutTicks = 40)
    public static void anUnknownOutcomeTokenIsRestoredWithTheGenericMessage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // The joined fixture player records its messages as tokens instead of sending chat packets.
        List<String> tokens = new ArrayList<>();
        ServerPlayer player = ConnectedTestPlayers.join(server, UUID.randomUUID(), "W37HUnknownToken", helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>(), false, tokens);
        CompoundTag root = RespawnPointBackup.encode(new RespawnPointBackup.Backup(
                new RespawnPointBackup.Point(CelestialIds.MOON_LEVEL, FAR, -72.25F, true), RespawnOutcome.UNLOADED));
        root.putString("outcome", "a_later_reason");
        CompoundTag persisted = new CompoundTag();
        persisted.put(RespawnPointBackup.KEY, root);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        tokens.clear();
        DeathRespawnGameTests.afterEachEnd(helper, () -> server.getPlayerList().remove(player), () -> {
            assertRestored(helper, player);
            helper.assertTrue(tokens.contains(SleepRespawnService.genericRespawnKey()),
                    "The restore of an unknown outcome did not send the generic message: " + tokens);
        });
    }

    private static ServerPlayer join(GameTestHelper helper, UUID id, String name) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), id, name, helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>());
    }

    private static boolean hasBackup(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(RespawnPointBackup.KEY);
    }

    private static void assertRestored(GameTestHelper helper, ServerPlayer player) {
        assertTuple(helper, player);
        helper.assertTrue(!hasBackup(player), "Verified reconnect did not complete the supported backup");
    }

    private static void assertTuple(GameTestHelper helper, ServerPlayer player) {
        helper.assertTrue(player.getRespawnDimension().equals(CelestialIds.MOON_LEVEL)
                        && FAR.equals(player.getRespawnPosition()) && player.isRespawnForced()
                        && player.getRespawnAngle() == -72.25F
                        && player.serverLevel().dimension().equals(Level.OVERWORLD),
                "The complete respawn tuple was not durably restored after the interruption");
    }
}
