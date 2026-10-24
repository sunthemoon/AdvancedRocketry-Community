package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedTerrainProofGameTests {
    @GameTest(template = "empty", batch = "w152_terrain", timeoutTicks = 200)
    public static void ordinaryTerrainUsesOriginalWorkerAndKeepsUnrelatedContents(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1)); var chunk = new ChunkPos(pos);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        ((ChestBlockEntity) level.getBlockEntity(pos)).setItem(0, new ItemStack(Items.DIAMOND, 7));
        var runtime = EscrowRuntime.of(server); var barrier = runtime.barrier(); var store = runtime.store(level);
        String dimension = level.dimension().location().toString();
        var result = new EscrowBarrier.Result[1]; var submitted = new boolean[1];
        var labels = new java.util.ArrayList<String>();
        helper.assertTrue(!barrier.availableOrdinaryTerrain(store, "w152:wrong_level", chunk, server.getTickCount()),
                "Terrain facade accepted a different Level identity");
        helper.onEachTick(() -> {
            if (!submitted[0]) {
                submitted[0] = barrier.beginOrdinaryTerrain(store, dimension, chunk,
                        root -> containsChest(root, pos), server.getTickCount(), labels::add, value -> result[0] = value);
                if (submitted[0]) {
                    helper.assertTrue(!barrier.beginOrdinary(store, dimension, chunk, root -> true, server.getTickCount(),
                            label -> { }, value -> { }), "Terrain and entity captures used separate per-tick capacity");
                }
            }
            if (result[0] == null) { return; }
            helper.assertTrue(result[0].confirmed(), "Ordinary terrain readback failed: " + result[0].reason());
            var proof = result[0].proof();
            try {
                helper.assertTrue(proof.frontier() == null, "Ordinary proof fabricated a governed frontier");
                helper.assertTrue(proof.capture().worker() == level.getChunkSource().chunkMap.worker, "Not the original terrain worker");
                helper.assertTrue(proof.capture().directory().getFileName().toString().equals("region"), "Captured entity storage, not terrain");
                helper.assertTrue(containsChest(AnvilRecordReader.read(proof.capture().directory(), chunk).root(), pos),
                        "Full native terrain readback omitted or changed unrelated contents");
                helper.assertTrue(labels.contains("BARRIER_STORE_BEFORE"), "Ordinary capture omitted its own diagnostic sink");
                runtime.monitor().observed(dimension, false, chunk);
                helper.assertTrue(!proof.valid(), "A later terrain store did not invalidate the proof generation");
                helper.succeed();
            } catch (java.io.IOException failed) { helper.fail("Native terrain readback failed: " + failed.getClass().getSimpleName()); }
            finally { proof.release(); }
        });
    }

    private static boolean containsChest(CompoundTag root, BlockPos pos) {
        if (!root.contains("sections", Tag.TAG_LIST) || root.getInt("xPos") != (pos.getX() >> 4)
                || root.getInt("zPos") != (pos.getZ() >> 4)) { return false; }
        for (Tag raw : root.getList("block_entities", Tag.TAG_COMPOUND)) {
            var be = (CompoundTag) raw;
            if (be.getInt("x") != pos.getX() || be.getInt("y") != pos.getY() || be.getInt("z") != pos.getZ()) { continue; }
            var items = be.getList("Items", Tag.TAG_COMPOUND);
            return be.getString("id").equals("minecraft:chest") && items.size() == 1
                    && items.getCompound(0).getString("id").equals("minecraft:diamond")
                    && items.getCompound(0).getByte("Count") == 7;
        }
        return false;
    }
    private SealedTerrainProofGameTests() { }
}
