package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.datafixers.util.Either;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.Options;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.SleepRefusal;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.SleepRespawnService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationReservation;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-066 section 3.3 W37: sleep admission on a non-Overworld mapped surface and in Space, the unchanged Overworld
 * bed rules and the two planet-respawn options through the real
 * {@code PlayerList.respawn}. Every fixture is built and released inside its own test.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SleepRespawnGameTests {
    private static final String TEMPLATE = "atmosphere_test";
    /** Above the v1.8 Moon terrain (top blocks y 12-36, crater rims y 44 or lower). */
    private static final int MOON_Y = 64;
    private static final int SPACE_Y = 128;
    /** Above the Tau Ceti f terrain. */
    private static final int SURFACE_Y = 200;
    private static final BlockPos FAR = new BlockPos(25_000_000, 80, 25_000_000);
    private static final ResourceKey<Level> MISSING_BODY =
            ResourceKey.create(Registries.DIMENSION, ModIdentity.id("w37_missing_body"));
    private static final String RESPAWN_TOKEN = "message." + ModIdentity.MOD_ID + ".planet_respawn.";

    private SleepRespawnGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void sleepIsDeniedInVacuumOnAPlanetAndInSpace(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        List<String> tokens = new ArrayList<>();
        ServerPlayer player = join(helper, "ARCEW37Vacuum", tokens);
        List<Runnable> cleanup = new ArrayList<>();
        try {
            for (ResourceKey<Level> key : List.of(CelestialIds.MOON_LEVEL, CelestialIds.SPACE_LEVEL)) {
                ServerLevel level = NonPlayerAtmosphereGameTests.level(helper, key);
                BlockPos head = new BlockPos(column.getX(), key == CelestialIds.MOON_LEVEL ? MOON_Y : SPACE_Y,
                        column.getZ());
                cleanup.add(placeBedOnPlatform(level, head));
                helper.assertTrue(StationRegistrySavedData.get(server).findAt(head.getX(), head.getZ()).isEmpty(),
                        "The vacuum fixture lies inside a station");
                player.teleportTo(level, head.getX() + 1.5D, head.getY(), head.getZ() + 1.5D, 0.0F, 0.0F);
                tokens.clear();
                Either<Player.BedSleepingProblem, Unit> result = player.startSleepInBed(head);
                helper.assertTrue(result.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_HERE
                        && !player.isSleeping(), "Vacuum sleep was not refused in " + key.location() + ": " + result);
                helper.assertTrue(tokens.contains(actionBar(SleepRespawnService.sleepKey(SleepRefusal.UNBREATHABLE))),
                        "The registered listener did not explain the vacuum refusal in " + key.location() + ": " + tokens);
                helper.assertTrue(player.getRespawnPosition() == null, "A refused off-world bed set a respawn point");
            }
        } finally {
            server.getPlayerList().remove(player);
            cleanup.forEach(Runnable::run);
        }
        helper.succeed();
    }

    /** Owner decision 05: inside a committed station, live VISIT permission comes before the room's air. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void sleepInsideAStationNeedsVisitPermission(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.SPACE_LEVEL);
        List<String> tokens = new ArrayList<>();
        ServerPlayer player = join(helper, "ARCEW37Station", tokens);
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        UUID foreign = UUID.randomUUID();
        UUID owned = UUID.randomUUID();
        List<Runnable> cleanup = new ArrayList<>();
        try {
            BlockPos foreignBed = commitStation(helper, stations, foreign, UUID.randomUUID(), cleanup);
            BlockPos ownedBed = commitStation(helper, stations, owned, player.getUUID(), cleanup);
            cleanup.add(placeBedOnPlatform(space, foreignBed));
            cleanup.add(placeBedOnPlatform(space, ownedBed));
            player.teleportTo(space, foreignBed.getX() + 1.5D, foreignBed.getY(), foreignBed.getZ() + 1.5D, 0.0F, 0.0F);
            Either<Player.BedSleepingProblem, Unit> denied = player.startSleepInBed(foreignBed);
            helper.assertTrue(denied.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_HERE
                    && tokens.contains(actionBar(SleepRespawnService.sleepKey(SleepRefusal.STATION_DENIED))),
                    "A stranger's station admitted sleep or gave another reason: " + tokens);
            tokens.clear();
            player.teleportTo(space, ownedBed.getX() + 1.5D, ownedBed.getY(), ownedBed.getZ() + 1.5D, 0.0F, 0.0F);
            player.startSleepInBed(ownedBed);
            helper.assertTrue(tokens.contains(actionBar(SleepRespawnService.sleepKey(SleepRefusal.UNBREATHABLE))),
                    "The owner's VISIT did not pass to the air check: " + tokens);
        } finally {
            server.getPlayerList().remove(player);
            cleanup.forEach(Runnable::run);
        }
        helper.succeed();
    }

    /**
     * Supplied sealed rooms admit sleep on the Moon and outside stations in Space; a room still being scanned does
     * not. Both admission and room supply use the installed production authority and native startSleepInBed.
     */
    // Adjacent airlock fixtures manually advance the installed atmosphere manager; isolate this real-tick scan.
    @GameTest(template = TEMPLATE, batch = "sleep_rooms", timeoutTicks = 60)
    public static void sealedBreathableRoomsAdmitSleepButAPendingRoomDoesNot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel moon = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.MOON_LEVEL);
        ServerLevel space = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.SPACE_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(3, 0, 3));
        BlockPos moonVent = new BlockPos(column.getX(), MOON_Y, column.getZ());
        BlockPos spaceVent = new BlockPos(column.getX(), SPACE_Y, column.getZ());
        BlockPos pendingVent = new BlockPos(column.getX() + 8, MOON_Y, column.getZ() + 4);
        List<String> tokens = new ArrayList<>();
        ServerPlayer player = join(helper, "ARCEW37Room", tokens);
        Set<ChunkPos> forcedMoon = new LinkedHashSet<>();
        Set<ChunkPos> forcedSpace = new LinkedHashSet<>();
        Runnable cleanup = () -> {
            try {
                server.getPlayerList().remove(player);
                clearBox(moon, moonVent.offset(-2, 0, -2), moonVent.offset(2, 3, 2));
                clearBox(space, spaceVent.offset(-2, 0, -2), spaceVent.offset(2, 3, 2));
                clearBox(moon, pendingVent.offset(-4, 0, -4), pendingVent.offset(4, 8, 4));
            } finally {
                forcedMoon.forEach(chunk -> moon.setChunkForced(chunk.x, chunk.z, false));
                forcedSpace.forEach(chunk -> space.setChunkForced(chunk.x, chunk.z, false));
            }
        };
        OxygenVentBlockEntity[] vents = new OxygenVentBlockEntity[3];
        try {
            force(moon, moonVent, 4, forcedMoon);
            force(moon, pendingVent, 4, forcedMoon);
            force(space, spaceVent, 4, forcedSpace);
            vents[0] = roomWithBed(moon, moonVent, 2, 3);
            vents[1] = roomWithBed(space, spaceVent, 2, 3);
        } catch (RuntimeException failed) {
            cleanup.run();
            throw failed;
        }
        GameTestTickPacer pacer = new GameTestTickPacer(server);
        long start = helper.getTick();
        boolean[] finished = {false};
        boolean[] pendingPlaced = {false};
        // One callback for the whole wait; cross-Level heightmaps settle at the end of the setup tick.
        helper.onEachTick(() -> {
            if (finished[0]) {
                return;
            }
            try {
                pacer.pace();
                if (helper.getTick() <= start) {
                    return;
                }
                com.mojang.logging.LogUtils.getLogger().info(
                        "W78 tick={} serverTick={} pendingPlaced={} moonLive={} spaceLive={} pendingLive={} "
                                + "moonSupplied={} spaceSupplied={} pendingAir={} moonMetrics={} spaceMetrics={}",
                        helper.getTick(), server.getTickCount(), pendingPlaced[0],
                        new NaturalBedFixtures.Room(moon, moonVent).live(),
                        new NaturalBedFixtures.Room(space, spaceVent).live(),
                        new NaturalBedFixtures.Room(moon, pendingVent).live(),
                        suppliedBed(moon, moonVent), suppliedBed(space, spaceVent),
                        AtmosphereRuntime.breathabilityAt(moon, bedHead(pendingVent)),
                        AtmosphereRuntime.metrics(moon), AtmosphereRuntime.metrics(space));
                if (!(suppliedBed(moon, moonVent) && suppliedBed(space, spaceVent))) {
                    helper.assertTrue(helper.getTick() < 50, "The supplied rooms did not seal: moon="
                            + vents[0].status() + " space=" + vents[1].status() + " metrics="
                            + AtmosphereRuntime.metrics(moon) + " / " + AtmosphereRuntime.metrics(space));
                    return;
                }
                for (OxygenVentBlockEntity vent : List.of(vents[0], vents[1])) {
                    ServerLevel level = (ServerLevel) vent.getLevel();
                    BlockPos head = bedHead(vent.getBlockPos());
                    helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE, "The supplying vent is not active");
                    BlockPos seed = vent.getBlockPos().above();
                    player.teleportTo(level, seed.getX() + 0.5D, seed.getY(), seed.getZ() + 0.5D, 0.0F, 0.0F);
                    tokens.clear();
                    player.startSleepInBed(head);
                    helper.assertTrue(tokens.stream().noneMatch(token -> token.contains(".sleep.")),
                            "A supplied sealed room refused sleep in " + level.dimension().location() + ": " + tokens);
                    try (IncompleteFullChunk incomplete = new IncompleteFullChunk(level, vent.getBlockPos())) {
                        helper.assertTrue(level.hasChunkAt(vent.getBlockPos()) && !incomplete.completed(),
                                "The provider fixture did not keep a visible holder with an incomplete FULL future");
                        helper.assertTrue(AtmosphereRuntime.breathabilityAt(level, head).orElse(null) == BreathabilityState.VACUUM
                                        && !incomplete.completed(),
                                "The provider lookup forced an unfinished vent chunk or granted its unavailable air");
                    }
                }
                if (!pendingPlaced[0]) {
                    // Delay creation until both supplied rooms are qualified; the production scan then begins.
                    // 343 traversable cells exceed one task's unchanged 256-observation budget.
                    vents[2] = roomWithBed(moon, pendingVent, 4, 8);
                    pendingPlaced[0] = true;
                    com.mojang.logging.LogUtils.getLogger().info("W78 placed pending tick={} serverTick={} metrics={}",
                            helper.getTick(), server.getTickCount(), AtmosphereRuntime.metrics(moon));
                    return;
                }
                BlockPos pendingHead = bedHead(pendingVent);
                helper.assertTrue(AtmosphereRuntime.breathabilityAt(moon, pendingHead).orElse(null) == BreathabilityState.PENDING,
                        "The large room was not pending after one production scan tick: " + AtmosphereRuntime.metrics(moon));
                player.teleportTo(moon, pendingVent.getX() + 0.5D, pendingVent.getY() + 1, pendingVent.getZ() + 0.5D,
                        0.0F, 0.0F);
                tokens.clear();
                var refused = player.startSleepInBed(pendingHead);
                helper.assertTrue(refused.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_HERE
                                && tokens.contains(actionBar(SleepRespawnService.sleepKey(SleepRefusal.PENDING))),
                        "A pending room admitted sleep: " + tokens);
                finished[0] = true;
                cleanup.run();
                helper.succeed();
            } catch (RuntimeException failure) {
                if (!finished[0]) {
                    finished[0] = true;
                    try {
                        cleanup.run();
                    } catch (RuntimeException cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                throw failure;
            }
        });
    }

    /**
     * Breathable Tau Ceti f: the registered listener admits the bed. While
     * the dimension type is not natural, vanilla itself still refuses with NOT_POSSIBLE_HERE.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void breathablePlanetBedIsAdmittedAndKeepsTheSavedPoint(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel planet = NonPlayerAtmosphereGameTests.level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos head = new BlockPos(column.getX(), SURFACE_Y, column.getZ());
        BlockPos home = helper.absolutePos(new BlockPos(2, 1, 2));
        List<String> tokens = new ArrayList<>();
        ServerPlayer player = join(helper, "ARCEW37Planet", tokens);
        Runnable bed = placeBedOnPlatform(planet, head);
        try {
            player.setRespawnPosition(Level.OVERWORLD, home, 0.0F, false, false);
            player.teleportTo(planet, head.getX() + 1.5D, head.getY(), head.getZ() + 1.5D, 0.0F, 0.0F);
            Either<Player.BedSleepingProblem, Unit> result = player.startSleepInBed(head);
            helper.assertTrue(tokens.stream().noneMatch(token -> token.contains(".sleep.")),
                    "A breathable planet bed was refused by ARCE: " + tokens);
            helper.assertTrue(player.getRespawnDimension() == Level.OVERWORLD && home.equals(player.getRespawnPosition()),
                    "An off-world bed replaced the saved point: " + player.getRespawnDimension().location() + " "
                            + player.getRespawnPosition());
            if (planet.dimensionType().natural()) {
                helper.assertTrue(player.isSleeping()
                                || result.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_NOW,
                        "A natural breathable planet refused sleep: " + result);
                player.stopSleepInBed(true, true);
            } else {
                helper.assertTrue(result.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_HERE
                        && !player.isSleeping(), "The unnatural dimension type did not keep vanilla's refusal: " + result);
                // W37F R4: a setter AFTER the returned sleep operation is unrelated and must not be cancelled.
                player.setRespawnPosition(ExoplanetContent.TAU_CETI_F_LEVEL, head, 37.5F, false, false);
                helper.assertTrue(player.getRespawnDimension() == ExoplanetContent.TAU_CETI_F_LEVEL
                                && head.equals(player.getRespawnPosition()) && player.getRespawnAngle() == 37.5F,
                        "An unrelated same-tuple setter was cancelled after the sleep call returned");
            }
            // An unrelated forced point (as /spawnpoint) is also saved.
            player.setRespawnPosition(ExoplanetContent.TAU_CETI_F_LEVEL, head, 0.0F, true, false);
            helper.assertTrue(player.getRespawnDimension() == ExoplanetContent.TAU_CETI_F_LEVEL
                    && head.equals(player.getRespawnPosition()) && player.isRespawnForced(),
                    "A forced off-world point was vetoed like a bed operation");
        } finally {
            server.getPlayerList().remove(player);
            bed.run();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void overworldBedRulesAreUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos head = helper.absolutePos(new BlockPos(9, 1, 8));
        List<String> tokens = new ArrayList<>();
        ServerPlayer player = ConnectedTestPlayers.join(server, UUID.randomUUID(), "ARCEW37Home", level,
                helper.absolutePos(new BlockPos(10, 1, 9)), new ArrayList<>(), false, tokens);
        level.setBlock(head.west(), bed(BedPart.FOOT), Block.UPDATE_ALL);
        level.setBlock(head, bed(BedPart.HEAD), Block.UPDATE_ALL);
        CelestialCatalogManager vacuum = catalogs(vacuumEarth());
        AtmosphereManager vacuumAir = new AtmosphereManager(new CelestialEnvironmentService(vacuum));
        try {
            // Even an atmosphere that calls the Overworld vacuum does not bring it under the off-world sleep rule.
            SleepRespawnService fixture = new SleepRespawnService(vacuumAir, vacuum, () -> new Options(true, true));
            helper.assertTrue(vacuumAir.breathabilityAt(level, head) == BreathabilityState.VACUUM
                    && !fixture.offWorldHost(Level.OVERWORLD), "The vacuum Overworld fixture is not as expected");
            PlayerSleepInBedEvent ignored = new PlayerSleepInBedEvent(player, Optional.of(head));
            fixture.onSleepInBed(ignored);
            helper.assertTrue(ignored.getResultStatus() == null && tokens.isEmpty(), "The Overworld bed was judged");

            boolean day = level.isDay();
            Either<Player.BedSleepingProblem, Unit> result = player.startSleepInBed(head);
            helper.assertTrue(tokens.stream().noneMatch(token -> token.contains(ModIdentity.MOD_ID)),
                    "An ARCE message reached an Overworld bed: " + tokens);
            helper.assertTrue(player.getRespawnDimension() == Level.OVERWORLD && head.equals(player.getRespawnPosition()),
                    "The Overworld bed did not set the respawn point as vanilla does");
            helper.assertTrue(day ? result.left().orElse(null) == Player.BedSleepingProblem.NOT_POSSIBLE_NOW
                            : result.right().isPresent() && player.isSleeping(),
                    "The Overworld bed result differs from vanilla's time rule: day=" + day + " " + result);
            if (player.isSleeping()) {
                player.stopSleepInBed(true, true);
            }
        } finally {
            vacuumAir.clear();
            server.getPlayerList().remove(player);
            level.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(head.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void bothOptionsOffRespawnInTheOverworldAndKeepThePoint(GameTestHelper helper) {
        ServerLevel planet = NonPlayerAtmosphereGameTests.level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos head = new BlockPos(column.getX(), SURFACE_Y, column.getZ());
        Runnable bed = placeBedOnPlatform(planet, head);
        withOptions(false, false, helper, "ARCEW37Off", (player, respawns) -> {
            player.setRespawnPosition(ExoplanetContent.TAU_CETI_F_LEVEL, head, 0.0F, false, false);
            ServerPlayer reborn = respawns.respawn(player);
            assertOverworldFallback(helper, reborn, ExoplanetContent.TAU_CETI_F_LEVEL, head, false, RespawnOutcome.DISABLED);
        }, bed);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void allowUsesABreathablePlanetBedButNotAnUnbreathableOne(GameTestHelper helper) {
        ServerLevel planet = NonPlayerAtmosphereGameTests.level(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
        ServerLevel moon = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.MOON_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 4));
        BlockPos planetBed = new BlockPos(column.getX(), SURFACE_Y, column.getZ());
        BlockPos moonBed = new BlockPos(column.getX(), MOON_Y, column.getZ());
        List<Runnable> beds = List.of(placeBedOnPlatform(planet, planetBed), placeBedOnPlatform(moon, moonBed));
        withOptions(true, false, helper, "ARCEW37Allow", (player, respawns) -> {
            player.setRespawnPosition(ExoplanetContent.TAU_CETI_F_LEVEL, planetBed, 0.0F, false, false);
            ServerPlayer reborn = respawns.respawn(player);
            assertArrivedAt(helper, reborn, planet, planetBed);
            reborn.setRespawnPosition(CelestialIds.MOON_LEVEL, moonBed, 0.0F, false, false);
            ServerPlayer refused = respawns.respawn(reborn);
            assertOverworldFallback(helper, refused, CelestialIds.MOON_LEVEL, moonBed, false, RespawnOutcome.UNBREATHABLE);
        }, () -> beds.forEach(Runnable::run));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void forceSkipsOnlyTheAirCheck(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel moon = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.MOON_LEVEL);
        ServerLevel space = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.SPACE_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(3, 0, 4));
        BlockPos vacuumBed = new BlockPos(column.getX(), MOON_Y, column.getZ());
        BlockPos floodedBed = new BlockPos(column.getX() + 8, MOON_Y, column.getZ());
        List<Runnable> cleanup = new ArrayList<>();
        BlockPos[] stationBeds = new BlockPos[1];
        try {
            cleanup.add(placeBedOnPlatform(moon, vacuumBed));
            cleanup.add(placeBedOnPlatform(moon, floodedBed));
            cleanup.add(flood(moon, floodedBed));
            StationRegistrySavedData stations = StationRegistrySavedData.get(server);
            stationBeds[0] = commitStation(helper, stations, UUID.randomUUID(), UUID.randomUUID(), cleanup);
            cleanup.add(placeBedOnPlatform(space, stationBeds[0]));
            helper.assertTrue(moon.getChunkSource().getChunkNow(FAR.getX() >> 4, FAR.getZ() >> 4) == null,
                    "The far Moon chunk was loaded before the test");
        } catch (RuntimeException failed) {
            cleanup.forEach(Runnable::run);
            throw failed;
        }
        BlockPos stationBed = stationBeds[0];
        withOptions(true, true, helper, "ARCEW37Force", (player, respawns) -> {
            player.setRespawnPosition(CelestialIds.MOON_LEVEL, vacuumBed, 0.0F, false, false);
            ServerPlayer airless = respawns.respawn(player);
            assertArrivedAt(helper, airless, moon, vacuumBed);

            airless.setRespawnPosition(CelestialIds.MOON_LEVEL, floodedBed, 0.0F, false, false);
            ServerPlayer flooded = respawns.respawn(airless);
            assertOverworldFallback(helper, flooded, CelestialIds.MOON_LEVEL, floodedBed, false, RespawnOutcome.HAZARD);

            flooded.setRespawnPosition(CelestialIds.MOON_LEVEL, FAR, 0.0F, false, false);
            ServerPlayer unloaded = respawns.respawn(flooded);
            assertOverworldFallback(helper, unloaded, CelestialIds.MOON_LEVEL, FAR, false, RespawnOutcome.UNLOADED);
            helper.assertTrue(moon.getChunkSource().getChunkNow(FAR.getX() >> 4, FAR.getZ() >> 4) == null,
                    "The unloaded arrival was loaded by the respawn");

            unloaded.setRespawnPosition(CelestialIds.SPACE_LEVEL, stationBed, 0.0F, false, false);
            ServerPlayer stranger = respawns.respawn(unloaded);
            assertOverworldFallback(helper, stranger, CelestialIds.SPACE_LEVEL, stationBed, false,
                    RespawnOutcome.STATION_DENIED);
        }, () -> cleanup.forEach(Runnable::run));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void aMissingBodyFallsBackEvenWhenForced(GameTestHelper helper) {
        BlockPos point = new BlockPos(0, 64, 0);
        helper.assertTrue(helper.getLevel().getServer().getLevel(MISSING_BODY) == null, "The missing body exists");
        withOptions(true, true, helper, "ARCEW37Missing", (player, respawns) -> {
            player.setRespawnPosition(MISSING_BODY, point, 0.0F, true, false);
            ServerPlayer reborn = respawns.respawn(player);
            assertOverworldFallback(helper, reborn, MISSING_BODY, point, true, RespawnOutcome.MISSING_BODY);
        }, () -> { });
        helper.succeed();
    }

    /** Respawns through the real {@code PlayerList.respawn}, as the death screen's respawn packet does. */
    interface Respawns {
        ServerPlayer respawn(ServerPlayer player);
    }

    interface RespawnSteps {
        void run(ServerPlayer player, Respawns respawns);
    }

    /** Holds both options in memory for one synchronous test body; the newest player and the fixtures are released. */
    static void withOptions(boolean allow, boolean force, GameTestHelper helper, String name, RespawnSteps steps,
                                    Runnable cleanup) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer[] newest = {join(helper, name, new ArrayList<>())};
        SwitchOverrides.set(CommonConfig.ALLOW_PLANET_RESPAWN, allow);
        SwitchOverrides.set(CommonConfig.FORCE_PLANET_RESPAWN, force);
        try {
            helper.assertTrue(CommonConfig.allowPlanetRespawn() == allow && CommonConfig.forcePlanetRespawn() == force,
                    "The planet-respawn overrides did not apply");
            chat(newest[0]);
            steps.run(newest[0], player -> {
                ServerPlayer reborn = server.getPlayerList().respawn(player, false);
                reborn.connection.player = reborn;
                newest[0] = reborn;
                return reborn;
            });
        } finally {
            SwitchOverrides.clear(CommonConfig.ALLOW_PLANET_RESPAWN);
            SwitchOverrides.clear(CommonConfig.FORCE_PLANET_RESPAWN);
            try {
                server.getPlayerList().remove(newest[0]);
            } finally {
                cleanup.run();
            }
        }
    }

    private static void assertArrivedAt(GameTestHelper helper, ServerPlayer reborn, ServerLevel level, BlockPos bed) {
        List<String> chat = chat(reborn);
        helper.assertTrue(reborn.serverLevel() == level && reborn.position().closerThan(Vec3.atCenterOf(bed), 3.0D),
                "The accepted respawn did not arrive beside its bed: " + reborn.serverLevel().dimension().location()
                        + " " + reborn.position());
        helper.assertTrue(reborn.getRespawnDimension() == level.dimension() && bed.equals(reborn.getRespawnPosition()),
                "The accepted respawn lost its saved point");
        helper.assertTrue(chat.stream().noneMatch(token -> token.startsWith(RESPAWN_TOKEN)),
                "An accepted respawn reported a fallback: " + chat);
    }

    static void assertOverworldFallback(GameTestHelper helper, ServerPlayer reborn, ResourceKey<Level> savedLevel,
                                                BlockPos saved, boolean forced, RespawnOutcome outcome) {
        List<String> chat = chat(reborn);
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(reborn.serverLevel() == server.overworld(), outcome + " did not use the Overworld: "
                + reborn.serverLevel().dimension().location());
        helper.assertTrue(reborn.getRespawnDimension() == savedLevel && saved.equals(reborn.getRespawnPosition())
                        && reborn.isRespawnForced() == forced,
                outcome + " did not keep the saved point: " + reborn.getRespawnDimension().location() + " "
                        + reborn.getRespawnPosition());
        helper.assertTrue(chat.contains(SleepRespawnService.respawnKey(outcome)),
                "The fallback did not report " + outcome + ": " + chat);
    }

    /** The chat tokens sent to this player's embedded connection since the last call, which are then discarded. */
    static List<String> chat(ServerPlayer player) {
        List<String> tokens = new ArrayList<>();
        if (player.connection.connection.channel() instanceof EmbeddedChannel channel) {
            for (Object message : channel.outboundMessages()) {
                if (message instanceof ClientboundSystemChatPacket packet && !packet.overlay()) {
                    tokens.add(ConnectedTestPlayers.token(packet.content()));
                }
            }
            channel.releaseOutbound();
        }
        return tokens;
    }

    private static ServerPlayer join(GameTestHelper helper, String name, List<String> tokens) {
        return ConnectedTestPlayers.join(helper.getLevel().getServer(), UUID.randomUUID(), name, helper.getLevel(),
                helper.absolutePos(new BlockPos(1, 1, 1)), new ArrayList<>(), false, tokens);
    }

    private static String actionBar(String key) {
        return ConnectedTestPlayers.ACTION_BAR + key;
    }

    /** Reserves and commits a station owned by {@code owner}; returns a bed position at its landing pad. */
    static BlockPos commitStation(GameTestHelper helper, StationRegistrySavedData stations, UUID stationId,
                                          UUID owner, List<Runnable> cleanup) {
        StationReservation reservation = stations.reserve(stationId, owner, "W37 " + stationId.toString().substring(0, 8),
                CelestialIds.EARTH_ID, helper.getLevel().getGameTime());
        boolean[] committed = {false};
        cleanup.add(() -> {
            if (committed[0]) {
                stations.delete(stationId);
            } else {
                stations.release(stationId);
            }
        });
        stations.commit(stationId);
        committed[0] = true;
        BlockPos bed = new BlockPos(reservation.landingPad().x() + 2, SPACE_Y, reservation.landingPad().z() + 2);
        helper.assertTrue(stations.findAt(bed.getX(), bed.getZ()).isPresent()
                        && stations.findAt(bed.getX() - 1, bed.getZ()).isPresent(),
                "The station bed lies outside its committed region");
        return bed;
    }

    /**
     * A vanilla bed facing east (head here, foot to the west) on a stone floor one block wider on every side, with
     * air above. Every chunk within the stand-up reach is loaded first (fixture setup only).
     */
    static Runnable placeBedOnPlatform(ServerLevel level, BlockPos head) {
        int reach = SleepRespawnService.ARRIVAL_REACH;
        for (int x = (head.getX() - reach - 1) >> 4; x <= (head.getX() + reach) >> 4; x++) {
            for (int z = (head.getZ() - reach) >> 4; z <= (head.getZ() + reach) >> 4; z++) {
                level.getChunk(x, z);
            }
        }
        BlockPos minimum = head.offset(-2, -1, -1);
        BlockPos maximum = head.offset(1, 2, 1);
        for (BlockPos cell : BlockPos.betweenClosed(minimum, maximum)) {
            level.setBlock(cell, (cell.getY() == minimum.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        level.setBlock(head.west(), bed(BedPart.FOOT), Block.UPDATE_ALL);
        level.setBlock(head, bed(BedPart.HEAD), Block.UPDATE_ALL);
        return () -> clearBox(level, minimum, maximum);
    }

    /** Water sources in every stand-up cell around the bed, so vanilla's arrival is inside a liquid. */
    private static Runnable flood(ServerLevel level, BlockPos head) {
        List<BlockPos> water = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(head.offset(-2, 0, -1), head.offset(1, 0, 1))) {
            if (!(level.getBlockState(cell).getBlock() instanceof BedBlock)) {
                level.setBlock(cell, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                water.add(cell.immutable());
            }
        }
        return () -> water.forEach(cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
    }

    private static BlockState bed(BedPart part) {
        return Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST).setValue(BedBlock.PART, part);
    }

    static boolean suppliedBed(ServerLevel level, BlockPos vent) {
        BlockPos head = bedHead(vent);
        return AtmosphereRuntime.breathabilityAt(level, head).orElse(null) == BreathabilityState.BREATHABLE
                && AtmosphereRuntime.breathabilityAt(level, head.west()).orElse(null) == BreathabilityState.BREATHABLE
                && AtmosphereRuntime.suppliedVolumes(level).values().stream().anyMatch(volume ->
                    volume.cells().contains(new io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition(
                            head.getX(), head.getY(), head.getZ())));
    }

    /** The bed head inside a room whose vent is in the floor at {@code vent}; the cell above the vent stays clear. */
    static BlockPos bedHead(BlockPos vent) {
        return vent.offset(1, 1, 1);
    }

    /** A sealed iron room with its vent in the floor centre and an east-facing bed beside the vent's cell. */
    static OxygenVentBlockEntity roomWithBed(ServerLevel level, BlockPos vent, int radius, int height) {
        AtmosphereGameTests.buildShell(level, vent.offset(-radius, 0, -radius), vent.offset(radius, height, radius));
        OxygenVentBlockEntity placed = AtmosphereGameTests.placePreparedVent(level, vent);
        BlockPos head = bedHead(vent);
        level.setBlock(head.west(), bed(BedPart.FOOT), Block.UPDATE_ALL);
        level.setBlock(head, bed(BedPart.HEAD), Block.UPDATE_ALL);
        return placed;
    }

    static void force(ServerLevel level, BlockPos center, int radius, Set<ChunkPos> added) {
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    level.setChunkForced(x, z, true);
                    added.add(chunk);
                }
                level.getChunk(x, z);
            }
        }
    }

    /**
     * Clears a fixture box. Bed heads go first: the foot then removes itself, and only a head drops an item, so no
     * item is left behind.
     */
    static void clearBox(ServerLevel level, BlockPos minimum, BlockPos maximum) {
        for (BlockPos cell : BlockPos.betweenClosed(minimum, maximum)) {
            BlockState state = level.getBlockState(cell);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos cell : BlockPos.betweenClosed(minimum, maximum)) {
            if (!level.getBlockState(cell).isAir()) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static CelestialCatalogManager catalogs(List<CelestialBodyDefinition> bodies) {
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        if (!catalogs.applyCandidate(CelestialCatalog.create(bodies))) {
            throw new IllegalStateException("Bad W37 fixture catalog");
        }
        return catalogs;
    }

    private static List<CelestialBodyDefinition> vacuumEarth() {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.set(0, NonPlayerAtmosphereGameTests.vacuumEarth());
        return bodies;
    }
}
