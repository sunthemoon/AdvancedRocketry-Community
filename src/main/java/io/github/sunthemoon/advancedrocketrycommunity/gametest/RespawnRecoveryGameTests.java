package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
