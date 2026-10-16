package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ResearchEnvironmentGameTests {
    @GameTest(template = "empty", batch = "research_environment", timeoutTicks = 400)
    public static void dayRainCoveredSkyAndUnloadedCellsPauseWithoutPayment(GameTestHelper helper) {
        var fixture = ResearchGameTests.fixture(helper, false, Direction.NORTH, UUID.randomUUID(), 0);
        var service = ResearchRuntime.service(fixture.level); BlockPos roof = fixture.lens().above(3);
        ResearchGameTests.steps(helper, ResearchGameTests.ticking(List.of(fixture), true), ResearchGameTests.then(() -> {
            var owner = fixture.controller.researchOwner().orElseThrow();
            ResearchGameTests.night(fixture); helper.assertTrue(service.intent(fixture.controller, owner, 0), "Environment START refused"); fixture.flush(); fixture.feed(1_000);
            fixture.level.setDayTime(6_000); fixture.level.updateSkyBrightness();
            helper.assertTrue(!service.advance(fixture.controller) && fixture.controller.researchStatus() == ResearchStatus.DAYTIME, "Daytime paid");
            fixture.level.setDayTime(18_000); fixture.level.setWeatherParameters(0, 100_000, true, false);
            fixture.level.setRainLevel(1); fixture.level.updateSkyBrightness();
            helper.assertTrue(!service.advance(fixture.controller) && fixture.controller.researchStatus() == ResearchStatus.WEATHER, "Rain paid");
            ResearchGameTests.night(fixture); fixture.level.setRainLevel(0); fixture.level.updateSkyBrightness();
            var chunk = new net.minecraft.world.level.ChunkPos(fixture.origin); fixture.controller.chunkUnavailable(chunk);
            helper.assertTrue(!service.advance(fixture.controller) && fixture.controller.researchStatus() == ResearchStatus.UNLOADED
                    && fixture.controller.storedEnergy() == 1_000
                    && fixture.power.get(0).energyStored() == 1_000, "Unavailable cell paid or loaded");
            fixture.controller.chunkAvailable(chunk); fixture.revalidate();
            // Keep work paused while the asynchronous light engine incorporates the new roof.
            fixture.level.setDayTime(6_000); fixture.level.updateSkyBrightness();
            fixture.level.setBlock(roof, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }), () -> !fixture.level.canSeeSky(fixture.lens()), ResearchGameTests.then(() -> {
            try (fixture) {
                ResearchGameTests.night(fixture);
                helper.assertTrue(!service.advance(fixture.controller) && fixture.controller.researchStatus() == ResearchStatus.NO_SKY,
                        "Covered lens paid: " + fixture.controller.researchStatus());
                helper.assertTrue(fixture.power.get(0).energyStored() == 1_000 && service.work(fixture.controller).orElseThrow().surveyProgress() == 0,
                        "Paused environment changed FE/progress");
            } finally { fixture.level.setBlock(roof, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); }
            helper.succeed();
        }));
    }
    /**
     * W21c review L5: a real ticket change takes a footprint chunk out of FULL while the queued controller's own chunk
     * stays FULL. The kernel sees no Unload event, so only the footprint scan stands between it and a payment.
     */
    @GameTest(template = "empty", batch = "research_unload", timeoutTicks = 600)
    public static void realFootprintChunkUnloadPausesWithoutPaymentOrLoading(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); var moon = server.getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level unavailable");
        // The plug (local x 0) and controller (x 1) sit in chunk home; the x 2 slabs and casing cross into chunk edge.
        // Nothing else holds tickets this far out on the Moon, so only this fixture keeps either chunk FULL.
        var origin = new BlockPos(-2_033, 128, -2_040);
        var fixture = new ResearchFixture(moon, origin, true, Direction.NORTH, UUID.randomUUID());
        var home = new ChunkPos(origin); var edge = new ChunkPos(origin.east());
        // Vanilla ticks no block entity in a Level without players or forced chunks. One forced chunk eight chunks
        // away keeps the Moon ticking without making either fixture chunk FULL.
        var keeper = new ChunkPos(home.x - 8, home.z); boolean ownsKeeper = !moon.getForcedChunks().contains(keeper.toLong());
        if (ownsKeeper) { moon.setChunkForced(keeper.x, keeper.z, true); }
        Runnable release = () -> { if (ownsKeeper) { moon.setChunkForced(keeper.x, keeper.z, false); } };
        var data = SatelliteMissionSavedData.get(server); var service = ResearchRuntime.service(moon); int[] at = new int[3];
        ResearchGameTests.steps(helper, () -> { fixture.close(); release.run(); }, ResearchGameTests.ticking(List.of(fixture), false), ResearchGameTests.then(() -> {
            helper.assertTrue(!home.equals(edge) && fixture.cells.stream().anyMatch(cell -> new ChunkPos(cell).equals(edge))
                    && new ChunkPos(fixture.power.get(0).getBlockPos()).equals(home), "Fixture does not cross into a plug-free chunk");
            UUID owner = fixture.controller.researchOwner().orElseThrow(), job = UUID.randomUUID();
            var source = new GroundSurveyJob.Device(UUID.randomUUID(), moon.dimension().location(), 0);
            long now = server.overworld().getGameTime(), created = data.groundSurveyQuery(jobs -> jobs.previewTime(now));
            var instance = new AsteroidInstance(2, UUID.randomUUID(), owner, CelestialIds.EARTH_ID, ModIdentity.id("small_asteroid"),
                    "0123456789abcdef", "0123456789abcdef", 1, List.of(new RewardEntry(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_ore"), 1)),
                    created, OptionalLong.empty(), InstanceState.PENDING, InstanceOrigin.ground(job), Optional.empty());
            helper.assertTrue(data.groundSurveys(jobs -> jobs.start(new GroundSurveys.Start(job, owner, source, instance, 20, 20, 200, 20, 120), now)).changed(), "Unload job refused");
            fixture.flush();
            for (int tick = 0; tick < 20; tick++) { helper.assertTrue(data.groundSurveys(jobs -> jobs.advanceSurvey(job, source, 24_000, now)).changed(), "Unload survey refused"); }
            helper.assertTrue(service.intent(fixture.controller, owner, 4), "Unload processor claim refused"); fixture.flush(); fixture.feed(4_000);
        }), ResearchGameTests.waitTicks(server, 2), ResearchGameTests.then(() -> {
            at[0] = server.getTickCount(); at[1] = analysis(service, fixture); at[2] = fixture.power.get(0).energyStored();
        }), ResearchGameTests.waitTicks(server, 20), ResearchGameTests.then(() -> {
            int ticks = server.getTickCount() - at[0], paid = analysis(service, fixture) - at[1];
            helper.assertTrue(ticks >= 20 && paid == ticks && at[2] - fixture.power.get(0).energyStored() == ResearchService.ENERGY_PER_TICK * ticks
                    && fixture.controller.researchStatus() == ResearchStatus.QUEUED, "Moon processor natural ticks " + ticks + " paid " + paid);
            // This tick's ticker has queued the processor; the server's END tick serves it after this ticket change.
            fixture.stopTicking(home); fixture.release(edge); fixture.applyTickets();
            helper.assertTrue(moon.getChunkSource().getChunkNow(edge.x, edge.z) == null && moon.getChunkSource().getChunkNow(home.x, home.z) != null
                    && fixture.controller.physical() && fixture.controller.formed(), "The ticket change did not take out only the edge chunk");
            at[1] = analysis(service, fixture); at[2] = fixture.power.get(0).energyStored();
        }), ResearchGameTests.waitTicks(server, 20), ResearchGameTests.then(() -> {
            helper.assertTrue(fixture.controller.researchStatus() == ResearchStatus.UNLOADED, "Unloaded footprint read " + fixture.controller.researchStatus());
            helper.assertTrue(analysis(service, fixture) == at[1] && fixture.power.get(0).energyStored() == at[2], "Unloaded footprint paid");
            helper.assertTrue(moon.getChunkSource().getChunkNow(edge.x, edge.z) == null && !moon.isPositionEntityTicking(origin),
                    "Research loaded or ticked the unloaded footprint");
            fixture.restoreTickets(); fixture.applyTickets();
        }), () -> analysis(service, fixture) > at[1], ResearchGameTests.then(() -> {
            fixture.close(); release.run(); helper.succeed();
        }));
    }
    private static int analysis(ResearchService service, ResearchFixture fixture) {
        return service.work(fixture.controller).orElseThrow().analysis().progress();
    }
    @GameTest(template = "empty", batch = "research_no_power", timeoutTicks = 400)
    public static void formedObservatoryWithoutPlugCannotPay(GameTestHelper helper) {
        var fixture = ResearchGameTests.fixture(helper, false, Direction.NORTH, UUID.randomUUID(), 0);
        ResearchGameTests.ready(helper, List.of(fixture), true, () -> {
            try (fixture) {
                ResearchGameTests.night(fixture); var service = ResearchRuntime.service(fixture.level);
                var plug = fixture.power.get(0); fixture.level.setBlock(plug.getBlockPos(),
                        io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.MACHINE_CASING.get().defaultBlockState(), Block.UPDATE_ALL);
                fixture.revalidate(); helper.assertTrue(fixture.controller.formed(), "Optional plug removal unformed the observatory");
                helper.assertTrue(service.intent(fixture.controller, fixture.controller.researchOwner().orElseThrow(), 0), "No-power START refused"); fixture.flush();
                helper.assertTrue(!service.advance(fixture.controller) && fixture.controller.researchStatus() == ResearchStatus.NO_POWER
                        && service.work(fixture.controller).orElseThrow().surveyProgress() == 0, "No-plug observatory paid");
            }
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "research_station", timeoutTicks = 400)
    public static void actualStationPermissionAndOrbitSystemControlObservation(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); var data = StationRegistrySavedData.get(server); UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
        data.reserve(id, owner, "Research station", CelestialIds.EARTH_ID, server.overworld().getGameTime()); var station = data.commit(id);
        var pad = station.landingPad(); var space = server.getLevel(CelestialIds.SPACE_LEVEL);
        helper.assertTrue(space != null, "Space Level unavailable");
        var fixture = new ResearchFixture(space, new BlockPos(pad.x(), pad.y() + 6, pad.z()), false, Direction.NORTH, owner);
        var denied = new ResearchFixture(space, fixture.origin.offset(16, 0, 0), false, Direction.NORTH, UUID.randomUUID());
        ResearchGameTests.ready(helper, List.of(fixture, denied), false, () -> {
            try (fixture; denied) {
                var service = ResearchRuntime.service(space);
                helper.assertTrue(service.system(denied.controller).isEmpty() && !service.intent(denied.controller, denied.controller.researchOwner().orElseThrow(), 0), "Station VISIT permission bypassed");
                helper.assertTrue(service.intent(fixture.controller, owner, 0), "Permitted station START refused"); fixture.flush(); fixture.feed(100);
                helper.assertTrue(!space.canSeeSky(fixture.lens()) && service.advance(fixture.controller), "Actual orbit did not skip sky/weather/day");
                data.foldWarpCredits(Map.of(id, 1)); data.flush(server);
                var relocation = data.checkedRelocation(server, data.find(id).orElseThrow(), ModIdentity.id("tau_ceti_f"), 1);
                helper.assertTrue(relocation == StationRegistrySavedData.CheckedUpdate.COMMITTED, "Station relocation did not commit");
                int before = fixture.power.get(0).energyStored();
                helper.assertTrue(!service.advance(fixture.controller) && fixture.controller.researchStatus() == ResearchStatus.SYSTEM_CHANGED
                        && fixture.power.get(0).energyStored() == before, "Relocated station advanced another system's job");
            } finally { data.delete(id); data.flush(server); }
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "research_fairness", timeoutTicks = 600)
    public static void moreThan32ProcessorsShareOneFairBudgetAcrossTwoLevels(GameTestHelper helper) {
        var fixtures = new ArrayList<ResearchFixture>(); var server = helper.getLevel().getServer(); var moon = server.getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level unavailable");
        for (int i = 0; i < 34; i++) {
            var level = i % 2 == 0 ? helper.getLevel() : moon;
            var origin = i % 2 == 0 ? helper.absolutePos(new BlockPos(5 + i / 2 * 16, 6, 5)) : new BlockPos(2_000 + i / 2 * 16, 128, 2_000);
            fixtures.add(new ResearchFixture(level, origin, true, Direction.NORTH, UUID.randomUUID()));
        }
        ResearchGameTests.ready(helper, fixtures, false, () -> {
            try {
                var data = SatelliteMissionSavedData.get(server); var service = ResearchRuntime.service(helper.getLevel());
                for (int index = 0; index < fixtures.size(); index++) {
                    var fixture = fixtures.get(index); UUID owner = readySurvey(helper, fixture, "Fairness");
                    helper.assertTrue(service.intent(fixture.controller, owner, 4), "Fairness processor claim refused: fixture " + index
                            + " in " + fixture.level.dimension().location() + " read " + fixture.controller.researchStatus());
                    fixture.feed(100);
                }
                data.flush(server);
                for (var fixture : fixtures) { service.enqueue(fixture.controller); } service.tick();
                long earthPaid = fixtures.stream().filter(fixture -> fixture.level == helper.getLevel())
                        .filter(fixture -> service.work(fixture.controller).orElseThrow().analysis().progress() == 1).count();
                long moonPaid = fixtures.stream().filter(fixture -> fixture.level == moon)
                        .filter(fixture -> service.work(fixture.controller).orElseThrow().analysis().progress() == 1).count();
                helper.assertTrue(earthPaid == 16 && moonPaid == 16, "Shared paid budget/fairness differs: " + earthPaid + "/" + moonPaid);
                for (var fixture : fixtures) { service.enqueue(fixture.controller); } service.tick();
                helper.assertTrue(fixtures.stream().allMatch(fixture -> service.work(fixture.controller).orElseThrow().analysis().progress() >= 1), "One Level/controller starved");
            } finally { fixtures.forEach(ResearchFixture::close); }
            helper.succeed();
        });
    }
    /**
     * W88: an update beside a formed processor, as falling gravel makes in raw terrain, queues it for revalidation. It
     * reads as unformed until the server's END tick revalidates it, so a claim in that tick is refused, and the shared
     * readiness step must not pass then. The fairness test above builds its Earth fixtures in raw cave terrain; when one
     * stood in a mineshaft, its claim sometimes ran in such a tick.
     */
    @GameTest(template = "empty", batch = "research_neighbour_update", timeoutTicks = 200)
    public static void aNeighbourUpdateHoldsReadinessUntilTheProcessorRevalidates(GameTestHelper helper) {
        var fixture = ResearchGameTests.fixture(helper, true, Direction.NORTH, UUID.randomUUID(), 0);
        var service = ResearchRuntime.service(fixture.level); var ready = ResearchGameTests.ticking(List.of(fixture), false);
        // One block east of the footprint's local x 2 column, outside the pattern.
        BlockPos beside = fixture.world(new PatternPosition(3, 0, 0)); UUID[] owner = new UUID[1];
        Runnable cleanup = () -> { fixture.close(); fixture.level.setBlock(beside, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); };
        ResearchGameTests.steps(helper, cleanup, ready, ResearchGameTests.then(() -> {
            owner[0] = readySurvey(helper, fixture, "Neighbour update");
            fixture.level.setBlock(beside, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(!fixture.controller.formed(), "A neighbour update did not queue the processor for revalidation");
            helper.assertTrue(!ready.run(), "Readiness passed while the processor waits for revalidation");
            helper.assertTrue(!service.intent(fixture.controller, owner[0], 4) && fixture.controller.researchStatus() == ResearchStatus.UNFORMED,
                    "A processor waiting for revalidation took a claim: " + fixture.controller.researchStatus());
        }), ready, ResearchGameTests.then(() -> {
            helper.assertTrue(service.intent(fixture.controller, owner[0], 4), "The revalidated processor refused the claim: "
                    + fixture.controller.researchStatus());
            cleanup.run(); helper.succeed();
        }));
    }
    /** Starts and completes a ground survey for the fixture's owner, so its processor can claim the analysis. */
    private static UUID readySurvey(GameTestHelper helper, ResearchFixture fixture, String label) {
        var server = helper.getLevel().getServer(); var data = SatelliteMissionSavedData.get(server);
        UUID owner = fixture.controller.researchOwner().orElseThrow(), job = UUID.randomUUID();
        var source = new GroundSurveyJob.Device(UUID.randomUUID(), helper.getLevel().dimension().location(), 0);
        long now = server.overworld().getGameTime(), created = data.groundSurveyQuery(jobs -> jobs.previewTime(now));
        var instance = new AsteroidInstance(2, UUID.randomUUID(), owner, CelestialIds.EARTH_ID, ModIdentity.id("small_asteroid"),
                "0123456789abcdef", "0123456789abcdef", 1, List.of(new RewardEntry(net.minecraft.resources.ResourceLocation.tryParse("minecraft:iron_ore"), 1)),
                created, OptionalLong.empty(), InstanceState.PENDING, InstanceOrigin.ground(job), Optional.empty());
        helper.assertTrue(data.groundSurveys(jobs -> jobs.start(new GroundSurveys.Start(job, owner, source, instance, 20, 20, 200, 20, 120), now)).changed(), label + " job refused");
        fixture.flush();
        for (int tick = 0; tick < 20; tick++) { helper.assertTrue(data.groundSurveys(jobs -> jobs.advanceSurvey(job, source, 24_000, now)).changed(), label + " survey refused"); }
        return owner;
    }
    @GameTest(template = "empty", batch = "research_authority", timeoutTicks = 200)
    public static void menuRejectsDistanceSpectatorForeignOwnerAndRapidIntents(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); UUID owner = UUID.randomUUID();
        try (var fixture = ResearchGameTests.fixture(helper, true, Direction.NORTH, owner, 0)) {
            var player = new ServerPlayer(server, fixture.level, new GameProfile(owner, "researchOwner"));
            var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player); player.setGameMode(GameType.CREATIVE);
            try {
                player.teleportTo(fixture.level, fixture.origin.getX() + 0.5, fixture.origin.getY(), fixture.origin.getZ() + 0.5, 0, 0);
                helper.assertTrue(ClassicControllerMenu.canOpen(player, fixture.controller), "Connected nearby owner cannot inspect");
                var menu = new ResearchControllerMenu(1, fixture.controller, player); player.containerMenu = menu;
                player.teleportTo(fixture.level, fixture.origin.getX() + 20, fixture.origin.getY(), fixture.origin.getZ(), 0, 0);
                helper.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, 4), "Distant intent accepted");
                player.teleportTo(fixture.level, fixture.origin.getX() + 0.5, fixture.origin.getY(), fixture.origin.getZ() + 0.5, 0, 0);
                player.setGameMode(GameType.SPECTATOR); helper.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, 4), "Spectator intent accepted");
                player.setGameMode(GameType.CREATIVE); var service = ResearchRuntime.service(fixture.level);
                helper.assertTrue(!service.intent(fixture.controller, UUID.randomUUID(), 4), "Foreign owner accepted");
                helper.assertTrue(service.allowIntent(owner) && !service.allowIntent(owner), "10/s intent spacing not enforced");
                helper.assertTrue(!menu.clickMenuButton(player, -1) && !menu.clickMenuButton(player, 6), "Out-of-range intent accepted");
            } finally { server.getPlayerList().remove(player); channel.close(); }
        }
        helper.succeed();
    }
    private ResearchEnvironmentGameTests() { }
}
