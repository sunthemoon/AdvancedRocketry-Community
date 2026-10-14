package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W37F review F8: the shared vent read behind every atmosphere consumer uses completed chunks only. A vent whose
 * chunk is visible but not complete supplies no cell, even in a neighbouring completed chunk, and is not loaded.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VentCompletedChunkGameTests {
    /** Above the v1.8 Moon terrain and the other tests' Moon fixtures at y 64. */
    private static final int ROOM_Y = 96;

    private VentCompletedChunkGameTests() { }

    @GameTest(template = "atmosphere_test", timeoutTicks = 60)
    public static void aVentInAnUnfinishedChunkSuppliesNoCellWithoutLoadingIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel moon = NonPlayerAtmosphereGameTests.level(helper, CelestialIds.MOON_LEVEL);
        BlockPos column = helper.absolutePos(new BlockPos(3, 0, 3));
        // The vent is in its chunk's last column, so the room's east cells lie in the next (completed) chunk.
        BlockPos vent = new BlockPos((column.getX() & ~15) + 15, ROOM_Y, column.getZ());
        BlockPos east = vent.offset(1, 1, 0);
        ChunkPos ventChunk = new ChunkPos(vent);
        ChunkPos eastChunk = new ChunkPos(east);
        BlockPos minimum = vent.offset(-2, 0, -2);
        BlockPos maximum = vent.offset(2, 3, 2);
        List<ChunkPos> forced = new ArrayList<>();
        Runnable cleanup = () -> {
            try {
                for (BlockPos cell : BlockPos.betweenClosed(minimum, maximum)) {
                    if (!moon.getBlockState(cell).isAir()) {
                        moon.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            } finally {
                forced.forEach(chunk -> moon.setChunkForced(chunk.x, chunk.z, false));
            }
        };
        try {
            for (int x = (vent.getX() - 4) >> 4; x <= (vent.getX() + 4) >> 4; x++) {
                for (int z = (vent.getZ() - 4) >> 4; z <= (vent.getZ() + 4) >> 4; z++) {
                    if (moon.setChunkForced(x, z, true)) { forced.add(new ChunkPos(x, z)); }
                    moon.getChunk(x, z);
                }
            }
            AtmosphereGameTests.buildShell(moon, minimum, maximum);
            OxygenVentBlockEntity placed = AtmosphereGameTests.placePreparedVent(moon, vent);
            helper.assertTrue(placed != null && !ventChunk.equals(eastChunk), "The two-chunk room fixture is not as expected");
        } catch (RuntimeException failed) {
            cleanup.run();
            throw failed;
        }
        GameTestTickPacer pacer = new GameTestTickPacer(server);
        long start = helper.getTick();
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) { return; }
            try {
                pacer.pace();
                if (helper.getTick() <= start) { return; }
                if (!supplied(moon, east)) {
                    helper.assertTrue(helper.getTick() < 50, "The two-chunk room did not seal: "
                            + AtmosphereRuntime.metrics(moon));
                    return;
                }
                try (IncompleteFullChunk incomplete = new IncompleteFullChunk(moon, vent)) {
                    helper.assertTrue(moon.hasChunkAt(vent) && moon.getChunkSource().getChunkNow(ventChunk.x, ventChunk.z) == null
                                    && moon.getChunkSource().getChunkNow(eastChunk.x, eastChunk.z) != null,
                            "The fixture did not leave the vent's chunk visible but unfinished beside a completed one");
                    helper.assertTrue(AtmosphereRuntime.breathabilityAt(moon, east).orElse(null) == BreathabilityState.VACUUM
                                    && !incomplete.completed()
                                    && moon.getChunkSource().getChunkNow(ventChunk.x, ventChunk.z) == null,
                            "A completed cell read its unfinished vent chunk's supply, or forced that chunk to finish");
                }
                helper.assertTrue(AtmosphereRuntime.breathabilityAt(moon, east).orElse(null) == BreathabilityState.BREATHABLE,
                        "The completed vent chunk did not supply its room again");
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

    private static boolean supplied(ServerLevel level, BlockPos cell) {
        return AtmosphereRuntime.breathabilityAt(level, cell).orElse(null) == BreathabilityState.BREATHABLE
                && AtmosphereRuntime.suppliedVolumes(level).values().stream().anyMatch(volume ->
                        volume.cells().contains(new VolumePosition(cell.getX(), cell.getY(), cell.getZ())));
    }
}
