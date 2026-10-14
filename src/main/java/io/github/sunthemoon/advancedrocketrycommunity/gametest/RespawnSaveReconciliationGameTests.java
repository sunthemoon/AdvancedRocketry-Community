package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W37J: real native logout files in the interrupted connection-adoption window, not a replacement save as cleanup. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RespawnSaveReconciliationGameTests {
    private static final String INTERRUPTED = "W37J injected respawn listener interruption";
    private static final BlockPos FAR = new BlockPos(25_000_000, 80, 25_000_000);

    private RespawnSaveReconciliationGameTests() { }

    @GameTest(template = "atmosphere_test", batch = "respawn_save_reconciliation", timeoutTicks = 40)
    public static void aSetterImmediatelyFollowedByLogoutSavesTheLatestPoint(GameTestHelper helper) {
        immediate(helper, false, false, false, "W37JImmediate");
    }

    @GameTest(template = "atmosphere_test", batch = "respawn_save_reconciliation", timeoutTicks = 40)
    public static void aSetterBeforeTheThrowingListenerSurvivesLogout(GameTestHelper helper) {
        immediate(helper, true, false, false, "W37JBeforeThrow");
    }

    @GameTest(template = "atmosphere_test", batch = "respawn_save_reconciliation", timeoutTicks = 40)
    public static void shutdownSavesTheSamePointFromReplacementThenOldPlayer(GameTestHelper helper) {
        immediate(helper, false, true, false, "W37JShutdown");
    }

    @GameTest(template = "atmosphere_test", batch = "respawn_save_reconciliation", timeoutTicks = 40)
    public static void logoutReconciliationReturnsTheRetainedOpaqueRoot(GameTestHelper helper) {
        immediate(helper, false, false, true, "W37JRetained");
    }

    @GameTest(template = "atmosphere_test", batch = "respawn_save_reconciliation", timeoutTicks = 40)
    public static void twoNewerSettersBeforeLogoutSaveTheSecondPoint(GameTestHelper helper) {
        successive(helper, false, false, "W37JTwoLogout");
    }

    @GameTest(template = "atmosphere_test", batch = "respawn_save_reconciliation", timeoutTicks = 40)
    public static void endCopiesFollowLaterReplacementChanges(GameTestHelper helper) {
        successive(helper, true, false, "W37JTwoEnds");
    }

    @GameTest(template = "atmosphere_test", batch = "respawn_save_reconciliation", timeoutTicks = 40)
    public static void aForeignOldTupleIsNotOverwrittenByAFollowupCopy(GameTestHelper helper) {
        successive(helper, true, true, "W37JForeignOld");
    }

    private static void immediate(GameTestHelper helper, boolean beforeThrow, boolean shutdown, boolean retained,
                                  String name) {
        Window window = openWindow(helper, beforeThrow, name);
        try {
            CompoundTag opaque = null;
            if (retained) {
                opaque = new CompoundTag();
                opaque.putInt("schema", 99);
                opaque.putString("opaque", "unchanged".repeat(300));
                persisted(window.old()).getCompound(RespawnPointBackup.KEY).put(RespawnPointBackup.RETAINED, opaque);
            }
            if (!beforeThrow) { setter(window, window.first(), 12.5F); }
            helper.assertTrue(window.old().getRespawnPosition() == null && pending(window.old()),
                    "An END copy ran before the immediate save cut");
            if (shutdown) {
                // A live GameTest cannot stop its host. Drive stopServer's two native stages on this connection:
                // saveAll writes the listed replacement, then its shared listener removes/saves the old player.
                window.server().getPlayerList().saveAll();
                assertFile(helper, window, window.first(), 12.5F, true, null);
                window.replacement().connection.onDisconnect(Component.translatable("multiplayer.disconnect.server_shutdown"));
            } else { window.server().getPlayerList().remove(window.old()); }
            assertFileAndReconnect(helper, window, window.first(), 12.5F, opaque);
        } finally { cleanup(window); }
        helper.succeed();
    }

    private static void successive(GameTestHelper helper, boolean waitForSecondEnd, boolean foreignOld, String name) {
        Window window = openWindow(helper, false, name);
        BlockPos second = window.first().offset(1, 0, 0);
        BlockPos foreign = window.first().offset(0, 0, 1);
        Runnable finish = () -> {
            BlockPos expected = foreignOld ? foreign : second;
            float angle = foreignOld ? 91.0F : -33.5F;
            if (waitForSecondEnd) {
                assertTuple(helper, window.old(), expected, angle);
            }
            window.server().getPlayerList().remove(window.old());
            assertFileAndReconnect(helper, window, expected, angle, null);
        };
        Runnable[] steps = waitForSecondEnd ? new Runnable[] {
                () -> setter(window, window.first(), 12.5F),
                () -> {
                    assertTuple(helper, window.old(), window.first(), 12.5F);
                    helper.assertTrue(!pending(window.old()), "The first newer point did not supersede the backup");
                    if (foreignOld) { window.old().setRespawnPosition(Level.OVERWORLD, foreign, 91.0F, true, false); }
                    setter(window, second, -33.5F);
                }, finish
        } : new Runnable[] {
                () -> setter(window, window.first(), 12.5F),
                () -> {
                    assertTuple(helper, window.old(), window.first(), 12.5F);
                    helper.assertTrue(!pending(window.old()), "The first copy did not release the backup");
                    setter(window, second, -33.5F);
                    finish.run(); // No END between the second setter and its logout save.
                }
        };
        DeathRespawnGameTests.afterEachEnd(helper, () -> cleanup(window), steps);
    }

    private record Window(MinecraftServer server, ServerPlayer old, ServerPlayer replacement, BlockPos first, String name) { }

    private static Window openWindow(GameTestHelper helper, boolean beforeThrow, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer old = join(helper, UUID.randomUUID(), name);
        BlockPos first = helper.absolutePos(new BlockPos(2, 1, 2));
        old.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, -72.25F, true, false);
        Consumer<PlayerEvent.PlayerRespawnEvent> interrupt = event -> {
            if (event.getEntity() instanceof ServerPlayer replacement && replacement.getUUID().equals(old.getUUID())) {
                if (beforeThrow) { replacement.setRespawnPosition(Level.OVERWORLD, first, 12.5F, true, false); }
                throw new IllegalStateException(INTERRUPTED);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerEvent.PlayerRespawnEvent.class, interrupt);
        try {
            try {
                server.getPlayerList().respawn(old, false);
                helper.fail("The injected listener did not interrupt native respawn");
            } catch (IllegalStateException expected) {
                helper.assertTrue(INTERRUPTED.equals(expected.getMessage()), "Unexpected exception: " + expected);
            }
        } finally { MinecraftForge.EVENT_BUS.unregister(interrupt); }
        ServerPlayer replacement = server.getPlayerList().getPlayer(old.getUUID());
        helper.assertTrue(replacement != null && replacement != old && replacement.connection == old.connection
                        && replacement.connection.player == old && pending(old), "The unadopted window was not reached");
        return new Window(server, old, replacement, first, name);
    }

    private static void setter(Window window, BlockPos point, float angle) {
        RespawnSupersessionGameTests.spawnpoint(window.server(), window.replacement(),
                Level.OVERWORLD.location().toString(), point, angle);
    }

    private static void assertFileAndReconnect(GameTestHelper helper, Window window, BlockPos point, float angle,
                                               CompoundTag opaque) {
        assertFile(helper, window, point, angle, false, opaque); // Before any join or cleanup can rewrite it.
        ServerPlayer reconnected = join(helper, window.old().getUUID(), window.name());
        assertTuple(helper, reconnected, point, angle);
        helper.assertTrue(!pending(reconnected), "Reconnect resurrected the superseded backup");
        if (opaque != null) {
            helper.assertTrue(opaque.equals(persisted(reconnected).get(RespawnPointBackup.KEY)),
                    "Reconnect lost the opaque root");
        }
    }

    private static void assertFile(GameTestHelper helper, Window window, BlockPos point, float angle, boolean active,
                                   CompoundTag opaque) {
        try {
            CompoundTag disk = NbtIo.readCompressed(window.server().getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .resolve(window.old().getStringUUID() + ".dat").toFile());
            helper.assertTrue(disk.getString("SpawnDimension").equals(Level.OVERWORLD.location().toString())
                            && disk.getInt("SpawnX") == point.getX() && disk.getInt("SpawnY") == point.getY()
                            && disk.getInt("SpawnZ") == point.getZ() && disk.getBoolean("SpawnForced")
                            && Float.floatToRawIntBits(disk.getFloat("SpawnAngle")) == Float.floatToRawIntBits(angle),
                    "The native file does not contain the latest complete tuple");
            var root = disk.getCompound("ForgeData").getCompound(Player.PERSISTED_NBT_TAG).get(RespawnPointBackup.KEY);
            helper.assertTrue(RespawnPointBackup.decode(root).isPresent() == active,
                    "The file's active envelope does not match the save cut");
            helper.assertTrue(opaque == null ? active || root == null : opaque.equals(root),
                    "The native save lost or changed the retained opaque root");
        } catch (IOException failure) { throw new IllegalStateException("Native player file read failed", failure); }
    }

    private static void assertTuple(GameTestHelper helper, ServerPlayer player, BlockPos point, float angle) {
        helper.assertTrue(Level.OVERWORLD.equals(player.getRespawnDimension()) && point.equals(player.getRespawnPosition())
                        && player.isRespawnForced()
                        && Float.floatToRawIntBits(player.getRespawnAngle()) == Float.floatToRawIntBits(angle),
                "The latest complete tuple was not preserved: " + player.getRespawnPosition());
    }

    private static boolean pending(ServerPlayer player) {
        return RespawnPointBackup.decode(persisted(player).get(RespawnPointBackup.KEY)).isPresent();
    }

    private static CompoundTag persisted(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static ServerPlayer join(GameTestHelper helper, UUID id, String name) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), id, name, helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>());
    }

    private static void cleanup(Window window) {
        // Only after the file and reconnect assertions. Saving R before those would mask a failed old-object save.
        window.server().getPlayerList().remove(window.replacement());
        DeathRespawnGameTests.remove(window.server(), window.old().getUUID());
    }
}
