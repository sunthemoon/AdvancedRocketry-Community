package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Shared checks of the worldgen GameTests: loading an area through tickets, and placing a structure piece chunk by
 * chunk through a level that records every write, compared with the blocks around it before and after.
 */
final class ChunkPlacementChecks {
    /** Blocks around a piece's box that a structure test compares before and after placement. */
    static final int MARGIN = 16;
    private static final TicketType<ChunkPos> LOAD_TICKET = TicketType.create("arce_gametest_planet_load",
            Comparator.comparingLong(ChunkPos::toLong));

    private ChunkPlacementChecks() {
    }

    /**
     * Requests every chunk a piece's box touches with a ticket and waits until all are loaded, so the generation is
     * spread over ticks instead of stalling one tick (and every test running beside it); then runs the check, lets the
     * chunks go and succeeds.
     */
    static void whenLoaded(GameTestHelper helper, ServerLevel level, BoundingBox box, Runnable check) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                chunks.add(chunk);
                level.getChunkSource().addRegionTicket(LOAD_TICKET, chunk, 1, chunk);
            }
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(chunks.stream().allMatch(chunk ->
                        level.getChunkSource().hasChunk(chunk.x, chunk.z)), "Chunks still loading"))
                .thenExecute(check)
                .thenExecute(() -> chunks.forEach(chunk ->
                        level.getChunkSource().removeRegionTicket(LOAD_TICKET, chunk, 1, chunk)))
                .thenSucceed();
    }

    /**
     * Places a piece in every chunk its box touches, one chunk box at a time, through a level that records every
     * block write, and checks that each call wrote only inside its own chunk and the piece's box, and that the box
     * lies within {@code reach} of the centre. Every block of the box and a {@link #MARGIN}-block margin is compared
     * before and after: only recorded writes inside the box may change, so a write that bypasses the recording (any
     * other method of the level) is found too (C15bR1-M2). Returns every position written.
     */
    static List<BlockPos> placeChunkByChunk(GameTestHelper helper, ServerLevel level, StructurePiece piece,
                                                    BlockPos centre, int reach, int minY, int maxY) {
        BoundingBox pieceBox = piece.getBoundingBox();
        helper.assertTrue(pieceBox.minX() >= centre.getX() - reach && pieceBox.maxX() <= centre.getX() + reach
                        && pieceBox.minZ() >= centre.getZ() - reach && pieceBox.maxZ() <= centre.getZ() + reach
                        && pieceBox.minY() >= minY && pieceBox.maxY() <= maxY,
                "The piece box exceeds its bounds: " + pieceBox);
        List<BlockPos> writes = new ArrayList<>();
        BoundingBox around = new BoundingBox(pieceBox.minX() - MARGIN,
                Math.max(level.getMinBuildHeight(), pieceBox.minY() - MARGIN), pieceBox.minZ() - MARGIN,
                pieceBox.maxX() + MARGIN, Math.min(level.getMaxBuildHeight() - 1, pieceBox.maxY() + MARGIN),
                pieceBox.maxZ() + MARGIN);
        for (int cx = around.minX() >> 4; cx <= around.maxX() >> 4; cx++) {
            for (int cz = around.minZ() >> 4; cz <= around.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockState[] before = snapshot(level, around);
        for (int cx = pieceBox.minX() >> 4; cx <= pieceBox.maxX() >> 4; cx++) {
            for (int cz = pieceBox.minZ() >> 4; cz <= pieceBox.maxZ() >> 4; cz++) {
                ChunkPos chunk = new ChunkPos(cx, cz);
                BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(),
                        chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
                List<BlockPos> written = new ArrayList<>();
                piece.postProcess(RecordingLevel.wrap(level, written), level.structureManager(),
                        level.getChunkSource().getGenerator(), RandomSource.create(1L), chunkBox, chunk, centre);
                for (BlockPos position : written) {
                    helper.assertTrue(chunkBox.isInside(position) && pieceBox.isInside(position),
                            "A write outside chunk " + chunk + " or the piece box at " + position);
                }
                writes.addAll(written);
            }
        }
        helper.assertTrue(!writes.isEmpty(), "The piece wrote nothing");
        Set<BlockPos> recorded = new HashSet<>(writes);
        BlockState[] after = snapshot(level, around);
        int index = 0;
        for (int x = around.minX(); x <= around.maxX(); x++) {
            for (int z = around.minZ(); z <= around.maxZ(); z++) {
                for (int y = around.minY(); y <= around.maxY(); y++, index++) {
                    if (before[index] != after[index]) {
                        BlockPos position = new BlockPos(x, y, z);
                        helper.assertTrue(recorded.contains(position) && pieceBox.isInside(position),
                                "An unrecorded change at " + position + ": " + before[index] + " -> " + after[index]);
                    }
                }
            }
        }
        return writes;
    }

    /** The block states of a box, x, then z, then y. */
    private static BlockState[] snapshot(ServerLevel level, BoundingBox box) {
        BlockState[] states = new BlockState[box.getXSpan() * box.getYSpan() * box.getZSpan()];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int index = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    states[index++] = level.getBlockState(position.set(x, y, z));
                }
            }
        }
        return states;
    }

    /** A {@link WorldGenLevel} view of a server level that records the position of every block write. */
    static final class RecordingLevel implements InvocationHandler {
        private final ServerLevel level;
        private final List<BlockPos> written;

        private RecordingLevel(ServerLevel level, List<BlockPos> written) {
            this.level = level;
            this.written = written;
        }

        static WorldGenLevel wrap(ServerLevel level, List<BlockPos> written) {
            return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),
                    new Class<?>[] {WorldGenLevel.class}, new RecordingLevel(level, written));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            if (method.getName().equals("setBlock") && arguments != null && arguments.length > 0
                    && arguments[0] instanceof BlockPos position) {
                written.add(position.immutable());
            }
            try {
                return method.invoke(level, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
