package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.assembleFueledRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearPadRockets;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearTransferJournal;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.findLogicalRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.primePadChunks;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.CraterPiece;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen.CraterShape;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180PlanetWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-075 (replacing ADR-063 section 9's y 80 cases): a rocket arriving on the new Moon terrain lands on the highest
 * column of its footprint, supported, whether the first pad lies over the highest highlands or over a crater. Each case
 * shapes the ground under the first pad, flies a rocket from Earth and checks its lowest block and what is below it.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoonArrivalGameTests {
    private static final int PAD_REACH = 8;
    /** The flat lowland under the crater case. */
    private static final int LOWLAND = 20;

    private MoonArrivalGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "moon_arrival_highlands", timeoutTicks = 1_000)
    public static void aMoonRocketLandsOnTheHighestHighlands(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        fly(helper, driver, moon -> MoonPadArea.shape(moon, PAD_REACH, V180PlanetWorldgen.MOON_SURFACE_MAX),
                V180PlanetWorldgen.MOON_SURFACE_MAX, (moon, landed) -> {
            int lowest = MoonPadArea.lowest(landed);
            helper.assertTrue(lowest == V180PlanetWorldgen.MOON_SURFACE_MAX + 1,
                    "The rocket's lowest block is at y " + lowest + ", not on the highlands at y 37");
            boolean turf = MoonPadArea.below(landed).stream().anyMatch(below ->
                    moon.getBlockState(below).is(SurfaceContent.MOON_TURF.get())
                            && moon.getBlockState(below).isFaceSturdy(moon, below, Direction.UP));
            helper.assertTrue(turf, "No block below the rocket's lowest layer is moon turf with a sturdy top");
        });
    }

    @GameTest(template = "rocket_test", batch = "moon_arrival_crater", timeoutTicks = 1_000)
    public static void aMoonRocketLandsInACrater(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        fly(helper, driver, moon -> {
            BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
            // A flat lowland of y 20 under a crater of radius 12 centred on the pad (its reach is 21 blocks).
            MoonPadArea.shape(moon, 22, LOWLAND);
            int ground = moon.getHeight(Heightmap.Types.WORLD_SURFACE, pad.getX(), pad.getZ()) - 1;
            BlockPos centre = new BlockPos(pad.getX(), ground, pad.getZ());
            CraterPiece crater = new CraterPiece(centre, new CraterShape(12, 4, 2, new int[4]),
                    V180PlanetWorldgen.CRATER_FLOOR_MIN, V180PlanetWorldgen.MOON_RIM_MAX);
            BoundingBox box = crater.getBoundingBox();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                    ChunkPos chunk = new ChunkPos(cx, cz);
                    moon.getChunk(cx, cz);
                    crater.postProcess(moon, moon.structureManager(), moon.getChunkSource().getGenerator(),
                            RandomSource.create(1L), new BoundingBox(chunk.getMinBlockX(), moon.getMinBuildHeight(),
                                    chunk.getMinBlockZ(), chunk.getMaxBlockX(), moon.getMaxBuildHeight() - 1,
                                    chunk.getMaxBlockZ()), chunk, centre);
                }
            }
            int floor = moon.getHeight(Heightmap.Types.WORLD_SURFACE, pad.getX(), pad.getZ()) - 1;
            if (floor >= ground) {
                throw new IllegalStateException("The crater did not dig under the pad: " + floor + " / " + ground);
            }
        }, V180PlanetWorldgen.MOON_RIM_MAX, (moon, landed) -> {
            int lowest = MoonPadArea.lowest(landed);
            helper.assertTrue(lowest < LOWLAND + 1,
                    "The rocket's lowest block is at y " + lowest + ", not below the lowland's y 21 in the crater");
        });
    }

    /**
     * Shapes the ground, waits for the pads to settle, flies a rocket to the Moon and checks that it landed on the
     * highest column of its footprint, below the old floor, under ADR-075's support rule; then the case's own checks.
     */
    private static void fly(GameTestHelper helper, GameTestTickDriver driver, Consumer<ServerLevel> shape, int highestGround,
                            BiConsumer<ServerLevel, RocketStructureSnapshot> landing) {
        ServerLevel earth = helper.getLevel();
        ServerLevel moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable");
        clearTransferJournal(earth);
        primePadChunks(earth);
        primePadChunks(moon);
        clearPadRockets(earth);
        clearPadRockets(moon);
        shape.accept(moon);
        BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
        int ground = moon.getHeight(Heightmap.Types.WORLD_SURFACE, pad.getX(), pad.getZ()) - 1;
        helper.assertTrue(ground <= highestGround, "The shaped ground is higher than the terrain bound: " + ground);
        // W166: the second clear waits until the pads' entity sections have loaded, so no rocket on disk is missed.
        driver.after(80, () -> RocketFlightGameTestFixtures.clearPadRocketsWhenLoaded(helper, driver,
                RocketFlightGameTestFixtures.PAD_LOAD_TICKS, () -> {
            assembleFueledRocket(helper, driver, new BlockPos(3, 2, 3), UUID.randomUUID(), rocket -> {
                UUID logical = rocket.assemblyTransactionId().orElseThrow();
                RocketFlightRequestResult result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON,
                        UUID.randomUUID());
                RocketFlightGameTestFixtures.assertPendingLaunch(helper, result, rocket);
                RocketFlightGameTestFixtures.awaitFlightState(helper, driver, moon, logical, RocketFlightState.LANDED, 270, () -> {
                    RocketEntity landed = findLogicalRocket(moon, logical);
                    helper.assertTrue(landed != null, "The rocket did not arrive on the Moon");
                    helper.assertTrue(landed.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                            "The Moon rocket did not land");
                    RocketStructureSnapshot snapshot = landed.snapshot().orElseThrow();
                    int lowest = MoonPadArea.lowest(snapshot);
                    int highest = MoonPadArea.highestColumn(moon, snapshot);
                    helper.assertTrue(lowest == highest, "The rocket's lowest block is at y " + lowest
                            + ", not on its highest footprint column at y " + highest + " (pad ground y " + ground + ")");
                    helper.assertTrue(lowest < SafeCelestialTravel.FIXED_FEET_POSITION.getY(),
                            "The rocket's lowest block is at y " + lowest + ", not below the old y 80 floor");
                    helper.assertTrue(MoonPadArea.supported(moon, snapshot),
                            "The rocket is not supported: no sturdy top below its lowest layer, or fluid below it");
                    landing.accept(moon, snapshot);
                    clearPadRockets(moon);
                    helper.succeed();
                });
            });
        }, earth, moon));
    }
}
