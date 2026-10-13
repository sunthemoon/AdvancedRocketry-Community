package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Owned, isolated FULL chunks (one radius-zero ticket each, level 33) beside one absent chunk, not a force-loaded 5x5
 * area. The default is one chunk with an absent east neighbour; {@link #corner} owns a 3x3 less its south-east chunk.
 */
final class OwnedLoadedFrontier implements AutoCloseable {
    private static final TicketType<Long> TICKET = TicketType.create("arce_gametest_owned_frontier", Comparator.<Long>naturalOrder());
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    final ServerLevel level;
    final BlockPos edge;
    private final GameTestHelper helper;
    private final List<ChunkPos> owned;
    private final ChunkPos absent;
    private final List<ChunkPos> ticketed = new ArrayList<>();
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();

    /** One FULL chunk; {@link #edge} is on its east edge, beside the absent east chunk. */
    OwnedLoadedFrontier(GameTestHelper helper, ServerLevel level, int y) {
        this(helper, level, centre(helper, 240_000), y, false);
    }

    /**
     * The centre chunk and seven neighbours FULL, the south-east diagonal absent. {@link #edge} is the centre's
     * south-east corner cell: every cell one or two blocks from it along an axis is loaded, but not the diagonal.
     */
    static OwnedLoadedFrontier corner(GameTestHelper helper, ServerLevel level, int y) {
        return new OwnedLoadedFrontier(helper, level, centre(helper, -240_000), y, true);
    }

    private OwnedLoadedFrontier(GameTestHelper helper, ServerLevel level, ChunkPos centre, int y, boolean corner) {
        this.helper = helper;
        this.level = level;
        List<ChunkPos> chunks = new ArrayList<>();
        if (corner) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 1 || dz != 1) {
                        chunks.add(new ChunkPos(centre.x + dx, centre.z + dz));
                    }
                }
            }
            absent = new ChunkPos(centre.x + 1, centre.z + 1);
            edge = new BlockPos(centre.getMaxBlockX(), y, centre.getMaxBlockZ());
        } else {
            chunks.add(centre);
            absent = new ChunkPos(centre.x + 1, centre.z);
            edge = new BlockPos(centre.getMaxBlockX(), y, centre.getMinBlockZ() + 8);
        }
        owned = List.copyOf(chunks);
        for (ChunkPos chunk : owned) {
            helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                    && !level.getChunkSource().hasChunk(chunk.x, chunk.z), "Frontier fixture chunk is already owned/loaded");
        }
        try {
            for (ChunkPos chunk : owned) {
                ticketed.add(chunk);
                level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk.toLong());
                level.getChunk(chunk.x, chunk.z); // Explicit fixture acquisition, never an operation under test.
            }
            assertAbsent();
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Structure origins are in blocks; multiplying by four separates simultaneous fixtures by many chunks. */
    private static ChunkPos centre(GameTestHelper helper, int base) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new ChunkPos(base + origin.getX() * 4, base + origin.getZ() * 4);
    }

    void set(BlockPos pos, BlockState state) {
        helper.assertTrue(owned.contains(new ChunkPos(pos)), "Frontier fixture attempted to write another chunk");
        if (!before.containsKey(pos)) {
            helper.assertTrue(level.getBlockEntity(pos) == null, "Frontier fixture cell must be BE-free");
            before.put(pos.immutable(), level.getBlockState(pos));
        }
        level.setBlock(pos, state, QUIET);
    }

    boolean inAbsentChunk(BlockPos pos) {
        return new ChunkPos(pos).equals(absent);
    }

    void assertAbsent() {
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.x, absent.z) == null
                && !level.getChunkSource().hasChunk(absent.x, absent.z), "Frontier's absent chunk is loaded or has a FULL ticket");
    }

    /** All native ticket types count here, including transient loads; the assertions bracket synchronous operations. */
    @SuppressWarnings("unchecked")
    int ticketCount() {
        try {
            Field field = DistanceManager.class.getDeclaredField("tickets");
            field.setAccessible(true);
            var tickets = (Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>>) field.get(
                    level.getChunkSource().chunkMap.getDistanceManager());
            return tickets.values().stream().mapToInt(SortedArraySet::size).sum();
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot inspect native frontier tickets", failure); }
    }

    @Override
    public void close() {
        try {
            for (var entry : before.entrySet()) {
                level.setBlock(entry.getKey(), entry.getValue(), QUIET);
            }
            before.clear();
        } finally {
            RuntimeException failure = null;
            for (ChunkPos chunk : ticketed) {
                try {
                    level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk.toLong());
                } catch (RuntimeException next) {
                    if (failure == null) { failure = next; } else { failure.addSuppressed(next); }
                }
            }
            ticketed.clear();
            if (failure != null) { throw failure; }
        }
    }
}
