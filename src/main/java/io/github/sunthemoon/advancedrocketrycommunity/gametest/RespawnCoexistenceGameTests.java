package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R3: other mods' gameplay setter vetoes cannot undo refusal or erase the preserved record. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RespawnCoexistenceGameTests {
    private RespawnCoexistenceGameTests() { }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void aLaterSpawnSetterReplacesACompletedRecoveryWithoutAnInterveningTick(GameTestHelper helper) {
        BlockPos first = new BlockPos(25_000_000, 80, 25_000_000);
        BlockPos second = first.above();
        SleepRespawnGameTests.withOptions(false, false, helper, "W37FNextPoint", (player, respawns) -> {
            player.setRespawnPosition(CelestialIds.MOON_LEVEL, first, 14.5F, false, false);
            var restored = respawns.respawn(player);
            SleepRespawnGameTests.assertOverworldFallback(helper, restored, CelestialIds.MOON_LEVEL, first, false,
                    RespawnOutcome.DISABLED);
            // The handler adopted this player, but no living tick has yet completed the retained recovery.
            restored.setRespawnPosition(CelestialIds.MOON_LEVEL, second, -28.75F, true, false);
            var latest = respawns.respawn(restored);
            SleepRespawnGameTests.assertOverworldFallback(helper, latest, CelestialIds.MOON_LEVEL, second, true,
                    RespawnOutcome.DISABLED);
            helper.assertTrue(latest.getRespawnAngle() == -28.75F, "An earlier recovery overwrote a later setter");
        }, () -> { });
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void cancellingEverySpawnSetterCannotEnterAStrangersStation(GameTestHelper helper) {
        ServerLevel space = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.SPACE_LEVEL);
        List<Runnable> cleanup = new ArrayList<>();
        BlockPos bed = SleepRespawnGameTests.commitStation(helper,
                StationRegistrySavedData.get(helper.getLevel().getServer()), UUID.randomUUID(), UUID.randomUUID(), cleanup);
        cleanup.add(SleepRespawnGameTests.placeBedOnPlatform(space, bed));
        SleepRespawnGameTests.withOptions(true, true, helper, "W37FCancelStation", (player, respawns) -> {
            player.setRespawnPosition(CelestialIds.SPACE_LEVEL, bed, 91.25F, true, false);
            Consumer<PlayerSetSpawnEvent> veto = event -> {
                if (event.getEntity().getUUID().equals(player.getUUID())) { event.setCanceled(true); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayerSetSpawnEvent.class, veto);
            try {
                var reborn = respawns.respawn(player);
                SleepRespawnGameTests.assertOverworldFallback(helper, reborn, CelestialIds.SPACE_LEVEL, bed, true,
                        RespawnOutcome.STATION_DENIED);
                helper.assertTrue(reborn.getRespawnAngle() == 91.25F, "Restore changed the exact saved angle");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(veto);
            }
        }, () -> cleanup.forEach(Runnable::run));
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 40)
    public static void cancellingEverySpawnSetterCannotLoadARefusedDestination(GameTestHelper helper) {
        ServerLevel moon = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.MOON_LEVEL);
        BlockPos far = new BlockPos(25_000_000, 80, 25_000_000);
        helper.assertTrue(moon.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "Unloaded fixture was already completed");
        SleepRespawnGameTests.withOptions(true, true, helper, "W37FCancelChunk", (player, respawns) -> {
            player.setRespawnPosition(CelestialIds.MOON_LEVEL, far, -135.75F, false, false);
            Consumer<PlayerSetSpawnEvent> veto = event -> {
                if (event.getEntity().getUUID().equals(player.getUUID())) { event.setCanceled(true); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, PlayerSetSpawnEvent.class, veto);
            try {
                var reborn = respawns.respawn(player);
                SleepRespawnGameTests.assertOverworldFallback(helper, reborn, CelestialIds.MOON_LEVEL, far, false,
                        RespawnOutcome.UNLOADED);
                helper.assertTrue(reborn.getRespawnAngle() == -135.75F, "Restore changed the exact saved angle");
                helper.assertTrue(moon.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null
                                && !moon.hasChunkAt(far),
                        "Refusal loaded or ticketed the unloaded destination despite a setter veto");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(veto);
            }
        }, () -> { });
        helper.succeed();
    }
}
