package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R1 uses native connected ServerPlayers, death ticks, /kill and the respawn packet handler, not fake players. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeathRespawnGameTests {
    private static final BlockPos FAR = new BlockPos(25_000_000, 80, 25_000_000);
    private DeathRespawnGameTests() { }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 60)
    public static void deathThenOverTwentyDeadTicksStillChecksTheSavedPoint(GameTestHelper helper) {
        delayed(helper, false);
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 60)
    public static void delayedRespawnAfterTheKillCommandStillChecksTheSavedPoint(GameTestHelper helper) {
        delayed(helper, true);
    }

    private static void delayed(GameTestHelper helper, boolean command) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, command ? "W37FKill" : "W37FDeath");
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 77.25F, true, false);
        if (command) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill " + player.getScoreboardName());
        } else {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        }
        helper.assertTrue(player.isDeadOrDying(), "The native death fixture did not die");
        int[] deadTicks = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            try {
                // Embedded fixtures are not socket-accepted into ServerConnectionListener; explicitly run the
                // production connection tick so doTick and the registered Forge END listener execute normally.
                player.connection.tick();
                if (++deadTicks[0] < 25) { return; }
                helper.assertTrue(player.getRemovalReason() == Entity.RemovalReason.KILLED && player.deathTime >= 20,
                        "The delayed fixture did not reach native corpse removal");
                helper.assertTrue(player.getRespawnPosition() == null && pending(player),
                        "The removed but connected dead player bypassed the policy");
                ServerPlayer reborn = packet(player);
                SleepRespawnGameTests.assertOverworldFallback(helper, reborn, CelestialIds.MOON_LEVEL, FAR, true,
                        RespawnOutcome.DISABLED);
                helper.assertTrue(reborn.getRespawnAngle() == 77.25F && !pending(reborn),
                        "Delayed death did not restore and verify the complete tuple");
                done[0] = true;
                helper.succeed();
            } catch (RuntimeException failure) {
                done[0] = true;
                throw failure;
            } finally {
                if (done[0]) { remove(server, player.getUUID()); }
            }
        });
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void immediateRespawnBeforeAnyDeadTickUsesTheDiscardedFinalCheck(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, "W37FImmediate");
        var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        boolean original = rule.get();
        try {
            rule.set(true, server);
            player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 77.25F, true, false);
            player.kill();
            helper.assertTrue(player.isDeadOrDying() && player.getRemovalReason() == null && !pending(player),
                    "Immediate fixture already passed a dead tick/removal");
            var reborn = packet(player);
            SleepRespawnGameTests.assertOverworldFallback(helper, reborn, CelestialIds.MOON_LEVEL, FAR, true,
                    RespawnOutcome.DISABLED);
        } finally {
            rule.set(original, server);
            remove(server, player.getUUID());
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void anAcceptedDeadTickRestoresAnEarlierSetAsidePoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var planet = NonPlayerAtmosphereGameTests.level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos head = new BlockPos(column.getX(), 200, column.getZ());
        Runnable bed = SleepRespawnGameTests.placeBedOnPlatform(planet, head);
        ServerPlayer player = join(helper, "W37FReconsider");
        try {
            player.setRespawnPosition(planet.dimension(), head, -14.5F, false, false);
            player.kill();
            player.connection.tick();
            helper.assertTrue(pending(player) && player.getRespawnPosition() == null, "Default refusal was not set aside");
            SwitchOverrides.set(CommonConfig.ALLOW_PLANET_RESPAWN, true);
            player.connection.tick();
            helper.assertTrue(!pending(player) && head.equals(player.getRespawnPosition())
                            && player.getRespawnAngle() == -14.5F,
                    "Fresh accepted dead-tick decision did not restore the earlier point");
            ServerPlayer reborn = packet(player);
            helper.assertTrue(reborn.serverLevel() == planet && reborn.position().closerThan(Vec3.atCenterOf(head), 3),
                    "The accepted decision did not reach native off-world respawn");
        } finally {
            SwitchOverrides.clear(CommonConfig.ALLOW_PLANET_RESPAWN);
            remove(server, player.getUUID());
            bed.run();
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aModCalledLivingRespawnWithTrueIsJudged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, "W37FModTrue");
        try {
            player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 77.25F, true, false);
            ServerPlayer reborn = server.getPlayerList().respawn(player, true);
            reborn.connection.player = reborn;
            SleepRespawnGameTests.assertOverworldFallback(helper, reborn, CelestialIds.MOON_LEVEL, FAR, true,
                    RespawnOutcome.DISABLED);
        } finally { remove(server, player.getUUID()); }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void nativeEndCreditsExitRetainsItsExcludedNonDeathBehavior(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var planet = NonPlayerAtmosphereGameTests.level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        var end = NonPlayerAtmosphereGameTests.level(helper, Level.END);
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos head = new BlockPos(column.getX(), 200, column.getZ());
        Runnable bed = SleepRespawnGameTests.placeBedOnPlatform(planet, head);
        ServerPlayer player = join(helper, "W37FEnd");
        try {
            player.setRespawnPosition(planet.dimension(), head, 0, false, false);
            player.teleportTo(end, 0.5, 100, 0.5, 0, 0);
            player.changeDimension(server.overworld()); // Real End->Overworld credits path, not a forged wonGame flag.
            helper.assertTrue(player.wonGame && !player.isDeadOrDying()
                            && player.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION && !pending(player),
                    "End exit was not the excluded native non-death return");
            ServerPlayer reborn = packet(player);
            helper.assertTrue(reborn.serverLevel() == planet && head.equals(reborn.getRespawnPosition()),
                    "The excluded End return did not retain native saved-bed behavior");
        } finally {
            remove(server, player.getUUID());
            bed.run();
        }
        helper.succeed();
    }

    private static ServerPlayer packet(ServerPlayer player) {
        player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        return player.connection.player;
    }

    private static ServerPlayer join(GameTestHelper helper, String name) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), UUID.randomUUID(), name, helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>());
    }

    private static void remove(MinecraftServer server, UUID id) {
        ServerPlayer current = server.getPlayerList().getPlayer(id);
        if (current != null) { server.getPlayerList().remove(current); }
    }

    private static boolean pending(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(RespawnPointBackup.KEY);
    }
}
