package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.DeferredRoomBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The environmental-change adapter frozen in the W33 design: every torch change needs a FULL chunk inside the world
 * and build height, then passes one cancellable preflight {@link BlockEvent.EntityPlaceEvent} naming the new state. A
 * relight names its player; an environmental change names no entity, so no FakePlayer and no operator bypass exist.
 * A cancelled change leaves the block, its data and every item untouched.
 */
final class TorchWorldChanges {
    enum Result { CHANGED, CANCELLED, REFUSED }

    private TorchWorldChanges() {
    }

    /** Converts a lit torch with no combustion air, or removes it and drops one pair item when {@code drop}. */
    static Result extinguish(ServerLevel level, BlockPos pos, BlockState lit, TorchPair pair, boolean drop) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(lit.getBlock());
        boolean wall = pair.wallBlock().equals(blockId);
        Item item = TorchShapes.item(pair.item());
        if (item == null || !pair.block(wall).equals(blockId) || wall && !lit.hasProperty(WallTorchBlock.FACING)) {
            return Result.REFUSED;
        }
        BlockState after = drop ? Blocks.AIR.defaultBlockState() : wall
                ? ModBlocks.UNLIT_WALL_TORCH.get().defaultBlockState().setValue(WallTorchBlock.FACING, lit.getValue(WallTorchBlock.FACING))
                : ModBlocks.UNLIT_TORCH.get().defaultBlockState();
        Result admitted = admit(level, pos, lit, after, null);
        if (admitted != Result.CHANGED) {
            return admitted;
        }
        if (!level.setBlock(pos, after, Block.UPDATE_ALL)) {
            return Result.REFUSED;
        }
        if (drop) {
            Block.popResource(level, pos, new ItemStack(item));
        } else if (level.getBlockEntity(pos) instanceof UnlitTorchBlockEntity torch) {
            torch.assign(new UnlitTorchRoot.Identity(pair.id(), blockId, pair.item()));
        }
        return Result.CHANGED;
    }

    /** Restores the pair's exact lit block in the saved orientation; the caller has checked the air and the root. */
    static Result relight(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState unlit, BlockState lit) {
        if (!lit.canSurvive(level, pos)) {
            return Result.REFUSED;
        }
        Result admitted = admit(level, pos, unlit, lit, player);
        if (admitted != Result.CHANGED) {
            return admitted;
        }
        return level.setBlock(pos, lit, Block.UPDATE_ALL) ? Result.CHANGED : Result.REFUSED;
    }

    private static Result admit(ServerLevel level, BlockPos pos, BlockState before, BlockState after,
                                @Nullable Entity actor) {
        if (!CombustionAir.loaded(level, pos) || !level.getBlockState(pos).equals(before)
                || UnlitTorchBlockEntity.protectedAt(level, pos)) {
            return Result.REFUSED;
        }
        BlockPos support = before.hasProperty(WallTorchBlock.FACING)
                ? pos.relative(before.getValue(WallTorchBlock.FACING).getOpposite()) : pos.below();
        BlockState against = CombustionAir.loaded(level, support) ? level.getBlockState(support) : Blocks.AIR.defaultBlockState();
        TorchChange event = new TorchChange(BlockSnapshot.create(level.dimension(), level, pos), against, actor, after);
        return MinecraftForge.EVENT_BUS.post(event) ? Result.CANCELLED : Result.CHANGED;
    }

    /** Preflight for a torch change; it changes no room boundary, so atmosphere invalidation skips it. */
    static final class TorchChange extends BlockEvent.EntityPlaceEvent implements DeferredRoomBoundary {
        private final BlockState placed;

        TorchChange(BlockSnapshot snapshot, BlockState against, @Nullable Entity actor, BlockState placed) {
            super(snapshot, against, actor);
            this.placed = placed;
        }

        @Override
        public BlockState getPlacedBlock() {
            return placed;
        }

        @Override
        public BlockState getState() {
            return placed;
        }
    }
}
