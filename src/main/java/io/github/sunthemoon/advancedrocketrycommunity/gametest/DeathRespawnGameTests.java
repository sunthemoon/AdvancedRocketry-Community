package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SleepRespawnService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * R1 uses native connected ServerPlayers, death ticks, /kill and the respawn packet handler, not fake players. The
 * production decision runs at the server tick's END, so multi-step tests move between steps on real ticks.
 */
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
        ServerPlayer[] reborn = {null};
        helper.onEachTick(() -> {
            if (done[0]) { return; }
            try {
                if (reborn[0] != null) {
                    // The handler adopted the replacement; the last server-tick END completed the verified recovery.
                    helper.assertTrue(reborn[0].getRespawnAngle() == 77.25F && !pending(reborn[0]),
                            "Delayed death did not restore and verify the complete tuple");
                    done[0] = true;
                    helper.succeed();
                    return;
                }
                // Embedded fixtures are not socket-accepted into ServerConnectionListener; explicitly run the
                // production connection tick so the native death ticks remove the corpse.
                player.connection.tick();
                if (++deadTicks[0] < 25) { return; }
                helper.assertTrue(player.getRemovalReason() == Entity.RemovalReason.KILLED && player.deathTime >= 20,
                        "The delayed fixture did not reach native corpse removal");
                helper.assertTrue(player.getRespawnPosition() == null && pending(player),
                        "The removed but connected dead player bypassed the policy");
                reborn[0] = packet(player);
                SleepRespawnGameTests.assertOverworldFallback(helper, reborn[0], CelestialIds.MOON_LEVEL, FAR, true,
                        RespawnOutcome.DISABLED);
            } catch (RuntimeException failure) {
                done[0] = true;
                throw failure;
            } finally {
                if (done[0]) { remove(server, player.getUUID()); }
            }
        });
    }

    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void anUnencodableNativeTupleDoesNotFailADeadTickOrEraseAnUnreadableBackup(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, "W37FNativeBounds");
        CompoundTag future = new CompoundTag();
        future.putInt("schema", 99);
        CompoundTag persisted = new CompoundTag();
        persisted.put(RespawnPointBackup.KEY, future);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        BlockPos outOfBounds = new BlockPos(30_000_001, 80, 0);
        player.kill();
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, Float.NaN, true, false);
        BiConsumer<BlockPos, Float> unchanged = (point, angle) -> helper.assertTrue(
                CelestialIds.MOON_LEVEL.equals(player.getRespawnDimension())
                        && point.equals(player.getRespawnPosition()) && player.isRespawnForced()
                        && Float.floatToRawIntBits(angle) == Float.floatToRawIntBits(player.getRespawnAngle())
                        && persisted.get(RespawnPointBackup.KEY) == future && future.getInt("schema") == 99,
                "An unencodable native tuple or unreadable backup was changed");
        afterEachEnd(helper, () -> remove(server, player.getUUID()),
                () -> {
                    unchanged.accept(FAR, Float.NaN);
                    player.setRespawnPosition(CelestialIds.MOON_LEVEL, outOfBounds, -12.25F, true, false);
                },
                () -> unchanged.accept(outOfBounds, -12.25F));
    }

    /** F5: another mod's non-compound PlayerPersisted parent is never overwritten; its refusal is skipped (fail-open). */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aForeignPersistedParentIsKeptAndItsRefusalSkipped(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, "W37HForeignRoot");
        StringTag foreign = StringTag.valueOf("another mod's data");
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, foreign);
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 77.25F, true, false);
        player.kill();
        Runnable unchanged = () -> helper.assertTrue(player.getPersistentData().get(Player.PERSISTED_NBT_TAG) == foreign
                        && CelestialIds.MOON_LEVEL.equals(player.getRespawnDimension()) && FAR.equals(player.getRespawnPosition())
                        && player.getRespawnAngle() == 77.25F && player.isRespawnForced(),
                "A foreign PlayerPersisted parent was overwritten, or the point was changed");
        afterEachEnd(helper, () -> remove(server, player.getUUID()), unchanged, unchanged);
    }

    /** F7: an unchanged refusal neither re-encodes nor rewrites the backup on later dead ticks; a changed one does. */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void anUnchangedRefusalDoesNotRewriteTheBackup(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, "W37HNoRewrite");
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 77.25F, true, false);
        player.kill();
        Supplier<Tag> root = () -> player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).get(RespawnPointBackup.KEY);
        Tag[] stored = {null};
        afterEachEnd(helper, () -> remove(server, player.getUUID()),
                () -> {
                    stored[0] = root.get();
                    helper.assertTrue(stored[0] instanceof CompoundTag tag && "disabled".equals(tag.getString("outcome")),
                            "The refusal was not set aside");
                },
                () -> {
                    helper.assertTrue(root.get() == stored[0] && "disabled".equals(((CompoundTag) stored[0]).getString("outcome")),
                            "An unchanged refusal rewrote the backup");
                    ((CompoundTag) stored[0]).putString("outcome", "a_later_reason"); // An outcome this version does not know.
                },
                () -> helper.assertTrue(root.get() != stored[0] && root.get() instanceof CompoundTag tag
                                && "disabled".equals(tag.getString("outcome")) && player.getRespawnPosition() == null,
                        "A changed outcome was not rewritten"));
    }

    /** F4: doTick skips Player.tick for a spectator touching unloaded chunks; the END decision needs no player tick. */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aDeadPlayerIsDecidedWithoutAnyPlayerTick(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = join(helper, "W37HNoPlayerTick");
        int[] playerTicks = {0};
        Consumer<TickEvent.PlayerTickEvent> count = event -> { if (event.player == player) { playerTicks[0]++; } };
        MinecraftForge.EVENT_BUS.addListener(count);
        player.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 77.25F, true, false);
        player.kill();
        afterEachEnd(helper, () -> {
                    MinecraftForge.EVENT_BUS.unregister(count);
                    remove(server, player.getUUID());
                },
                () -> helper.assertTrue(playerTicks[0] == 0 && player.getRespawnPosition() == null && pending(player),
                        "A dead player without a player tick was not decided: playerTicks=" + playerTicks[0]));
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

    /** Holds allow on across a server tick, so it has a batch of its own: concurrent tests keep the default options. */
    @GameTest(template = "atmosphere_test", batch = "respawn_option_change", timeoutTicks = 40)
    public static void anAcceptedDeadTickRestoresAnEarlierSetAsidePoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var planet = NonPlayerAtmosphereGameTests.level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos head = new BlockPos(column.getX(), 200, column.getZ());
        Runnable bed = SleepRespawnGameTests.placeBedOnPlatform(planet, head);
        Runnable loaded = forceReach(planet, head);
        ServerPlayer player = join(helper, "W37FReconsider");
        player.setRespawnPosition(planet.dimension(), head, -14.5F, false, false);
        player.kill();
        afterEachEnd(helper, () -> {
                    SwitchOverrides.clear(CommonConfig.ALLOW_PLANET_RESPAWN);
                    remove(server, player.getUUID());
                    loaded.run();
                    bed.run();
                },
                () -> {
                    helper.assertTrue(pending(player) && player.getRespawnPosition() == null,
                            "Default refusal was not set aside");
                    SwitchOverrides.set(CommonConfig.ALLOW_PLANET_RESPAWN, true);
                },
                () -> {
                    helper.assertTrue(!pending(player) && head.equals(player.getRespawnPosition())
                                    && player.getRespawnAngle() == -14.5F,
                            "Fresh accepted dead-tick decision did not restore the earlier point");
                    ServerPlayer reborn = packet(player);
                    helper.assertTrue(reborn.serverLevel() == planet
                                    && reborn.position().closerThan(Vec3.atCenterOf(head), 3),
                            "The accepted decision did not reach native off-world respawn");
                });
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

    /** F2: the End credits return is judged like a death respawn; the default options refuse the planet bed. */
    @GameTest(template = "atmosphere_test", batch = "death_respawn", timeoutTicks = 40)
    public static void aRefusedEndExitLandsAtTheOverworldSpawnAndKeepsThePoint(GameTestHelper helper) {
        endExit(helper, false, "W37HEndRefused");
    }

    /** Holds allow on across a server tick, so it has a batch of its own: concurrent tests keep the default options. */
    @GameTest(template = "atmosphere_test", batch = "end_exit_allowed", timeoutTicks = 40)
    public static void anAcceptedEndExitLandsAtTheSavedPoint(GameTestHelper helper) {
        endExit(helper, true, "W37HEndAccepted");
    }

    private static void endExit(GameTestHelper helper, boolean allow, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        var planet = NonPlayerAtmosphereGameTests.level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        var end = NonPlayerAtmosphereGameTests.level(helper, Level.END);
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos head = new BlockPos(column.getX(), 200, column.getZ());
        Runnable bed = SleepRespawnGameTests.placeBedOnPlatform(planet, head);
        Runnable loaded = forceReach(planet, head);
        if (allow) { SwitchOverrides.set(CommonConfig.ALLOW_PLANET_RESPAWN, true); }
        ServerPlayer player = join(helper, name);
        player.setRespawnPosition(planet.dimension(), head, -14.5F, false, false);
        player.teleportTo(end, 0.5, 100, 0.5, 0, 0);
        player.changeDimension(server.overworld()); // Real End->Overworld credits path, not a forged wonGame flag.
        helper.assertTrue(player.wonGame && !player.isDeadOrDying()
                        && player.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION,
                "The fixture did not take the native End credits exit");
        ServerPlayer[] reborn = {null};
        afterEachEnd(helper, () -> {
                    SwitchOverrides.clear(CommonConfig.ALLOW_PLANET_RESPAWN);
                    remove(server, player.getUUID());
                    loaded.run();
                    bed.run();
                },
                () -> {
                    helper.assertTrue(player.wonGame && (allow
                                    ? !pending(player) && head.equals(player.getRespawnPosition())
                                    : pending(player) && player.getRespawnPosition() == null),
                            "The credits ticks were not judged: pending=" + pending(player));
                    reborn[0] = packet(player); // handleClientCommand: wonGame, so respawn(player, true).
                    if (allow) {
                        helper.assertTrue(reborn[0].serverLevel() == planet
                                        && reborn[0].position().closerThan(Vec3.atCenterOf(head), 3)
                                        && head.equals(reborn[0].getRespawnPosition()),
                                "The accepted End exit did not land at the saved bed: " + reborn[0].position());
                        helper.assertTrue(SleepRespawnGameTests.chat(reborn[0]).stream()
                                        .noneMatch(token -> token.contains(".planet_respawn.")),
                                "An accepted End exit reported a fallback");
                    } else {
                        SleepRespawnGameTests.assertOverworldFallback(helper, reborn[0], planet.dimension(), head, false,
                                RespawnOutcome.DISABLED);
                        BlockPos spawn = server.overworld().getSharedSpawnPos();
                        int radius = Math.max(1, server.getSpawnRadius(server.overworld())) + 1;
                        helper.assertTrue(Math.abs(reborn[0].getBlockX() - spawn.getX()) <= radius
                                        && Math.abs(reborn[0].getBlockZ() - spawn.getZ()) <= radius
                                        && reborn[0].getRespawnAngle() == -14.5F,
                                "The refused End exit did not land at the Overworld spawn: " + reborn[0].position());
                    }
                },
                () -> helper.assertTrue(!pending(reborn[0]) && head.equals(reborn[0].getRespawnPosition()),
                        "The End exit's recovery was not completed"));
    }

    /** The real respawn packet handler; it adopts the replacement, which the next server-tick END can complete. */
    static ServerPlayer packet(ServerPlayer player) {
        player.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        return player.connection.player;
    }

    /**
     * Runs each step on its own later tick, the first on the tick after the test body, from one onEachTick
     * registration. So the production server-tick END decision runs before every step. The cleanup runs once.
     */
    static void afterEachEnd(GameTestHelper helper, Runnable cleanup, Runnable... steps) {
        long start = helper.getTick();
        int[] next = {0};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || helper.getTick() == start) { return; }
            try {
                steps[next[0]++].run();
                if (next[0] == steps.length) {
                    done[0] = true;
                    helper.succeed();
                }
            } catch (RuntimeException failure) {
                done[0] = true;
                throw failure;
            } finally {
                if (done[0]) { cleanup.run(); }
            }
        });
    }

    /** Keeps every chunk a bed's stand-up search reads loaded across ticks; returns the release. */
    static Runnable forceReach(ServerLevel level, BlockPos head) {
        List<ChunkPos> added = new ArrayList<>();
        int reach = SleepRespawnService.ARRIVAL_REACH;
        for (int x = (head.getX() - reach - 1) >> 4; x <= (head.getX() + reach) >> 4; x++) {
            for (int z = (head.getZ() - reach) >> 4; z <= (head.getZ() + reach) >> 4; z++) {
                if (level.setChunkForced(x, z, true)) { added.add(new ChunkPos(x, z)); }
            }
        }
        return () -> added.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
    }

    static ServerPlayer join(GameTestHelper helper, String name) {
        return join(helper, UUID.randomUUID(), name);
    }

    static ServerPlayer join(GameTestHelper helper, UUID id, String name) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), id, name, helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>());
    }

    static void remove(MinecraftServer server, UUID id) {
        ServerPlayer current = server.getPlayerList().getPlayer(id);
        if (current != null) { server.getPlayerList().remove(current); }
    }

    static boolean pending(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(RespawnPointBackup.KEY);
    }
}
