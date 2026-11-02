package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.FormedModelController;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Worker-owned distant cells/tickets; cleanup runs on success, assertion failure and vanilla timeout. */
public final class K3ModelTestRegion implements AutoCloseable, GameTestListener {
    private static final TicketType<UUID> TICKET = TicketType.create("arce_w236_model", Comparator.comparing(UUID::toString));
    private final ServerLevel level;
    private final UUID ticket = UUID.randomUUID();
    private final List<ChunkPos> chunks = new ArrayList<>();
    private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
    private boolean closed;

    public K3ModelTestRegion(GameTestHelper helper, BlockPos center) { this(helper, center, 2); }

    /**
     * Tickets the square of chunks within {@code radius} of the center's chunk. K3 sizes its shared region to the
     * fixture's validation reach; W247's K4 fixtures share one chunk and pass 0. The legacy default remains 2.
     */
    public K3ModelTestRegion(GameTestHelper helper, BlockPos center, int radius) {
        level = helper.getLevel();
        if (radius < 0 || radius > 2) { throw new IllegalArgumentException("K3 region radius must be 0 to 2"); }
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo"); field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(this);
            ChunkPos c = new ChunkPos(center);
            for (int x = c.x - radius; x <= c.x + radius; x++) { for (int z = c.z - radius; z <= c.z + radius; z++) {
                // Check the FULL fringe too, not just the exact template force ticket.
                for (int dx = -2; dx <= 2; dx++) { for (int dz = -2; dz <= 2; dz++) {
                    helper.assertTrue(!level.getForcedChunks().contains(ChunkPos.asLong(x + dx, z + dz)),
                            "K3 fixture overlaps a template/other forced-chunk footprint");
                } }
                var chunk = new ChunkPos(x, z); chunks.add(chunk);
                level.getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); level.getChunk(x, z);
            } }
        } catch (ReflectiveOperationException | RuntimeException | Error failure) {
            close(); throw new IllegalStateException("Could not install isolated K3 model region", failure);
        }
    }
    /** Whether the position lies in one of this region's ticketed chunks. */
    public boolean ticketed(BlockPos position) { return chunks.contains(new ChunkPos(position)); }
    public void set(BlockPos position, BlockState state) {
        if (!before.containsKey(position)) {
            if (level.getBlockEntity(position) != null) { throw new IllegalStateException("K3 test cannot replace native data"); }
            before.put(position.immutable(), level.getBlockState(position));
        }
        level.setBlock(position, state, Block.UPDATE_ALL);
    }
    public static void assertProjection(GameTestHelper helper, BlockEntity entity, boolean formed) {
        var tag = entity.getUpdateTag();
        helper.assertTrue(((FormedModelController) entity).modelFormed() == formed && ClassicModelProjection.decode(tag) == formed,
                "K3 controller/model state differs");
        helper.assertTrue(tag.getAllKeys().equals(formed ? Set.of("formed") : Set.of()), "K3 update exposed nonvisual state");
        var packet = (net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket) entity.getUpdatePacket();
        helper.assertTrue(packet != null && packet.getPos().equals(entity.getBlockPos())
                && Objects.equals(packet.getTag(), tag.isEmpty() ? null : tag), "K3 vanilla update packet differs");
        var replica = Objects.requireNonNull(entity.getType().create(entity.getBlockPos(), entity.getBlockState()));
        var saved = replica.saveWithoutMetadata();
        CompoundTag previous = new CompoundTag(); previous.putBoolean("formed", true); replica.handleUpdateTag(previous);
        helper.assertTrue(replica.getUpdateTag().isEmpty(), "K3 received projection became server formation authority");
        replica.onDataPacket(null, packet);
        helper.assertTrue(((FormedModelController) replica).modelFormed() == formed && saved.equals(replica.saveWithoutMetadata()),
                "K3 packet affected persistent state");
        for (net.minecraft.nbt.Tag malformed : List.of(net.minecraft.nbt.IntTag.valueOf(1), net.minecraft.nbt.ByteTag.valueOf((byte)2))) {
            var invalid = new CompoundTag(); invalid.put("formed", malformed); replica.handleUpdateTag(invalid);
            helper.assertTrue(!((FormedModelController) replica).modelFormed(), "K3 accepted malformed formed flag");
        }
        replica.handleUpdateTag(previous); replica.handleUpdateTag(null);
        helper.assertTrue(!((FormedModelController) replica).modelFormed() && saved.equals(replica.saveWithoutMetadata()),
                "K3 null flag did not clear or changed save data");
    }
    @Override public void close() {
        if (closed) { return; } closed = true;
        try { before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL)); }
        finally { chunks.forEach(chunk -> level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); }
    }
    @Override public void testStructureLoaded(GameTestInfo info) { }
    @Override public void testPassed(GameTestInfo info) { close(); }
    @Override public void testFailed(GameTestInfo info) { close(); }
}
