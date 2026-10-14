package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W37F review F1: a point set while an older one is set aside is newer and wins. W37I: the exact cleared tuple is not
 * newer, whoever writes it. Native connected players, the real command dispatcher, the respawn packet and the
 * production server-tick END decision, with the default options.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RespawnSupersessionGameTests {
    private static final BlockPos FAR = new BlockPos(25_000_000, 80, 25_000_000);
    private static final BlockPos NEWER_FAR = new BlockPos(-25_000_000, 90, 25_000_000);
    private static final String RESPAWN_TOKEN = "message." + ModIdentity.MOD_ID + ".planet_respawn.";

    private RespawnSupersessionGameTests() { }

    /** The dead tick removes the older backup and judges the command's off-world point as a fresh saved point. */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void spawnpointBeforeCorpseRemovalIsJudgedAsTheNewerPoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = dying(helper, "W37HCmdMoon");
        ServerPlayer[] reborn = {null};
        DeathRespawnGameTests.afterEachEnd(helper, () -> DeathRespawnGameTests.remove(server, player.getUUID()),
                () -> {
                    assertSetAside(helper, player, FAR);
                    spawnpoint(server, player, CelestialIds.MOON_LEVEL.location().toString(), NEWER_FAR, 33.5F);
                    helper.assertTrue(NEWER_FAR.equals(player.getRespawnPosition()) && player.getRemovalReason() == null,
                            "The command did not set the dead player's point before corpse removal");
                },
                () -> {
                    assertSetAside(helper, player, NEWER_FAR);
                    reborn[0] = DeathRespawnGameTests.packet(player);
                    SleepRespawnGameTests.assertOverworldFallback(helper, reborn[0], CelestialIds.MOON_LEVEL, NEWER_FAR,
                            true, RespawnOutcome.DISABLED);
                    helper.assertTrue(reborn[0].getRespawnAngle() == 33.5F, "The newer point's angle was not kept");
                },
                () -> helper.assertTrue(!DeathRespawnGameTests.pending(reborn[0])
                                && NEWER_FAR.equals(reborn[0].getRespawnPosition()),
                        "The newer point's recovery was not completed"));
    }

    /** The packet arrives before any dead tick sees the command: the DISCARDED final check lets the newer point win. */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void spawnpointBeforeCorpseRemovalWinsAtTheFinalCheck(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos newer = standing(helper);
        ServerPlayer player = dying(helper, "W37HCmdFinal");
        DeathRespawnGameTests.afterEachEnd(helper, () -> DeathRespawnGameTests.remove(server, player.getUUID()),
                () -> {
                    assertSetAside(helper, player, FAR);
                    spawnpoint(server, player, Level.OVERWORLD.location().toString(), newer, 0.0F);
                    helper.assertTrue(player.getRemovalReason() == null, "The corpse was removed too early");
                    assertNativeRespawnAt(helper, server, DeathRespawnGameTests.packet(player), newer);
                });
    }

    /** No DISCARDED event after KILLED removal: vanilla uses the newer point, and the respawn event keeps it. */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void spawnpointAfterCorpseRemovalIsTheNativeRespawnPoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos newer = standing(helper);
        ServerPlayer player = dying(helper, "W37HCmdRemoved");
        DeathRespawnGameTests.afterEachEnd(helper, () -> DeathRespawnGameTests.remove(server, player.getUUID()),
                () -> {
                    assertSetAside(helper, player, FAR);
                    for (int tick = 0; tick < 25; tick++) { player.connection.tick(); }
                    helper.assertTrue(player.getRemovalReason() == Entity.RemovalReason.KILLED,
                            "The fixture did not reach native corpse removal");
                    spawnpoint(server, player, Level.OVERWORLD.location().toString(), newer, 0.0F);
                    assertNativeRespawnAt(helper, server, DeathRespawnGameTests.packet(player), newer);
                });
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aNewerOriginalPointWithAVetoedNativeSetterDoesNotResurrectTheBackup(GameTestHelper helper) {
        newerOriginalWithoutCopy(helper, false, "W37JVetoClone");
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aNewerOriginalPointWithoutAnArrivalDoesNotResurrectTheBackup(GameTestHelper helper) {
        newerOriginalWithoutCopy(helper, true, "W37JNoArrival");
    }

    private static void newerOriginalWithoutCopy(GameTestHelper helper, boolean noArrival, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos newer = standing(helper);
        ServerPlayer player = dying(helper, name);
        int[] setters = {0};
        Consumer<PlayerSetSpawnEvent> veto = event -> {
            if (event.getEntity().getUUID().equals(player.getUUID())) {
                setters[0]++;
                event.setCanceled(true);
            }
        };
        ServerPlayer[] reborn = {null};
        DeathRespawnGameTests.afterEachEnd(helper, () -> {
                    MinecraftForge.EVENT_BUS.unregister(veto);
                    DeathRespawnGameTests.remove(server, player.getUUID());
                },
                () -> {
                    assertSetAside(helper, player, FAR);
                    for (int tick = 0; tick < 25; tick++) { player.connection.tick(); }
                    helper.assertTrue(player.getRemovalReason() == Entity.RemovalReason.KILLED,
                            "The fixture did not reach corpse removal");
                    spawnpoint(server, player, Level.OVERWORLD.location().toString(), newer, 31.25F);
                    helper.assertTrue(newer.equals(player.getRespawnPosition()) && DeathRespawnGameTests.pending(player),
                            "The original's newer point and pending envelope did not coexist at the cut");
                    if (noArrival) {
                        helper.setBlock(new BlockPos(2, 1, 2), Blocks.OBSIDIAN);
                        helper.setBlock(new BlockPos(2, 2, 2), Blocks.OBSIDIAN);
                    }
                    // Installed after the command, so it cannot veto the original's newer tuple.
                    MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayerSetSpawnEvent.class, veto);
                    reborn[0] = DeathRespawnGameTests.packet(player);
                    helper.assertTrue(setters[0] == (noArrival ? 0 : 1),
                            "Native respawn did not take the intended arrival/setter branch: " + setters[0]);
                    helper.assertTrue(reborn[0].getRespawnPosition() == null && !DeathRespawnGameTests.pending(reborn[0]),
                            "The freshly cleared replacement resurrected the superseded Moon point");
                    if (!noArrival) {
                        helper.assertTrue(reborn[0].position().closerThan(Vec3.atBottomCenterOf(newer), 1.0D),
                                "The veto fixture did not arrive at the newer native point");
                    }
                    assertNoKeptMessage(helper, reborn[0]);
                },
                () -> helper.assertTrue(reborn[0].getRespawnPosition() == null && !DeathRespawnGameTests.pending(reborn[0]),
                        "A later END resurrected the superseded backup"));
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aRespawnListenerBeforeOursKeepsItsNewerPoint(GameTestHelper helper) {
        respawnSetter(helper, EventPriority.HIGHEST, "W37HSetterFirst");
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aRespawnListenerAfterOursKeepsItsNewerPoint(GameTestHelper helper) {
        respawnSetter(helper, EventPriority.LOWEST, "W37HSetterLast");
    }

    /** Another mod sets the replacement's point in the respawn event, before or after this mod's restore. */
    private static void respawnSetter(GameTestHelper helper, EventPriority priority, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos newer = helper.absolutePos(new BlockPos(2, 1, 2));
        ServerPlayer player = dying(helper, name);
        Consumer<PlayerEvent.PlayerRespawnEvent> setter = event -> {
            if (event.getEntity() instanceof ServerPlayer replacement && replacement.getUUID().equals(player.getUUID())) {
                replacement.setRespawnPosition(Level.OVERWORLD, newer, 12.5F, false, false);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(priority, false, PlayerEvent.PlayerRespawnEvent.class, setter);
        ServerPlayer[] reborn = {null};
        DeathRespawnGameTests.afterEachEnd(helper, () -> {
                    MinecraftForge.EVENT_BUS.unregister(setter);
                    DeathRespawnGameTests.remove(server, player.getUUID());
                },
                () -> {
                    assertSetAside(helper, player, FAR);
                    reborn[0] = DeathRespawnGameTests.packet(player);
                    helper.assertTrue(reborn[0].serverLevel() == server.overworld(), "The refused point was used");
                    if (priority == EventPriority.HIGHEST) {
                        assertNoKeptMessage(helper, reborn[0]); // The newer point was never overwritten.
                    }
                },
                () -> helper.assertTrue(Level.OVERWORLD.equals(reborn[0].getRespawnDimension())
                                && newer.equals(reborn[0].getRespawnPosition()) && reborn[0].getRespawnAngle() == 12.5F
                                && !reborn[0].isRespawnForced() && !DeathRespawnGameTests.pending(reborn[0]),
                        "The older backup overwrote a respawn listener's newer point: "
                                + reborn[0].getRespawnDimension().location() + " " + reborn[0].getRespawnPosition()));
    }

    /**
     * W37I (Root's decision; W37H expected the opposite): another mod's null setter writes the exact cleared tuple,
     * which is not a newer point. The backup governs and the point is restored.
     */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aForeignClearWhileABackupIsPendingIsRestored(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = dying(helper, "W37IForeignClear");
        ServerPlayer[] reborn = {null};
        DeathRespawnGameTests.afterEachEnd(helper, () -> DeathRespawnGameTests.remove(server, player.getUUID()),
                () -> {
                    assertSetAside(helper, player, FAR);
                    player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
                },
                () -> {
                    assertSetAside(helper, player, FAR); // The next decision kept the backup.
                    reborn[0] = DeathRespawnGameTests.packet(player);
                    assertRestored(helper, reborn[0]);
                },
                () -> assertCompleted(helper, reborn[0]));
    }

    /** W37I: nothing transient decides, so the clear, the logout save and a reconnect still leave the backup governing. */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aForeignClearKeepsTheBackupAcrossTheLogoutSave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID id = UUID.randomUUID();
        ServerPlayer player = DeathRespawnGameTests.join(helper, id, "W37IClearLogout");
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 77.25F, true, false);
        player.kill();
        ServerPlayer[] reborn = {null};
        DeathRespawnGameTests.afterEachEnd(helper, () -> DeathRespawnGameTests.remove(server, id),
                () -> {
                    assertSetAside(helper, player, FAR);
                    player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
                    server.getPlayerList().remove(player); // The native player-file save.
                    ServerPlayer reconnected = DeathRespawnGameTests.join(helper, id, "W37IClearLogout");
                    helper.assertTrue(reconnected.isDeadOrDying(), "The reconnected fixture is not on the death screen");
                    assertSetAside(helper, reconnected, FAR);
                },
                () -> {
                    ServerPlayer current = server.getPlayerList().getPlayer(id);
                    helper.assertTrue(current != null, "The reconnected player left the player list");
                    assertSetAside(helper, current, FAR); // The reconnected dead player's decision kept it.
                    reborn[0] = DeathRespawnGameTests.packet(current);
                    assertRestored(helper, reborn[0]);
                },
                () -> assertCompleted(helper, reborn[0]));
    }

    /**
     * W37I: another mod's Clone listener copies the old player's tuple, which is this mod's clear, onto the replacement
     * through the real setter. That is not a newer point, so the refused point is restored.
     */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aCloneListenerCopyingTheClearedTupleKeepsThePoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = dying(helper, "W37ICloneCopy");
        boolean[] copied = {false};
        Consumer<PlayerEvent.Clone> copy = event -> {
            if (event.getEntity() instanceof ServerPlayer replacement && event.getOriginal() instanceof ServerPlayer original
                    && replacement.getUUID().equals(player.getUUID())) {
                copied[0] = original.getRespawnPosition() == null;
                replacement.setRespawnPosition(original.getRespawnDimension(), original.getRespawnPosition(),
                        original.getRespawnAngle(), original.isRespawnForced(), false);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.Clone.class, copy);
        ServerPlayer[] reborn = {null};
        DeathRespawnGameTests.afterEachEnd(helper, () -> {
                    MinecraftForge.EVENT_BUS.unregister(copy);
                    DeathRespawnGameTests.remove(server, player.getUUID());
                },
                () -> {
                    assertSetAside(helper, player, FAR);
                    reborn[0] = DeathRespawnGameTests.packet(player);
                    helper.assertTrue(copied[0], "The Clone listener did not copy the cleared tuple");
                    assertRestored(helper, reborn[0]);
                },
                () -> assertCompleted(helper, reborn[0]));
    }

    /** A connected player with a Moon point (the default options refuse it) who has just died. */
    private static ServerPlayer dying(GameTestHelper helper, String name) {
        ServerPlayer player = DeathRespawnGameTests.join(helper, name);
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 77.25F, true, false);
        player.kill();
        return player;
    }

    /** /spawnpoint through the real dispatcher; a name selector also finds a dead player. */
    static void spawnpoint(MinecraftServer server, ServerPlayer player, String dimension, BlockPos at,
                                   float angle) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "execute in " + dimension + " run spawnpoint " + player.getScoreboardName() + " " + at.getX() + " "
                        + at.getY() + " " + at.getZ() + " " + angle);
    }

    /** An empty two-block standing place inside the test structure, as vanilla's forced point needs. */
    private static BlockPos standing(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR);
        return helper.absolutePos(new BlockPos(2, 1, 2));
    }

    private static void assertSetAside(GameTestHelper helper, ServerPlayer player, BlockPos point) {
        Tag root = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).get(RespawnPointBackup.KEY);
        helper.assertTrue(player.getRespawnPosition() == null && RespawnPointBackup.decode(root)
                        .map(backup -> backup.point().position().equals(point)).orElse(false),
                "The refused point " + point + " was not set aside");
    }

    /** The refused Moon point did not place the replacement, and is back on it with its reason. */
    private static void assertRestored(GameTestHelper helper, ServerPlayer reborn) {
        SleepRespawnGameTests.assertOverworldFallback(helper, reborn, CelestialIds.MOON_LEVEL, FAR, true,
                RespawnOutcome.DISABLED);
        helper.assertTrue(reborn.getRespawnAngle() == 77.25F, "The restored point lost its angle");
    }

    private static void assertCompleted(GameTestHelper helper, ServerPlayer reborn) {
        helper.assertTrue(!DeathRespawnGameTests.pending(reborn) && CelestialIds.MOON_LEVEL.equals(reborn.getRespawnDimension())
                        && FAR.equals(reborn.getRespawnPosition()) && reborn.getRespawnAngle() == 77.25F,
                "The restored point's recovery was not completed: " + reborn.getRespawnPosition());
    }

    private static void assertNativeRespawnAt(GameTestHelper helper, MinecraftServer server, ServerPlayer reborn,
                                              BlockPos newer) {
        helper.assertTrue(reborn.serverLevel() == server.overworld()
                        && reborn.position().closerThan(Vec3.atBottomCenterOf(newer), 1.0D),
                "The newer command point was not the native respawn: " + reborn.position());
        helper.assertTrue(Level.OVERWORLD.equals(reborn.getRespawnDimension()) && newer.equals(reborn.getRespawnPosition())
                        && reborn.isRespawnForced() && !DeathRespawnGameTests.pending(reborn),
                "The older backup overwrote the newer command point: " + reborn.getRespawnPosition());
        assertNoKeptMessage(helper, reborn);
    }

    private static void assertNoKeptMessage(GameTestHelper helper, ServerPlayer reborn) {
        List<String> chat = SleepRespawnGameTests.chat(reborn);
        helper.assertTrue(chat.stream().noneMatch(token -> token.startsWith(RESPAWN_TOKEN)),
                "The older point was reported as kept: " + chat);
    }
}
