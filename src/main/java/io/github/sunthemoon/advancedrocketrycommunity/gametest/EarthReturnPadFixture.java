package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** One bounded arrival pad, owned and restored by each Earth-return test batch. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EarthReturnPadFixture {
    // GameTest batch hooks run serially on the server thread, including failed batches.
    private static EarthReturnPadFixture active;
    private final ServerLevel level;
    private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();

    private EarthReturnPadFixture(ServerLevel level) {
        this.level = level;
    }

    @BeforeBatch(batch = "flight")
    public static void beforeFlight(ServerLevel level) { install(level); }

    @AfterBatch(batch = "flight")
    public static void afterFlight(ServerLevel level) { restore(level); }

    @BeforeBatch(batch = "station_flight")
    public static void beforeStation(ServerLevel level) { install(level); }

    @AfterBatch(batch = "station_flight")
    public static void afterStation(ServerLevel level) { restore(level); }

    @BeforeBatch(batch = "planetary_surface_flight")
    public static void beforePlanetary(ServerLevel level) {
        install(level);
        try { DiscoveryProgressFixture.install(level, true); }
        catch (RuntimeException | Error failure) { restore(level); throw failure; }
    }

    @AfterBatch(batch = "planetary_surface_flight")
    public static void afterPlanetary(ServerLevel level) {
        try { DiscoveryProgressFixture.restore(level); } finally { restore(level); }
    }

    @BeforeBatch(batch = "planetary_admission")
    public static void beforeAdmission(ServerLevel level) { install(level); }

    @AfterBatch(batch = "planetary_admission")
    public static void afterAdmission(ServerLevel level) { restore(level); }

    @GameTest(template = "rocket_test", batch = "flight", timeoutTicks = 40)
    public static void provisionedEarthPadStillRejectsSnowAndGrass(GameTestHelper helper) {
        var earth = helper.getLevel();
        var rocket = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), UUID.randomUUID());
        var selector = new RocketLandingPadSelector();
        try {
            var selection = selector.select(earth, rocket.snapshot().orElseThrow(), UUID.randomUUID(),
                    List.of(), earth.getGameTime());
            helper.assertTrue(selection.success(), "Provisioned Earth pad was not admitted");
            helper.assertTrue(selection.candidatesChecked() == 1, "Fixture did not provision the first server candidate");
            var destination = selection.snapshot().orElseThrow();
            var absolute = destination.sourceOrigin().add(destination.blocks().get(0).position());
            var obstruction = new BlockPos(absolute.x(), absolute.y(), absolute.z());
            var original = earth.getBlockState(obstruction);
            try {
                for (var block : List.of(Blocks.SNOW, Blocks.GRASS)) {
                    earth.setBlock(obstruction, block.defaultBlockState(), Block.UPDATE_CLIENTS);
                    helper.assertTrue(!selector.available(earth, destination, null, false),
                            "Occupied Earth pad was admitted for " + block);
                }
            } finally {
                earth.setBlock(obstruction, original, Block.UPDATE_CLIENTS);
            }
            helper.assertTrue(selector.available(earth, destination, null, false), "Restored Earth pad was not admitted");
            helper.succeed();
        } finally {
            rocket.discard();
        }
    }

    private static void install(ServerLevel level) {
        if (active != null) {
            throw new IllegalStateException("Earth return fixture was not released by its previous batch");
        }
        var fixture = new EarthReturnPadFixture(level);
        try {
            fixture.prepare();
            active = fixture;
        } catch (RuntimeException | Error exception) {
            fixture.release();
            throw exception;
        }
    }

    private void prepare() {
        BlockPos spawn = level.getSharedSpawnPos();
        int floorY = level.getMinBuildHeight() + 1;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int absoluteX = spawn.getX() + x;
                int absoluteZ = spawn.getZ() + z;
                level.getChunkAt(new BlockPos(absoluteX, 0, absoluteZ));
                floorY = Math.max(floorY, level.getHeight(Heightmap.Types.WORLD_SURFACE, absoluteX, absoluteZ));
            }
        }
        if (floorY + 3 >= level.getMaxBuildHeight()) {
            throw new IllegalStateException("Earth return fixture has no bounded overhead clearance");
        }
        // Preflight every changed block and the complete rocket corridor before writing.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 3; y++) {
                    var pos = new BlockPos(spawn.getX() + x, floorY + y, spawn.getZ() + z);
                    var state = level.getBlockState(pos);
                    if (!state.isAir() || level.getBlockEntity(pos) != null) {
                        throw new IllegalStateException("Earth return fixture would overwrite occupied terrain at " + pos);
                    }
                    if (y == 0) {
                        original.put(pos, state);
                    }
                }
            }
        }
        original.keySet().forEach(pos -> level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_EARTH_RETURN_FIXTURE installed x={} y={} z={} blocks={}",
                spawn.getX(), floorY, spawn.getZ(), original.size());
    }

    private static void restore(ServerLevel level) {
        var fixture = active;
        if (fixture == null || fixture.level != level) {
            throw new IllegalStateException("Earth return fixture has a mismatched batch lifecycle");
        }
        try {
            fixture.release();
        } finally {
            active = null;
        }
    }

    private void release() {
        original.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
        original.forEach((pos, state) -> {
            if (!level.getBlockState(pos).equals(state)) {
                throw new IllegalStateException("Earth return fixture did not restore " + pos);
            }
        });
        AdvancedRocketryCommunity.LOGGER.info("ARCE_EARTH_RETURN_FIXTURE restored blocks={}", original.size());
        original.clear();
    }
}
