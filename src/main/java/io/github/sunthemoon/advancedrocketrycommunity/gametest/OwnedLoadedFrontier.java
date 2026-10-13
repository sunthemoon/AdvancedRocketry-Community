package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

/** One owned, isolated FULL chunk (ticket level 33), with an absent east neighbour, not a force-loaded 5x5 area. */
final class OwnedLoadedFrontier implements AutoCloseable {
    private static final TicketType<Long> TICKET = TicketType.create("arce_gametest_owned_frontier", Comparator.<Long>naturalOrder());
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    final ServerLevel level;
    final BlockPos edge;
    private final GameTestHelper helper;
    private final ChunkPos chunk;
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    private boolean ownsTicket;

    OwnedLoadedFrontier(GameTestHelper helper, ServerLevel level, int y) {
        this.helper = helper;
        this.level = level;
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        // Structure origins are in blocks; multiplying by four separates simultaneous fixtures by many chunks.
        chunk = new ChunkPos(240_000 + origin.getX() * 4, 240_000 + origin.getZ() * 4);
        edge = new BlockPos(chunk.getMaxBlockX(), y, chunk.getMinBlockZ() + 8);
        helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                && !level.getChunkSource().hasChunk(chunk.x, chunk.z), "Frontier fixture chunk is already owned/loaded");
        try {
            ownsTicket = true;
            level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk.toLong());
            level.getChunk(chunk.x, chunk.z); // Explicit fixture acquisition, never an operation under test.
            assertAbsent();
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    void set(BlockPos pos, BlockState state) {
        helper.assertTrue(new ChunkPos(pos).equals(chunk), "Frontier fixture attempted to write another chunk");
        if (!before.containsKey(pos)) {
            helper.assertTrue(level.getBlockEntity(pos) == null, "Frontier fixture cell must be BE-free");
            before.put(pos.immutable(), level.getBlockState(pos));
        }
        level.setBlock(pos, state, QUIET);
    }

    void assertAbsent() {
        helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x + 1, chunk.z) == null
                && !level.getChunkSource().hasChunk(chunk.x + 1, chunk.z), "Frontier's east chunk is loaded or has a FULL ticket");
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
            if (ownsTicket) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk.toLong());
                ownsTicket = false;
            }
        }
    }
}
