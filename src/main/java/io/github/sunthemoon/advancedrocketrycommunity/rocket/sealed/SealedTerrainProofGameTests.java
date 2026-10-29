package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.EscrowGameTestProof;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestAutosave;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
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
    // As in W166, three native force batches fit inside the unchanged 200-tick fixture.
    private static final int CAPTURE_WINDOW_TICKS = 60;
    @GameTest(template = "empty", batch = "w152_terrain", timeoutTicks = 200)
    public static void ordinaryTerrainUsesOriginalWorkerAndKeepsUnrelatedContents(GameTestHelper helper) {
        readback(helper, false);
    }

    @GameTest(template = "empty", batch = "w164_terrain_supersession", timeoutTicks = 200)
    public static void ordinaryTerrainReprovesAfterOneVanillaSave(GameTestHelper helper) {
        readback(helper, true);
    }

    private static void readback(GameTestHelper helper, boolean supersedeFirst) {
        var level = helper.getLevel(); var server = level.getServer();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1)); var chunk = new ChunkPos(pos);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        ((ChestBlockEntity) level.getBlockEntity(pos)).setItem(0, new ItemStack(Items.DIAMOND, 7));
        var runtime = EscrowRuntime.of(server); var barrier = runtime.barrier(); var store = runtime.store(level);
        String dimension = level.dimension().location().toString();
        var result = new EscrowBarrier.Result[1]; var completed = new boolean[1];
        var labels = new java.util.ArrayList<String>();
        boolean[] saved = {false};
        var pacer = new GameTestTickPacer(server);
        helper.assertTrue(!barrier.availableOrdinaryTerrain(store, "w152:wrong_level", chunk, server.getTickCount()),
                "Terrain facade accepted a different Level identity");
        // Reuse W94's exact-result retry and product backoff, capped at two accepted captures here.
        var request = new EscrowGameTestProof<EscrowBarrier.Result>(server::getTickCount,
                (tick, callback) -> {
                if (GameTestAutosave.within(server, CAPTURE_WINDOW_TICKS)
                        || !EscrowGameTestProof.freshTerrainSave(level, chunk)) { return false; }
                labels.clear();
                boolean submitted = barrier.beginOrdinaryTerrain(store, dimension, chunk,
                        root -> containsChest(root, pos), tick, labels::add, callback);
                if (submitted) {
                    helper.assertTrue(!barrier.beginOrdinary(store, dimension, chunk, root -> true, server.getTickCount(),
                            label -> { }, value -> { }), "Terrain and entity captures used separate per-tick capacity");
                    if (supersedeFirst && !saved[0]) {
                        saved[0] = true;
                        // A real vanilla store of unchanged contents invalidates this native capture.
                        level.getChunkAt(pos).setUnsaved(true);
                        server.saveEverything(true, true, true);
                    }
                }
                return submitted;
            }, EscrowBarrier.Result::confirmed, EscrowBarrier.Result::reason, value -> result[0] = value, 2);
        helper.onEachTick(() -> {
            // Scheduled observations may run again after success, when the deliberately invalidated proof is stale.
            if (completed[0]) { return; }
            pacer.pace(); request.tick();
            if (result[0] == null) { return; }
            helper.assertTrue(result[0].confirmed(), "Ordinary terrain readback failed: " + result[0].reason());
            var proof = result[0].proof();
            try {
                helper.assertTrue(!supersedeFirst || saved[0] && request.attempts() == 2,
                        "The forced vanilla save did not invalidate the first capture");
                helper.assertTrue(proof.frontier() == null, "Ordinary proof fabricated a governed frontier");
                helper.assertTrue(proof.capture().worker() == level.getChunkSource().chunkMap.worker, "Not the original terrain worker");
                helper.assertTrue(proof.capture().directory().getFileName().toString().equals("region"), "Captured entity storage, not terrain");
                helper.assertTrue(containsChest(AnvilRecordReader.read(proof.capture().directory(), chunk).root(), pos),
                        "Full native terrain readback omitted or changed unrelated contents");
                helper.assertTrue(labels.contains("BARRIER_STORE_BEFORE"), "Ordinary capture omitted its own diagnostic sink");
                runtime.monitor().observed(dimension, false, chunk);
                helper.assertTrue(!proof.valid(), "A later terrain store did not invalidate the proof generation");
                completed[0] = true;
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
