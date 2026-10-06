package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual fixed-Level terrain, ordinary transfer and player station paths. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetaryWorldGameTests {
    private PlanetaryWorldGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "planetary_terrain", timeoutTicks = 40)
    public static void planetaryEffectsLoadOnDedicatedServerWithoutChangingWorldSettings(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var surface = io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles.SURFACE_EFFECTS;
        var space = io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles.SPACE_EFFECTS;
        for (var id : List.of(PlanetaryContent.MARS, PlanetaryContent.VENUS, CelestialIds.MOON_ID, CelestialIds.SPACE_ID)) {
            var level = server.getLevel(PlanetaryContent.level(id));
            helper.assertTrue(level != null, "Missing sky fixture Level " + id);
            var type = level.dimensionType();
            boolean orbital = id.equals(CelestialIds.SPACE_ID);
            helper.assertTrue(type.effectsLocation().equals(orbital ? space : surface), "Incorrect effects ID " + id);
            helper.assertTrue(type.minY() == 0 && type.height() == 256 && type.logicalHeight() == 256,
                    "Presentation changed world height");
            helper.assertTrue(type.hasSkyLight() != orbital && !type.hasCeiling(), "Presentation changed skylight/ceiling");
        }
        helper.assertTrue(server.overworld().dimensionType().effectsLocation().toString().equals("minecraft:overworld"),
                "Earth effects changed");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "planetary_terrain", timeoutTicks = 100)
    public static void startupWorldsHaveContrastingGeneratedTerrainAndNoGasLevel(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (var id : List.of(PlanetaryContent.MARS, PlanetaryContent.VENUS)) {
            var world = server.getLevel(PlanetaryContent.level(id));
            helper.assertTrue(world != null, "Missing started planet " + id);
            var heights = new HashSet<Integer>();
            for (int offset : new int[] {0, 32, 64, 96}) {
                int x = 1024 + offset;
                world.getChunkAt(new BlockPos(x, 0, 1024));
                int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, 1024);
                var top = world.getBlockState(new BlockPos(x, y - 1, 1024));
                var rock = world.getBlockState(new BlockPos(x, y - 12, 1024));
                boolean mars = id.equals(PlanetaryContent.MARS);
                helper.assertTrue(y > 4 && y < 240, "Generated terrain height is unsafe");
                // v1.8 (ADR-063 section 5): Mars has ferric sand over red sandstone, Venus is basalt throughout; a
                // volcano's lava pool may also be the top. The base blocks and heights are the v1.4 ones.
                helper.assertTrue(mars ? top.is(SurfaceContent.FERRIC_SAND.get()) : top.is(Blocks.BASALT) || top.is(Blocks.LAVA)
                        || top.is(io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids.ENRICHED_LAVA_BLOCK.get()),
                        "Unexpected planetary surface " + top);
                // A Venus geode's hollow or a volcano's lava can lie under a sampled column.
                helper.assertTrue(rock.is(mars ? Blocks.RED_SANDSTONE : Blocks.BASALT) || rock.is(Blocks.LAVA)
                        || rock.is(io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids.ENRICHED_LAVA_BLOCK.get())
                                || rock.is(Blocks.CAVE_AIR)
                                || rock.is(SurfaceContent.GEODE_SHELL.get()),
                        "Unexpected planetary interior " + rock);
                var biome = world.getBiome(new BlockPos(x, y, 1024)).unwrapKey().orElseThrow().location();
                helper.assertTrue(mars ? biome.getPath().equals("ferric_regolith")
                                : biome.getPath().equals("volcanic") || biome.getPath().equals("volcanic_lowlands"),
                        "Planet uses another biome: " + biome);
                heights.add(y);
                AdvancedRocketryCommunity.LOGGER.info("ARCE_PLANETARY_TERRAIN body={} x={} z=1024 height={} top={} rock={}",
                        id, x, y, top, rock);
            }
            helper.assertTrue(heights.size() > 1, "Planet terrain is flat instead of noise-generated");
        }
        helper.assertTrue(server.getLevel(PlanetaryContent.level(PlanetaryContent.GAS_GIANT)) == null, "Gas giant fabricated a Level");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "planetary_surface_flight", timeoutTicks = 1000)
    public static void earthMarsVenusEarthKeepsOneRocketAndExactFuelDebits(GameTestHelper helper) {
        helper.runAfterDelay(20, () -> {
            helper.setBlock(new BlockPos(4, 2, 3), io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks.ROCKET_FUEL_TANK.get());
            var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), UUID.randomUUID());
            var logical = rocket.assemblyTransactionId().orElseThrow();
            var original = rocket.snapshot().orElseThrow();
            var route = List.of(PlanetaryContent.MARS, PlanetaryContent.VENUS, CelestialIds.EARTH_ID);
            fly(helper, rocket, logical, original.blocks(), route, 0);
        });
    }

    private static void fly(GameTestHelper helper, RocketEntity rocket, UUID logical,
            List<io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock> blocks,
            List<net.minecraft.resources.ResourceLocation> route, int index) {
        var server = helper.getLevel().getServer();
        guarded(server, logical, () -> {
            var before = rocket.flightData().orElseThrow().fuel();
            // Each leg starts with the same tank contents left by the previous one.
            var destination = route.get(index);
            var result = RocketRuntime.requestAdminFlight(rocket, new TravelTarget.BodySurface(destination), UUID.randomUUID());
            helper.assertTrue(result.success(), "Planetary launch failed: " + result.code() + " to " + destination);
            helper.assertTrue(result.requiredFuel() > 0, "Planetary flight omitted its fuel debit");
            // Successful native preparation has already initialized this server's journal.
            var journal = RocketTransferSavedData.get(server);
            helper.runAfterDelay(270, () -> guarded(server, logical, () -> {
                var matches = new ArrayList<RocketEntity>();
                for (var level : server.getAllLevels()) {
                    for (var entity : level.getAllEntities()) {
                        if (entity instanceof RocketEntity found && found.assemblyTransactionId().filter(logical::equals).isPresent()) {
                            matches.add(found);
                        }
                    }
                }
                RocketEntity landed;
                RocketFlightData observedState = null;
                try {
                    helper.assertTrue(matches.size() == 1, "Planetary transfer did not retain exactly one logical rocket");
                    landed = matches.get(0);
                    var state = landed.flightData().orElseThrow();
                    observedState = state;
                    helper.assertTrue(state.state() == RocketFlightState.LANDED && state.currentBody().equals(destination),
                            "Planetary flight did not land at the requested body");
                    helper.assertTrue(state.currentTarget().orElseThrow().equals(new TravelTarget.BodySurface(destination)), "Typed target changed");
                    helper.assertTrue(state.fuel().amount() == before.amount() - result.requiredFuel(), "Planetary fuel debit differs");
                    helper.assertTrue(landed.snapshot().orElseThrow().blocks().equals(blocks), "Travel changed the rocket blocks or payloads");
                } catch (RuntimeException | Error failure) {
                    observePlanetaryFailure(helper, journal, logical, destination, index, matches, observedState);
                    throw failure;
                }
                if (index + 1 < route.size()) {
                    fly(helper, landed, logical, blocks, route, index + 1);
                } else {
                    cleanRocket(server, logical);
                    helper.succeed();
                }
            }));
        });
    }

    /** Failure-only scalar observation: no tickets, world scans, ticking, or journal writes. */
    private static void observePlanetaryFailure(GameTestHelper helper, RocketTransferSavedData journal,
            UUID logical, net.minecraft.resources.ResourceLocation requested, int leg,
            List<RocketEntity> matches, RocketFlightData observedState) {
        try {
            var record = journal.findByLogicalRocket(logical).orElse(null);
            var matched = matches.size() == 1 ? matches.get(0) : null;
            var origin = record == null ? null : record.destinationSnapshot().sourceOrigin();
            var server = helper.getLevel().getServer();
            var destinationLevel = record == null ? null : server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, record.destinationSnapshot().sourceDimension()));
            var sourceLevel = record == null ? null : server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, record.sourceSnapshot().sourceDimension()));
            boolean loaded = destinationLevel != null && destinationLevel.getChunkSource()
                    .getChunkNow(origin.x() >> 4, origin.z() >> 4) != null;
            boolean entitiesLoaded = destinationLevel != null && destinationLevel.areEntitiesLoaded(
                    net.minecraft.world.level.ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4));
            boolean ticking = destinationLevel != null && destinationLevel.isPositionEntityTicking(
                    new BlockPos(origin.x(), origin.y(), origin.z()));
            AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_PLANETARY_FAILURE leg={} logical={} requested={} matches={} test_tick={} "
                            + "entity={} level={} level_time={} state={} body={} state_started={} "
                            + "journal_operational={} phase={} transfer={} source_entity={} destination_entity={} "
                            + "source_time={} destination_level={} origin={},{},{} destination_time={} "
                            + "scheduled_transit={} scheduled_descent={} loaded={} entities_loaded={} ticking={}",
                    leg, logical, planetaryDiagnosticId(requested), matches.size(), helper.getTick(),
                    matched == null ? "none" : matched.getUUID(),
                    matched == null ? "none" : planetaryDiagnosticId(matched.level().dimension().location()),
                    matched == null ? -1L : matched.level().getGameTime(),
                    observedState == null ? "none" : observedState.state().name(),
                    observedState == null ? "none" : planetaryDiagnosticId(observedState.currentBody()),
                    observedState == null ? -1L : observedState.stateStartedGameTime(),
                    journal.operational(), record == null ? "none" : record.phase().name(),
                    record == null ? "none" : record.transferId(), record == null ? "none" : record.sourceEntityId(),
                    record == null ? "none" : record.destinationEntityId().map(UUID::toString).orElse("none"),
                    sourceLevel == null ? -1L : sourceLevel.getGameTime(),
                    record == null ? "none" : planetaryDiagnosticId(record.destinationSnapshot().sourceDimension()),
                    origin == null ? 0 : origin.x(), origin == null ? 0 : origin.y(), origin == null ? 0 : origin.z(),
                    destinationLevel == null ? -1L : destinationLevel.getGameTime(),
                    record == null ? -1L : record.sourceFlightData().stateStartedGameTime(),
                    record == null ? -1L : record.destinationFlightData().stateStartedGameTime(),
                    loaded, entitiesLoaded, ticking);
        } catch (RuntimeException | Error observationFailure) {
            // Observation failures must never replace or decorate the original assertion.
            try {
                AdvancedRocketryCommunity.LOGGER.warn("ARCE_PLANETARY_FAILURE observation_unavailable");
            } catch (RuntimeException | Error loggingFailure) {
                // Retain the original assertion even when the logger itself is unavailable.
            }
        }
        // BEGIN transfer service diagnostics
        observeServiceFailure(helper, journal, logical);
        // END transfer service diagnostics
    }

    // BEGIN transfer service diagnostics
    /** One extra failure line; neither logging attempt retries nor world activation are permitted. */
    private static void observeServiceFailure(GameTestHelper helper, RocketTransferSavedData journal, UUID logical) {
        String line;
        try {
            var record = journal == null ? null : journal.findByLogicalRocket(logical).orElse(null);
            UUID transferId = record == null ? null : record.transferId();
            String state = transferId == null ? "installed=UNOBSERVED"
                    : RocketRuntime.transferFailureDiagnostics(transferId);
            line = "ARCE_TRANSFER_SERVICE_FAILURE fixture=PLANETARY transfer="
                    + (transferId == null ? "UNOBSERVED" : transferId.toString())
                    + " current_server_tick=" + helper.getLevel().getServer().getTickCount() + " " + state;
            if (line.length() > 512) {
                line = "ARCE_TRANSFER_SERVICE_FAILURE fixture=PLANETARY diagnostic=UNAVAILABLE";
            } else {
                for (int index = 0; index < line.length(); index++) {
                    if (line.charAt(index) < 32 || line.charAt(index) > 126) {
                        line = "ARCE_TRANSFER_SERVICE_FAILURE fixture=PLANETARY diagnostic=UNAVAILABLE";
                        break;
                    }
                }
            }
        } catch (RuntimeException | Error observationFailure) {
            line = "ARCE_TRANSFER_SERVICE_FAILURE fixture=PLANETARY diagnostic=UNAVAILABLE";
        }
        try {
            AdvancedRocketryCommunity.LOGGER.warn("{}", line);
        } catch (RuntimeException | Error loggingFailure) {
            // Preserve the original assertion and cleanup; do not retry a possibly partial logging write.
        }
    }
    // END transfer service diagnostics

    private static String planetaryDiagnosticId(net.minecraft.resources.ResourceLocation id) {
        String value = id.toString();
        return value.length() <= 96 ? value : "overlong";
    }

    private static void guarded(net.minecraft.server.MinecraftServer server, UUID logical, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error exception) {
            cleanRocket(server, logical);
            throw exception;
        }
    }

    static void cleanRocket(net.minecraft.server.MinecraftServer server, UUID logical) {
        var journal = RocketTransferSavedData.get(server);
        journal.findByLogicalRocket(logical).ifPresent(record -> journal.remove(record.transferId()));
        journal.flush(server);
        var owned = new ArrayList<RocketEntity>();
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof RocketEntity rocket && rocket.assemblyTransactionId().filter(logical::equals).isPresent()) {
                    owned.add(rocket);
                }
            }
        }
        owned.forEach(RocketEntity::discard);
    }

    @GameTest(template = "rocket_test", batch = "planetary_player_station", timeoutTicks = 100)
    public static void playerOnEachPlanetCreatesItsOrbitStationButSpaceIsNotASurface(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var catalogs = new CelestialCatalogManager();
        var bodies = new ArrayList<>(io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        helper.assertTrue(catalogs.applyCandidate(io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog.create(bodies)),
                "Invalid station fixture catalog");
        var manager = new StationManager(catalogs);
        var registry = StationRegistrySavedData.get(server);
        for (var id : List.of(PlanetaryContent.MARS, PlanetaryContent.VENUS)) {
            var world = server.getLevel(PlanetaryContent.level(id));
            var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "PlanetStation"));
            var result = manager.createForPlayer(player);
            helper.assertTrue(result.success(), "Planet player could not create station: " + result.code());
            var station = result.station().orElseThrow();
            try {
                helper.assertTrue(station.orbitBody().equals(id) && station.ownerId().equals(player.getUUID()), "Wrong orbit/owner");
                helper.assertTrue(registry.find(station.stationId()).orElseThrow().equals(station), "Station was not committed");
                helper.assertTrue(manager.createForPlayer(player).code() == StationCreationCode.OWNER_LIMIT_REACHED, "Player station limit changed");
            } finally {
                registry.delete(station.stationId());
                new StationPlatformGenerator().removeTemplate(server.getLevel(CelestialIds.SPACE_LEVEL), station.cell());
                registry.flush(server);
            }
        }
        var before = registry.save(new CompoundTag());
        var spacePlayer = new FakePlayer(server.getLevel(CelestialIds.SPACE_LEVEL), new GameProfile(UUID.randomUUID(), "SpaceStation"));
        helper.assertTrue(manager.createForPlayer(spacePlayer).code() == StationCreationCode.INVALID_SOURCE, "Space became a surface origin");
        helper.assertTrue(registry.save(new CompoundTag()).equals(before), "Denied creation changed station authority");
        registry.flush(server);
        helper.succeed();
    }
}
