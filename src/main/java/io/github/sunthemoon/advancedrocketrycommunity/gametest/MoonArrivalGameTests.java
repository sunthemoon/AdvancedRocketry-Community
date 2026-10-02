package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.assembleFueledRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearPadRockets;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearTransferJournal;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.findLogicalRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.primePadChunks;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
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
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
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
 * ADR-063 section 5 and 9 (A1): a rocket arriving on the new Moon terrain still arrives at y 80 or above, as on the
 * flat Moon (ADR-033's fixed pads, no ground support), whether the first pad lies over the highest highlands or over
 * a crater. Each case shapes the ground under the first pad, flies a rocket from Earth and checks its lowest block.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MoonArrivalGameTests {
    private static final int PAD_REACH = 8;

    private MoonArrivalGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "moon_arrival_highlands", timeoutTicks = 1_000)
    public static void aMoonRocketArrivesAtY80OverTheHighestHighlands(GameTestHelper helper) {
        fly(helper, moon -> MoonPadArea.shape(moon, PAD_REACH, V180PlanetWorldgen.MOON_SURFACE_MAX),
                V180PlanetWorldgen.MOON_SURFACE_MAX);
    }

    @GameTest(template = "rocket_test", batch = "moon_arrival_crater", timeoutTicks = 1_000)
    public static void aMoonRocketArrivesAtY80OverACrater(GameTestHelper helper) {
        fly(helper, moon -> {
            BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
            // A flat lowland of y 20 under a crater of radius 12 centred on the pad (its reach is 21 blocks).
            MoonPadArea.shape(moon, 22, 20);
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
        }, V180PlanetWorldgen.MOON_RIM_MAX);
    }

    /** Shapes the ground, waits for the pads to settle, flies a rocket to the Moon and checks its arrival height. */
    private static void fly(GameTestHelper helper, Consumer<ServerLevel> shape, int highestGround) {
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
        helper.runAfterDelay(80, () -> {
            clearPadRockets(earth);
            clearPadRockets(moon);
            RocketEntity rocket = assembleFueledRocket(helper, new BlockPos(3, 2, 3), UUID.randomUUID());
            UUID logical = rocket.assemblyTransactionId().orElseThrow();
            RocketFlightRequestResult result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON,
                    UUID.randomUUID());
            helper.assertTrue(result.success(), "Earth-to-Moon launch failed: " + result.code());
            helper.runAfterDelay(270, () -> {
                RocketEntity landed = findLogicalRocket(moon, logical);
                helper.assertTrue(landed != null, "The rocket did not arrive on the Moon");
                helper.assertTrue(landed.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                        "The Moon rocket did not land");
                RocketStructureSnapshot snapshot = landed.snapshot().orElseThrow();
                int lowest = snapshot.sourceOrigin().y() + snapshot.bounds().minimum().y();
                helper.assertTrue(lowest == SafeCelestialTravel.FIXED_FEET_POSITION.getY(),
                        "The rocket's lowest block arrived at y " + lowest + ", not y 80, over ground at y " + ground);
                clearPadRockets(moon);
                helper.succeed();
            });
        });
    }
}
