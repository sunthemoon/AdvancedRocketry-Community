package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-054 sections 9 and 9.1 for the laser target: registration on persistence, removal, retirement and freezing. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaserTargetGameTests {
    private LaserTargetGameTests() {
    }

    @GameTest(template = "empty", batch = "endgame_laser_target", timeoutTicks = 300)
    public static void aTargetRegistersOnceSavedAndARemovedIdComesBackFrozen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState state = ModBlocks.LASER_TARGET.get().defaultBlockState();
        helper.assertTrue(state.is(BlockTags.WITHER_IMMUNE) && state.is(BlockTags.DRAGON_IMMUNE)
                        && state.getPistonPushReaction() == PushReaction.BLOCK
                        && state.getBlock().getExplosionResistance() >= 1200.0F,
                "The target lacks the endpoint protections");
        level.setBlockAndUpdate(pos, state);
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) level.getBlockEntity(pos);
        UUID owner = UUID.randomUUID();
        helper.assertTrue(target.assignOwner(owner), "The fixture owner was not assigned");
        UUID id = target.deviceId().orElseThrow();
        CompoundTag[] copy = new CompoundTag[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE,
                        "Not awaiting a save: " + target.describe()))
                .thenExecuteAfter(25, () -> helper.assertTrue(root().endpoint(id).isEmpty()
                        && target.endpointStatus() == EndgameCode.AWAITING_WORLD_SAVE, "Registered before any save"))
                .thenWaitUntil(() -> {
                    chunkSaved(level, pos);
                    helper.assertTrue(target.endpointStatus() == EndgameCode.OK && target.endpointActive(),
                            "Not registered after the save: " + target.describe());
                })
                .thenExecute(() -> {
                    EndpointRecord record = root().endpoint(id).orElseThrow();
                    helper.assertTrue(record.kind().equals(LaserTargetBlockEntity.KIND) && record.owner().equals(owner)
                                    && record.pos() == pos.asLong() && record.level().equals(level.dimension().location()),
                            "The record does not match the target");
                    target.buffer().setStackInSlot(4, new ItemStack(Items.COBBLESTONE, 7));
                    var items = target.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                            .orElseThrow(IllegalStateException::new);
                    helper.assertTrue(items.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1
                            && items.extractItem(4, 2, false).getCount() == 2, "The buffer is not extract-only");
                    copy[0] = target.saveWithoutMetadata();
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    helper.assertTrue(root().endpoint(id).isEmpty() && root().retired(id),
                            "A removed target was not retired");
                    int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D)).stream()
                            .mapToInt(entity -> entity.getItem().is(Items.COBBLESTONE) ? entity.getItem().getCount() : 0)
                            .sum();
                    helper.assertTrue(dropped == 5, "The buffer did not drop: " + dropped);
                    // A copy of the removed target comes back (a crash or a block mover): it is retired and frozen.
                    level.setBlockAndUpdate(pos, state);
                    ((LaserTargetBlockEntity) level.getBlockEntity(pos)).load(copy[0]);
                })
                .thenWaitUntil(() -> {
                    LaserTargetBlockEntity back = (LaserTargetBlockEntity) level.getBlockEntity(pos);
                    helper.assertTrue(back.endpointStatus() == EndgameCode.ENDPOINT_RETIRED && back.frozen()
                            && !back.endpointActive(), "A returning copy was not frozen: " + back.describe());
                })
                .thenExecute(() -> {
                    helper.assertTrue(root().endpoint(id).isEmpty(), "A frozen copy registered again");
                    ((LaserTargetBlockEntity) level.getBlockEntity(pos)).buffer().setStackInSlot(4, ItemStack.EMPTY);
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0D)).forEach(ItemEntity::discard);
                })
                .thenSucceed();
    }

    /**
     * Test fixture: the event a chunk save posts, with the tag it would write (vanilla throttles real saves of one
     * chunk to one per 10 s of wall time, which a GameTest outruns).
     */
    static void chunkSaved(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, ChunkSerializer.write(level, chunk)));
    }

    private static EndgameRoot root() {
        return EndgameRuntime.operational().orElseThrow().root().orElseThrow();
    }
}
